package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.adapter.web.dto.IrsContractRequest;
import com.hedge.prototype.valuation.adapter.web.dto.IrsValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.IrsValuationResponse;
import com.hedge.prototype.valuation.domain.irs.IrsContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * IRS 공정가치 평가 UseCase 인터페이스.
 *
 * @see K-IFRS 1109호 6.5.8 (공정가치 위험회피 — IRS 평가)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — IRS 유효부분 OCI)
 */
public interface IrsValuationUseCase {

    /**
     * IRS 공정가치 평가 실행.
     *
     * @param request 평가 요청 (계약 ID, 평가기준일, 시장 데이터)
     * @return 평가 결과 응답
     */
    IrsValuationResponse valuate(IrsValuationRequest request);

    /**
     * IRS 평가 결과 단건 조회.
     *
     * @param valuationId 평가 결과 ID
     * @return 평가 결과 응답
     */
    IrsValuationResponse findById(Long valuationId);

    /**
     * 계약별 IRS 평가 이력 조회 (최신순).
     *
     * @param contractId 계약 ID
     * @param pageable   페이지네이션
     * @return 평가 이력 페이지
     */
    Page<IrsValuationResponse> findByContractId(String contractId, Pageable pageable);

    /**
     * IRS 계약 단건 조회.
     */
    IrsContract findContractById(String contractId);

    /**
     * 전체 IRS 계약 목록 조회.
     */
    Page<IrsContract> findAllContracts(Pageable pageable);

    /**
     * IRS 계약 등록 또는 갱신 (upsert).
     *
     * @param request 계약 등록 요청
     * @return 등록된 계약 엔티티
     */
    IrsContract registerContract(IrsContractRequest request);

        /**
     * IRS 계약 삭제 (연관 평가 이력 포함).
     */
    void deleteContract(String contractId);
}
