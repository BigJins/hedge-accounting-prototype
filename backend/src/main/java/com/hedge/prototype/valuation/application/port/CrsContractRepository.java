package com.hedge.prototype.valuation.application.port;

import com.hedge.prototype.valuation.domain.crs.CrsContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CRS 계약 레포지토리.
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 — CRS 계약 관리)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑 헤지비율 산정)
 */
public interface CrsContractRepository extends JpaRepository<CrsContract, String> {

    /**
     * 전체 CRS 계약 목록 조회 (생성일시 내림차순).
     */
    Page<CrsContract> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
