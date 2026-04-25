package com.hedge.prototype.valuation.application;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardPricing;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardValuation;
import com.hedge.prototype.valuation.adapter.web.dto.FxForwardValuationRequest;
import com.hedge.prototype.valuation.application.event.ValuationCompletedEvent;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import com.hedge.prototype.valuation.application.port.FxForwardValuationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 통화선도 공정가치 평가 오케스트레이션 서비스.
 *
 * <p>계약 관리 + 평가 이력 관리 + 중복 평가 방지를 담당합니다.
 * 실제 계산은 {@link FxForwardPricing}에 위임합니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 — 매 보고기간 말)
 * @see K-IFRS 1113호 (공정가치 측정 Level 2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxForwardValuationService implements FxForwardValuationUseCase {

    private final FxForwardContractRepository contractRepository;
    private final FxForwardValuationRepository valuationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * IRP 계산 중간 결과 및 공정가치를 묶는 값 객체.
     *
     * @param remainingDays      잔존일수
     * @param currentForwardRate 현재 선도환율 (IRP 기준)
     * @param discountFactor     할인계수
     * @param fairValue          공정가치
     * @see K-IFRS 1113호 (공정가치 측정 Level 2)
     */
    private record FairValueCalculation(
            int remainingDays,
            BigDecimal currentForwardRate,
            BigDecimal discountFactor,
            BigDecimal fairValue
    ) {}

    /**
     * 통화선도 공정가치 평가 실행.
     *
     * <p>동일 계약 + 동일 평가기준일 요청 시 기존 결과 반환 (idempotent).
     * {@link ValuationResult#isNew()}로 신규(HTTP 201)와 중복(HTTP 200)을 구분합니다.
     *
     * @param request 평가 요청 (계약 정보 + 시장 데이터)
     * @return 평가 결과 래퍼 (신규 여부 포함)
     * @see K-IFRS 1113호 9항 (측정일 기준 공정가치)
     */
    @Transactional
    public ValuationResult valuate(FxForwardValuationRequest request) {
        // PoC: 시장 데이터(spotRate, 금리 등)가 변경될 때마다 재계산
        // 동일 contractId + valuationDate 조합이어도 입력값이 다르면 새로 계산
        FxForwardContract contract = resolveContract(request);
        FairValueCalculation calculation = calculateFairValue(contract, request);
        FxForwardValuation valuation = saveValuation(contract, request, calculation);

        log.info("통화선도 공정가치 평가 완료: contractId={}, valuationDate={}, spotRate={}",
                request.contractId(), request.valuationDate(), request.spotRate());

        eventPublisher.publishEvent(new ValuationCompletedEvent(
                valuation.getValuationId(), request.contractId(), request.valuationDate()));

        return ValuationResult.created(valuation);
    }

    /**
     * 계약 조회 또는 신규 등록.
     *
     * <p>기존 계약이 없으면 요청 정보로 신규 계약을 생성하여 저장합니다.
     * 조회 후 평가기준일 기준 만기 초과 검증(K-IFRS 6.5.10)을 수행합니다.
     *
     * @param request 평가 요청
     * @return 유효성이 확인된 FxForwardContract
     * @see K-IFRS 1109호 6.5.10 (위험회피수단 만기)
     */
    private FxForwardContract resolveContract(FxForwardValuationRequest request) {
        FxForwardContract contract = contractRepository.findById(request.contractId())
                .map(existing -> {
                    existing.update(
                            request.notionalAmountUsd(),
                            request.contractForwardRate(),
                            request.contractDate(),
                            request.maturityDate(),
                            request.hedgeDesignationDate(),
                            request.counterpartyCreditRating()
                    );
                    log.info("기존 계약 정보 갱신: contractId={}", request.contractId());
                    return contractRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("신규 통화선도 계약 등록: contractId={}", request.contractId());
                    return contractRepository.save(
                            FxForwardContract.designate(
                                    request.contractId(),
                                    request.notionalAmountUsd(),
                                    request.contractForwardRate(),
                                    request.contractDate(),
                                    request.maturityDate(),
                                    request.hedgeDesignationDate(),
                                    null,
                                    request.counterpartyCreditRating()
                            )
                    );
                });

        // 만기 초과 검증 — K-IFRS 6.5.10 (도메인 규칙)
        contract.validateValuationDate(request.valuationDate());
        return contract;
    }

    /**
     * IRP 기준 공정가치 계산.
     *
     * <p>잔존일수 계산 후 FxForwardPricing에 선도환율, 할인계수, 공정가치를 순서대로 위임합니다.
     *
     * @param contract 통화선도 계약
     * @param request  평가 요청 (시장 데이터 포함)
     * @return IRP 계산 중간 결과 및 공정가치
     * @see K-IFRS 1113호 (공정가치 측정 Level 2)
     */
    private FairValueCalculation calculateFairValue(FxForwardContract contract, FxForwardValuationRequest request) {
        int remainingDays = contract.calculateRemainingDays(request.valuationDate());

        BigDecimal currentForwardRate = FxForwardPricing.calculateForwardRate(
                request.spotRate(),
                request.krwInterestRate(),
                request.usdInterestRate(),
                remainingDays
        );

        BigDecimal discountFactor = FxForwardPricing.calculateDiscountFactor(
                request.krwInterestRate(), remainingDays);

        BigDecimal fairValue = FxForwardPricing.calculateFairValue(
                currentForwardRate,
                contract.getContractForwardRate(),
                contract.getNotionalAmountUsd(),
                discountFactor
        );

        return new FairValueCalculation(remainingDays, currentForwardRate, discountFactor, fairValue);
    }

    /**
     * 전기 공정가치 조회.
     *
     * <p>최초 평가 시(이전 평가 이력 없음) BigDecimal.ZERO를 반환합니다.
     *
     * @param contractId    계약 ID
     * @param valuationDate 평가기준일
     * @return 전기 공정가치 (최초 평가 시 0)
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 회계처리)
     */
    private BigDecimal getPreviousFairValue(String contractId, java.time.LocalDate valuationDate) {
        return valuationRepository
                .findLatestBeforeDate(contractId, valuationDate)
                .map(FxForwardValuation::getFairValue)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * 평가 결과 저장 — 항상 신규 INSERT (Append-Only).
     *
     * <p>동일 contractId + valuationDate 조합이어도 항상 새로운 레코드를 INSERT합니다.
     * 이전 레코드는 이력으로 영구 보존되며, 최신 레코드가 현행 평가로 사용됩니다.
     * unique constraint 없이 동일 기준일에 대한 재평가(시장 데이터 변경 등)를 허용합니다.
     *
     * @param contract    통화선도 계약
     * @param request     평가 요청
     * @param calculation IRP 계산 결과
     * @return 새로 저장된 FxForwardValuation
     * @see K-IFRS 1109호 6.5.8 (공정가치위험회피 회계처리)
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    private FxForwardValuation saveValuation(
            FxForwardContract contract,
            FxForwardValuationRequest request,
            FairValueCalculation calculation) {

        BigDecimal previousFairValue = getPreviousFairValue(request.contractId(), request.valuationDate());

        FxForwardValuation valuation = FxForwardValuation.of(
                contract,
                request.valuationDate(),
                request.spotRate(),
                request.krwInterestRate(),
                request.usdInterestRate(),
                calculation.remainingDays(),
                calculation.currentForwardRate(),
                calculation.fairValue(),
                previousFairValue
        );

        return valuationRepository.save(valuation);
    }

    /**
     * 평가 결과 단건 조회.
     *
     * @throws BusinessException FX_004 — 존재하지 않는 평가 ID
     */
    @Transactional(readOnly = true)
    public FxForwardValuation findById(Long valuationId) {
        return valuationRepository.findById(valuationId)
                .orElseThrow(() -> new BusinessException("FX_004",
                        "평가 결과를 찾을 수 없습니다. valuationId=" + valuationId));
    }

    /**
     * 전체 평가 이력 목록 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @param pageable 페이지네이션 파라미터
     * @return 평가 이력 페이지 (최신순)
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 공시 의무)
     */
    @Transactional(readOnly = true)
    public Page<FxForwardValuation> findAll(Pageable pageable) {
        return valuationRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 계약별 평가 이력 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @param contractId 계약번호
     * @param pageable   페이지네이션 파라미터
     * @return 계약의 평가 이력 페이지 (최신순)
     * @throws BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1109호 B6.4.12 (유효성 평가 이력 보존)
     */
    @Transactional(readOnly = true)
    public Page<FxForwardValuation> findByContractId(String contractId, Pageable pageable) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("FX_004",
                    "계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        return valuationRepository.findByContract_ContractIdOrderByCreatedAtDesc(contractId, pageable);
    }

    // -------------------------------------------------------------------------
    // 계약 CRUD
    // -------------------------------------------------------------------------

    /**
     * 계약 단건 조회.
     *
     * @param contractId 계약번호
     * @return 통화선도 계약 엔티티
     * @throws BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 — 계약 정보 확인)
     */
    @Transactional(readOnly = true)
    public FxForwardContract findContractById(String contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("FX_004",
                        "계약을 찾을 수 없습니다. contractId=" + contractId));
    }

    /**
     * 전체 계약 목록 조회 (생성일시 내림차순 페이징).
     *
     * @param pageable 페이지네이션 파라미터
     * @return 전체 통화선도 계약 페이지 (최신순, 빈 페이지 가능)
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 — 계약 현황 파악)
     */
    @Transactional(readOnly = true)
    public Page<FxForwardContract> findAllContracts(Pageable pageable) {
        return contractRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 계약 삭제 — 연관 평가 이력도 함께 삭제.
     *
     * <p>PoC 환경에서는 테스트 데이터 정리를 위해 삭제를 허용합니다.
     * 실무에서는 K-IFRS 1107호 공시 의무에 따라 평가 이력 보존이 원칙이므로
     * 삭제 대신 TERMINATED 상태 전환을 권장합니다.
     *
     * @param contractId 삭제할 계약번호
     * @throws BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @Transactional
    public void deleteContract(String contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("FX_004",
                    "계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        valuationRepository.deleteByContract_ContractId(contractId);
        contractRepository.deleteById(contractId);
        log.info("계약 및 연관 평가 이력 삭제: contractId={}", contractId);
    }

    /**
     * 평가 결과 삭제.
     *
     * <p>PoC 환경에서만 허용. 실무에서는 평가 이력은 영구 보존이 원칙입니다.
     *
     * @param valuationId 삭제할 평가 ID
     * @throws BusinessException FX_004 — 존재하지 않는 평가 ID
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @Transactional
    public void deleteValuation(Long valuationId) {
        if (!valuationRepository.existsById(valuationId)) {
            throw new BusinessException("FX_004",
                    "평가 결과를 찾을 수 없습니다. valuationId=" + valuationId);
        }
        valuationRepository.deleteById(valuationId);
        log.info("평가 결과 삭제: valuationId={}", valuationId);
    }
}

