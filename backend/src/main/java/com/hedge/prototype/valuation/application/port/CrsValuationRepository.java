package com.hedge.prototype.valuation.application.port;

import com.hedge.prototype.valuation.domain.crs.CrsValuation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * CRS 평가 이력 레포지토리 — Append-Only 이력 관리.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS 평가 이력 보존)
 * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 의무)
 */
public interface CrsValuationRepository extends JpaRepository<CrsValuation, Long> {

    /**
     * 계약별 평가 이력 조회 (생성일시 내림차순 — 최신순).
     */
    Page<CrsValuation> findByContractIdOrderByCreatedAtDesc(String contractId, Pageable pageable);

    /**
     * 특정 기준일 이전의 가장 최근 평가 결과 조회 (전기 공정가치 산출용).
     *
     * @param contractId    계약 ID
     * @param valuationDate 기준일 (이 날짜 이전 레코드 중 가장 최근)
     * @return 직전 평가 결과 (없으면 Optional.empty())
     */
    @Query("SELECT v FROM CrsValuation v WHERE v.contractId = :contractId " +
           "AND v.valuationDate < :valuationDate " +
           "ORDER BY v.createdAt DESC LIMIT 1")
    Optional<CrsValuation> findLatestBeforeDate(
            @Param("contractId") String contractId,
            @Param("valuationDate") LocalDate valuationDate);

    /**
     * 계약 ID로 연관 평가 이력 전체 삭제 (계약 삭제 시 계층적 삭제용).
     */
    void deleteByContractId(String contractId);
}
