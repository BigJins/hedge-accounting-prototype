package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.adapter.web.dto.FxForwardValuationRequest;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardValuation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 통화선도 공정가치 평가 인바운드 포트 (UseCase 인터페이스).
 *
 * <p>구현체: {@link FxForwardValuationService}
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 회계처리)
 * @see K-IFRS 1113호 (공정가치 측정 Level 2)
 */
public interface FxForwardValuationUseCase {

    /**
     * 공정가치 평가 실행.
     *
     * @param request 평가 요청 (계약 정보 + 시장 데이터)
     * @return 평가 결과 래퍼 (신규 여부 포함)
     */
    ValuationResult valuate(@Valid FxForwardValuationRequest request);

    /**
     * 평가 결과 단건 조회.
     *
     * @param valuationId 평가 ID
     * @return 평가 결과 엔티티
     */
    FxForwardValuation findById(Long valuationId);

    /**
     * 전체 평가 이력 목록 조회.
     *
     * @param pageable 페이지네이션
     * @return 평가 이력 페이지 (최신순)
     */
    Page<FxForwardValuation> findAll(Pageable pageable);

    /**
     * 계약별 평가 이력 조회.
     *
     * @param contractId 계약번호
     * @param pageable   페이지네이션
     * @return 계약의 평가 이력 페이지
     */
    Page<FxForwardValuation> findByContractId(String contractId, Pageable pageable);

    /**
     * 계약 단건 조회.
     *
     * @param contractId 계약번호
     * @return 통화선도 계약 엔티티
     */
    FxForwardContract findContractById(String contractId);

    /**
     * 전체 계약 목록 조회.
     *
     * @param pageable 페이지네이션
     * @return 전체 통화선도 계약 페이지
     */
    Page<FxForwardContract> findAllContracts(Pageable pageable);

    /**
     * 계약 삭제 (PoC 전용).
     *
     * @param contractId 삭제할 계약번호
     */
    void deleteContract(String contractId);

    /**
     * 평가 결과 삭제 (PoC 전용).
     *
     * @param valuationId 삭제할 평가 ID
     */
    void deleteValuation(Long valuationId);
}
