package com.hedge.prototype.hedge.application.port;

import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import org.springframework.data.jpa.domain.Specification;

/**
 * 위험회피관계 동적 쿼리 Specification.
 *
 * <p>JpaSpecificationExecutor와 함께 사용하여 null-safe 필터 조합을 지원합니다.
 * 필터 추가 시 메서드 하나만 추가하면 되므로 if-else 분기를 제거합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 */
public class HedgeRelationshipSpec {

    private HedgeRelationshipSpec() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * 헤지 유형 필터.
     *
     * @param hedgeType 헤지 유형 (FAIR_VALUE / CASH_FLOW)
     * @return Specification
     * @see K-IFRS 1109호 6.5.2 (공정가치위험회피)
     * @see K-IFRS 1109호 6.5.4 (현금흐름위험회피)
     */
    public static Specification<HedgeRelationship> hasHedgeType(HedgeType hedgeType) {
        return (root, query, cb) ->
                hedgeType == null ? cb.conjunction() : cb.equal(root.get("hedgeType"), hedgeType);
    }

    /**
     * 위험회피관계 상태 필터.
     *
     * @param status 상태 (DESIGNATED / DISCONTINUED 등)
     * @return Specification
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가)
     */
    public static Specification<HedgeRelationship> hasStatus(HedgeStatus status) {
        return (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /**
     * 적격요건 상태 필터.
     *
     * @param eligibilityStatus 적격요건 상태 (ELIGIBLE / INELIGIBLE)
     * @return Specification
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    public static Specification<HedgeRelationship> hasEligibilityStatus(EligibilityStatus eligibilityStatus) {
        return (root, query, cb) ->
                eligibilityStatus == null ? cb.conjunction() : cb.equal(root.get("eligibilityStatus"), eligibilityStatus);
    }
}
