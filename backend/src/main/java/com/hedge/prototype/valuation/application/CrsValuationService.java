package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.adapter.web.dto.CrsContractRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationResponse;
import com.hedge.prototype.valuation.application.event.ValuationCompletedEvent;
import com.hedge.prototype.valuation.application.port.CrsContractRepository;
import com.hedge.prototype.valuation.application.port.CrsValuationRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.valuation.domain.crs.CrsContract;
import com.hedge.prototype.valuation.domain.crs.CrsPricing;
import com.hedge.prototype.valuation.domain.crs.CrsValuation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * CRS 공정가치 평가 오케스트레이션 서비스.
 *
 * <p>계약 조회/등록, 평가 계산, 이벤트 발행을 오케스트레이션합니다.
 * 실제 계산 로직은 {@link CrsPricing} 도메인 클래스에 위임합니다.
 * 이 서비스는 Repository 호출, 트랜잭션 관리, 도메인 메서드 조합만 담당합니다.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정)
 * @see K-IFRS 1113호 72~90항 (Level 2 공정가치 측정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrsValuationService implements CrsValuationUseCase {

    private final CrsContractRepository contractRepository;
    private final CrsValuationRepository valuationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * CRS 계산 중간 결과 값 객체.
     */
    private record CrsFairValueCalc(
            BigDecimal krwLegPv,
            BigDecimal foreignLegPv,
            BigDecimal fairValue
    ) {}

    // -----------------------------------------------------------------------
    // 계약 등록
    // -----------------------------------------------------------------------

    /**
     * CRS 계약 등록 — 존재하면 업데이트, 없으면 신규 생성 (upsert).
     *
     * @param request 계약 등록 요청
     * @return 등록된 CRS 계약
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — CRS)
     */
    @Transactional
    public CrsContract registerContract(CrsContractRequest request) {
        return contractRepository.findById(request.contractId())
                .map(existing -> {
                    existing.update(
                            request.notionalAmountKrw(),
                            request.notionalAmountForeign(),
                            request.contractRate(),
                            request.krwFixedRate(),
                            request.krwFloatingIndex(),
                            request.foreignFixedRate(),
                            request.foreignFloatingIndex(),
                            request.maturityDate(),
                            request.counterpartyCreditRating()
                    );
                    log.info("CRS 계약 정보 갱신: contractId={}", request.contractId());
                    return contractRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("CRS 신규 계약 등록: contractId={}", request.contractId());
                    return contractRepository.save(CrsContract.of(
                            request.contractId(),
                            request.notionalAmountKrw(),
                            request.notionalAmountForeign(),
                            request.foreignCurrency(),
                            request.contractRate(),
                            request.krwFixedRate(),
                            request.krwFloatingIndex(),
                            request.foreignFixedRate(),
                            request.foreignFloatingIndex(),
                            request.contractDate(),
                            request.maturityDate(),
                            request.settlementFrequency(),
                            request.dayCountConvention(),
                            request.counterpartyName(),
                            request.counterpartyCreditRating()
                    ));
                });
    }

    // -----------------------------------------------------------------------
    // 평가
    // -----------------------------------------------------------------------

    /**
     * CRS 공정가치 평가 실행 — Append-Only INSERT.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>계약 조회 및 평가기준일 유효성 검증</li>
     *   <li>CrsPricing 도메인 클래스로 원화/외화 다리 PV 및 공정가치 계산</li>
     *   <li>신규 CrsValuation 레코드 INSERT (Append-Only)</li>
     *   <li>ValuationCompletedEvent 발행</li>
     * </ol>
     *
     * @param request 평가 요청
     * @return 평가 결과 응답
     * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — 매 보고기간 말 평가)
     */
    @Transactional
    @Override
    public CrsValuationResponse valuate(CrsValuationRequest request) {
        CrsContract contract = loadAndValidateContract(request);
        CrsFairValueCalc calc = calculateFairValue(contract, request);
        CrsValuation valuation = saveValuation(contract, request, calc);

        log.info("CRS 공정가치 평가 완료: contractId={}, valuationDate={}", request.contractId(), request.valuationDate());

        eventPublisher.publishEvent(new ValuationCompletedEvent(
                valuation.getValuationId(), request.contractId(), request.valuationDate()));

        return CrsValuationResponse.from(valuation);
    }

    /**
     * CRS 평가 결과 단건 조회.
     *
     * @throws BusinessException CRS_404 — 존재하지 않는 평가 ID
     */
    @Transactional(readOnly = true)
    @Override
    public CrsValuationResponse findById(Long valuationId) {
        CrsValuation valuation = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new BusinessException("CRS_404",
                        "CRS 평가 결과를 찾을 수 없습니다. valuationId=" + valuationId));
        return CrsValuationResponse.from(valuation);
    }

    /**
     * 계약별 CRS 평가 이력 조회 (최신순).
     *
     * @throws BusinessException CRS_404 — 존재하지 않는 계약
     */
    @Transactional(readOnly = true)
    @Override
    public Page<CrsValuationResponse> findByContractId(String contractId, Pageable pageable) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("CRS_404", "CRS 계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        return valuationRepository.findByContractIdOrderByCreatedAtDesc(contractId, pageable)
                .map(CrsValuationResponse::from);
    }

    /**
     * CRS 계약 단건 조회.
     *
     * @throws BusinessException CRS_404 — 존재하지 않는 계약
     */
    @Transactional(readOnly = true)
    @Override
    public CrsContract findContractById(String contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("CRS_404",
                        "CRS 계약을 찾을 수 없습니다. contractId=" + contractId));
    }

    /**
     * 전체 CRS 계약 목록 조회 (생성일시 내림차순).
     */
    @Transactional(readOnly = true)
    @Override
    public Page<CrsContract> findAllContracts(Pageable pageable) {
        return contractRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * CRS 계약 삭제 — 연관 평가 이력 먼저 삭제 후 계약 삭제 (계층적 삭제).
     *
     * @throws BusinessException CRS_404 — 존재하지 않는 계약
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @Transactional
    @Override
    public void deleteContract(String contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("CRS_404", "CRS 계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        // 자식(평가 이력) 먼저 삭제 → 부모(계약) 삭제 (FK 제약 보장)
        valuationRepository.deleteByContractId(contractId);
        contractRepository.deleteById(contractId);
        log.info("CRS 계약 및 연관 평가 이력 삭제: contractId={}", contractId);
    }

    // -----------------------------------------------------------------------
    // Private 헬퍼 메서드
    // -----------------------------------------------------------------------

    /**
     * 계약 조회 및 평가기준일 유효성 검증.
     */
    private CrsContract loadAndValidateContract(CrsValuationRequest request) {
        CrsContract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new BusinessException("CRS_404",
                        "CRS 계약을 찾을 수 없습니다. contractId=" + request.contractId()));
        contract.validateValuationDate(request.valuationDate());
        return contract;
    }

    /**
     * CrsPricing 도메인 클래스를 통한 공정가치 계산.
     *
     * <p>원화 다리는 krwFixedRate(고정) 또는 krwDiscountRate(변동 대용)으로 계산.
     * 외화 다리는 foreignFixedRate(고정) 또는 foreignDiscountRate(변동 대용)으로 계산.
     */
    private CrsFairValueCalc calculateFairValue(CrsContract contract, CrsValuationRequest request) {
        int remainingDays = contract.calculateRemainingDays(request.valuationDate());

        // 원화 이자율: 고정금리 있으면 사용, 없으면 할인율로 대용 (PoC)
        BigDecimal krwCouponRate = contract.getKrwFixedRate() != null
                ? contract.getKrwFixedRate()
                : request.krwDiscountRate();

        // 외화 이자율: 고정금리 있으면 사용, 없으면 외화 할인율로 대용 (PoC)
        BigDecimal foreignCouponRate = contract.getForeignFixedRate() != null
                ? contract.getForeignFixedRate()
                : request.foreignDiscountRate();

        BigDecimal foreignLegPv = CrsPricing.calculateForeignLegPv(
                contract.getNotionalAmountForeign(),
                foreignCouponRate,
                request.spotRate(),
                remainingDays,
                contract.getSettlementFrequency(),
                request.foreignDiscountRate()
        );

        BigDecimal krwLegPv = CrsPricing.calculateKrwLegPv(
                contract.getNotionalAmountKrw(),
                krwCouponRate,
                remainingDays,
                contract.getSettlementFrequency(),
                request.krwDiscountRate()
        );

        BigDecimal fairValue = CrsPricing.calculateFairValue(foreignLegPv, krwLegPv);

        return new CrsFairValueCalc(krwLegPv, foreignLegPv, fairValue);
    }

    /**
     * 전기 공정가치 조회 (최초 평가 시 ZERO).
     */
    private BigDecimal getPreviousFairValue(String contractId, java.time.LocalDate valuationDate) {
        return valuationRepository.findLatestBeforeDate(contractId, valuationDate)
                .map(CrsValuation::getFairValue)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * CRS 평가 결과 저장 — Append-Only INSERT.
     */
    private CrsValuation saveValuation(CrsContract contract, CrsValuationRequest request, CrsFairValueCalc calc) {
        BigDecimal previousFairValue = getPreviousFairValue(request.contractId(), request.valuationDate());
        BigDecimal fairValueChange = calc.fairValue().subtract(previousFairValue);

        CrsValuation valuation = CrsValuation.of(
                contract.getContractId(),
                request.valuationDate(),
                request.spotRate(),
                calc.krwLegPv(),
                calc.foreignLegPv(),
                calc.fairValue(),
                fairValueChange
        );
        return valuationRepository.save(valuation);
    }
}

