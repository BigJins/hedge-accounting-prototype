package com.hedge.prototype.valuation.application.port;

import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 통화선도 계약 Repository.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건 — 계약 관리)
 */
public interface FxForwardContractRepository extends JpaRepository<FxForwardContract, String> {

    /**
     * 전체 계약 목록 생성일시 내림차순 페이징 조회.
     *
     * @param pageable 페이지네이션 파라미터
     * @return 계약 페이지 (최신순)
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 — 계약 현황 파악)
     */
    Page<FxForwardContract> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
