package com.hedge.prototype.effectiveness.application.port;

import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 유효성 테스트 결과 Repository.
 *
 * <p>Append-Only 정책에 따라 UPDATE/DELETE는 사용하지 않습니다.
 * 이력 보존은 K-IFRS 1107호 공시 의무 및 감사 추적 요건에 따른 것입니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 유효성 평가 이력)
 * @see K-IFRS 1107호 (금융상품 공시 — 헤지회계 이력 공시 의무)
 */
public interface EffectivenessTestRepository extends JpaRepository<EffectivenessTest, Long> {

    /**
     * 헤지관계별 가장 최근 유효성 테스트 조회.
     *
     * <p>재조정 또는 중단 여부 판단 시 최신 상태 확인에 사용됩니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 최신 유효성 테스트 결과 (없을 시 empty)
     */
    Optional<EffectivenessTest> findTopByHedgeRelationshipIdOrderByTestDateDesc(
            String hedgeRelationshipId);

    /**
     * 헤지관계별 유효성 테스트 전체 이력 조회 (최신순, 페이지네이션).
     *
     * <p>K-IFRS 1107호 공시를 위한 전체 이력 조회에 사용됩니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션
     * @return 유효성 테스트 이력 페이지 (최신순)
     * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력 포함)
     */
    Page<EffectivenessTest> findByHedgeRelationshipIdOrderByTestDateDesc(
            String hedgeRelationshipId, Pageable pageable);

    /**
     * 누적 계산을 위한 헤지관계별 전체 이력 조회 (오름차순).
     *
     * <p>누적 Dollar-offset 계산 시 지정 이후 전체 이력이 필요합니다.
     * 서비스 계층에서 누적값 합산에 사용됩니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 유효성 테스트 이력 목록 (오름차순)
     * @see K-IFRS 1109호 B6.4.12 (누적 Dollar-offset — 지정 이후 누적)
     */
    List<EffectivenessTest> findByHedgeRelationshipIdOrderByTestDateAsc(
            String hedgeRelationshipId);
}
