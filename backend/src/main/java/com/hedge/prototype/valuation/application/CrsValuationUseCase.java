package com.hedge.prototype.valuation.application;

import com.hedge.prototype.valuation.adapter.web.dto.CrsContractRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationResponse;
import com.hedge.prototype.valuation.domain.crs.CrsContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * CRS 공정가치 평가 UseCase 인터페이스.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 유효부분 OCI)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑 헤지비율 산정)
 */
public interface CrsValuationUseCase {

    /**
     * CRS 공정가치 평가 실행.
     *
     * @param request 평가 요청 (계약 ID, 평가기준일, 시장 데이터)
     * @return 평가 결과 응답
     */
    CrsValuationResponse valuate(CrsValuationRequest request);

    /**
     * CRS 평가 결과 단건 조회.
     */
    CrsValuationResponse findById(Long valuationId);

    /**
     * 계약별 CRS 평가 이력 조회 (최신순).
     */
    Page<CrsValuationResponse> findByContractId(String contractId, Pageable pageable);

    /**
     * CRS 계약 단건 조회.
     */
    CrsContract findContractById(String contractId);

    /**
     * 전체 CRS 계약 목록 조회.
     */
    Page<CrsContract> findAllContracts(Pageable pageable);

    /**
     * CRS 계약 등록 또는 갱신 (upsert).
     *
     * @param request 계약 등록 요청
     * @return 등록된 계약 엔티티
     */
    CrsContract registerContract(CrsContractRequest request);

        /**
     * CRS 계약 삭제 (연관 평가 이력 포함).
     */
    void deleteContract(String contractId);
}
