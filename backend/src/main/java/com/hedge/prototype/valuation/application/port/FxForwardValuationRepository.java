package com.hedge.prototype.valuation.application.port;

import com.hedge.prototype.valuation.domain.fxforward.FxForwardValuation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 통화선도 공정가치 평가 Repository.
 *
 * <p>평가 이력은 영구 보존 — 삭제 쿼리 금지.
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 이력 보존)
 * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 공시 의무)
 */
public interface FxForwardValuationRepository extends JpaRepository<FxForwardValuation, Long> {

    /**
     * 전체 평가 이력 목록 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 공시 의무)
     */
    Page<FxForwardValuation> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 계약별 평가 이력 목록 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @see K-IFRS 1109호 B6.4.12 (유효성 평가 이력 보존)
     */
    Page<FxForwardValuation> findByContract_ContractIdOrderByCreatedAtDesc(String contractId, Pageable pageable);

    /**
     * 계약번호 + 평가기준일로 기존 평가 조회 — 하위 호환용 (upsert 패턴 제거 후 미사용이나 유지).
     */
    Optional<FxForwardValuation> findByContract_ContractIdAndValuationDate(
            String contractId, LocalDate valuationDate);

    /**
     * 계약별 평가 이력 전체 조회 (기준일 오름차순).
     */
    @Query("SELECT v FROM FxForwardValuation v WHERE v.contract.contractId = :contractId ORDER BY v.valuationDate ASC")
    List<FxForwardValuation> findByContractIdOrderByValuationDateAsc(@Param("contractId") String contractId);

    /**
     * 계약의 직전 평가 결과 조회 — 공정가치 변동액 계산 시 사용.
     */
    @Query("SELECT v FROM FxForwardValuation v WHERE v.contract.contractId = :contractId AND v.valuationDate < :valuationDate ORDER BY v.valuationDate DESC LIMIT 1")
    Optional<FxForwardValuation> findLatestBeforeDate(
            @Param("contractId") String contractId,
            @Param("valuationDate") LocalDate valuationDate);

    /**
     * 계약번호로 연관 평가 이력 전체 삭제 — 계약 삭제 시 연관 데이터 정리용.
     *
     * <p>PoC 환경 전용. 실무에서는 K-IFRS 1107호 공시 의무에 따라
     * 평가 이력은 영구 보존이 원칙입니다.
     *
     * @param contractId 삭제 대상 계약번호
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    void deleteByContract_ContractId(String contractId);
}
