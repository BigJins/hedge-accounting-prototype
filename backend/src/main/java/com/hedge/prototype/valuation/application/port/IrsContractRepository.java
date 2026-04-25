package com.hedge.prototype.valuation.application.port;

import com.hedge.prototype.valuation.domain.irs.IrsContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * IRS 계약 레포지토리.
 *
 * @see K-IFRS 1109호 6.2.1 (위험회피수단 — IRS 계약 관리)
 */
public interface IrsContractRepository extends JpaRepository<IrsContract, String> {

    /**
     * 전체 IRS 계약 목록 조회 (생성일시 내림차순).
     */
    Page<IrsContract> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
