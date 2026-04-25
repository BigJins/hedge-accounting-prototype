package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.adapter.web.dto.IrsContractRequest;
import com.hedge.prototype.valuation.adapter.web.dto.IrsValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.IrsValuationResponse;
import com.hedge.prototype.valuation.application.event.ValuationCompletedEvent;
import com.hedge.prototype.valuation.application.port.IrsContractRepository;
import com.hedge.prototype.valuation.application.port.IrsValuationRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.valuation.domain.irs.IrsContract;
import com.hedge.prototype.valuation.domain.irs.IrsPricing;
import com.hedge.prototype.valuation.domain.irs.IrsValuation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * IRS 공정가치 평가 오케스트레이션 서비스.
 *
 * <p>계약 조회/등록, 평가 계산, 이벤트 발행을 오케스트레이션합니다.
 * 실제 계산 로직은 {@link IrsPricing} 도메인 클래스에 위임합니다.
 * 이 서비스는 Repository 호출, 트랜잭션 관리, 도메인 메서드 조합만 담당합니다.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가손익 P&L)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — IRS 유효부분 OCI)
 * @see K-IFRS 1113호 72~90항 (Level 2 공정가치 측정)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IrsValuationService implements IrsValuationUseCase {

    private final IrsContractRepository contractRepository;
    private final IrsValuationRepository valuationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * IRS 계산 중간 결과 값 객체.
     */
    private record IrsFairValueCalc(
            int remainingDays,
            BigDecimal fixedLegPv,
            BigDecimal floatingLegPv,
            BigDecimal fairValue
    ) {}

    // -----------------------------------------------------------------------
    // 계약 등록
    // -----------------------------------------------------------------------

    /**
     * IRS 계약 등록 — 존재하면 업데이트, 없으면 신규 생성 (upsert).
     *
     * @param request 계약 등록 요청
     * @return 등록된 IRS 계약
     * @see K-IFRS 1109호 6.2.1 (위험회피수단 적격성 — IRS)
     */
    @Transactional
    public IrsContract registerContract(IrsContractRequest request) {
        return contractRepository.findById(request.contractId())
                .map(existing -> {
                    existing.update(
                            request.notionalAmount(),
                            request.fixedRate(),
                            request.floatingRateIndex(),
                            request.floatingSpread(),
                            request.maturityDate(),
                            request.counterpartyCreditRating()
                    );
                    log.info("IRS 계약 정보 갱신: contractId={}", request.contractId());
                    return contractRepository.save(existing);
                })
                .orElseGet(() -> {
                    log.info("IRS 신규 계약 등록: contractId={}", request.contractId());
                    return contractRepository.save(IrsContract.of(
                            request.contractId(),
                            request.notionalAmount(),
                            request.fixedRate(),
                            request.floatingRateIndex(),
                            request.floatingSpread(),
                            request.contractDate(),
                            request.maturityDate(),
                            request.payFixedReceiveFloating(),
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
     * IRS 공정가치 평가 실행 — Append-Only INSERT.
     *
     * <p>처리 흐름:
     * <ol>
     *   <li>계약 조회 및 평가기준일 유효성 검증</li>
     *   <li>IrsPricing 도메인 클래스로 고정/변동 다리 PV 및 공정가치 계산</li>
     *   <li>신규 IrsValuation 레코드 INSERT (Append-Only)</li>
     *   <li>ValuationCompletedEvent 발행</li>
     * </ol>
     *
     * @param request 평가 요청
     * @return 평가 결과 응답
     * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — 매 보고기간 말 평가)
     */
    @Transactional
    @Override
    public IrsValuationResponse valuate(IrsValuationRequest request) {
        IrsContract contract = loadAndValidateContract(request);
        IrsFairValueCalc calc = calculateFairValue(contract, request);
        IrsValuation valuation = saveValuation(contract, request, calc);

        log.info("IRS 공정가치 평가 완료: contractId={}, valuationDate={}", request.contractId(), request.valuationDate());

        eventPublisher.publishEvent(new ValuationCompletedEvent(
                valuation.getValuationId(), request.contractId(), request.valuationDate()));

        return IrsValuationResponse.from(valuation);
    }

    /**
     * IRS 평가 결과 단건 조회.
     *
     * @throws BusinessException IRS_404 — 존재하지 않는 평가 ID
     */
    @Transactional(readOnly = true)
    @Override
    public IrsValuationResponse findById(Long valuationId) {
        IrsValuation valuation = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new BusinessException("IRS_404",
                        "IRS 평가 결과를 찾을 수 없습니다. valuationId=" + valuationId));
        return IrsValuationResponse.from(valuation);
    }

    /**
     * 계약별 IRS 평가 이력 조회 (최신순).
     *
     * @throws BusinessException IRS_404 — 존재하지 않는 계약
     */
    @Transactional(readOnly = true)
    @Override
    public Page<IrsValuationResponse> findByContractId(String contractId, Pageable pageable) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("IRS_404", "IRS 계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        return valuationRepository.findByContractIdOrderByCreatedAtDesc(contractId, pageable)
                .map(IrsValuationResponse::from);
    }

    /**
     * IRS 계약 단건 조회.
     *
     * @throws BusinessException IRS_404 — 존재하지 않는 계약
     */
    @Transactional(readOnly = true)
    @Override
    public IrsContract findContractById(String contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new BusinessException("IRS_404",
                        "IRS 계약을 찾을 수 없습니다. contractId=" + contractId));
    }

    /**
     * 전체 IRS 계약 목록 조회 (생성일시 내림차순).
     */
    @Transactional(readOnly = true)
    @Override
    public Page<IrsContract> findAllContracts(Pageable pageable) {
        return contractRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * IRS 계약 삭제 — 연관 평가 이력 먼저 삭제 후 계약 삭제 (계층적 삭제).
     *
     * <p>PoC 환경에서는 테스트 데이터 정리를 위해 삭제를 허용합니다.
     *
     * @throws BusinessException IRS_404 — 존재하지 않는 계약
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @Transactional
    @Override
    public void deleteContract(String contractId) {
        if (!contractRepository.existsById(contractId)) {
            throw new BusinessException("IRS_404", "IRS 계약을 찾을 수 없습니다. contractId=" + contractId);
        }
        // 자식(평가 이력) 먼저 삭제 → 부모(계약) 삭제 (FK 제약 보장)
        valuationRepository.deleteByContractId(contractId);
        contractRepository.deleteById(contractId);
        log.info("IRS 계약 및 연관 평가 이력 삭제: contractId={}", contractId);
    }

    // -----------------------------------------------------------------------
    // Private 헬퍼 메서드
    // -----------------------------------------------------------------------

    /**
     * 계약 조회 및 평가기준일 유효성 검증.
     */
    private IrsContract loadAndValidateContract(IrsValuationRequest request) {
        IrsContract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new BusinessException("IRS_404",
                        "IRS 계약을 찾을 수 없습니다. contractId=" + request.contractId()));
        contract.validateValuationDate(request.valuationDate());
        return contract;
    }

    /**
     * IrsPricing 도메인 클래스를 통한 공정가치 계산.
     */
    private IrsFairValueCalc calculateFairValue(IrsContract contract, IrsValuationRequest request) {
        // 요청에 명목금액이 있으면 오버라이드, 없으면 계약에서 조회
        BigDecimal notional = request.notionalAmount() != null ? request.notionalAmount() : contract.getNotionalAmount();
        int remainingDays = contract.calculateRemainingDays(request.valuationDate());

        BigDecimal fixedLegPv = IrsPricing.calculateFixedLegPv(
                notional,
                contract.getFixedRate(),
                remainingDays,
                contract.getSettlementFrequency(),
                request.discountRate()
        );

        BigDecimal floatingLegPv = IrsPricing.calculateFloatingLegPv(
                notional,
                request.currentFloatingRate(),
                remainingDays,
                contract.getSettlementFrequency(),
                request.discountRate()
        );

        BigDecimal fairValue = IrsPricing.calculateFairValue(
                fixedLegPv,
                floatingLegPv,
                contract.isPayFixedReceiveFloating()
        );

        return new IrsFairValueCalc(remainingDays, fixedLegPv, floatingLegPv, fairValue);
    }

    /**
     * 전기 공정가치 조회 (최초 평가 시 ZERO).
     */
    private BigDecimal getPreviousFairValue(String contractId, java.time.LocalDate valuationDate) {
        return valuationRepository.findLatestBeforeDate(contractId, valuationDate)
                .map(IrsValuation::getFairValue)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * IRS 평가 결과 저장 — Append-Only INSERT.
     */
    private IrsValuation saveValuation(IrsContract contract, IrsValuationRequest request, IrsFairValueCalc calc) {
        BigDecimal previousFairValue = getPreviousFairValue(request.contractId(), request.valuationDate());
        BigDecimal fairValueChange = calc.fairValue().subtract(previousFairValue);

        IrsValuation valuation = IrsValuation.of(
                contract.getContractId(),
                request.valuationDate(),
                calc.fixedLegPv(),
                calc.floatingLegPv(),
                calc.fairValue(),
                fairValueChange,
                request.discountRate(),
                calc.remainingDays()
        );
        return valuationRepository.save(valuation);
    }
}

