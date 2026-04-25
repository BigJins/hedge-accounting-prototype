package com.hedge.prototype.hedge.application.port;

import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 위험회피관계 Repository.
 *
 * <p>동적 필터 조합은 {@link HedgeRelationshipSpec}과 {@link JpaSpecificationExecutor}를 통해 처리합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 — 이력 보존 필수)
 */
public interface HedgeRelationshipRepository
        extends JpaRepository<HedgeRelationship, String>,
                JpaSpecificationExecutor<HedgeRelationship> {

    /**
     * 특정 통화선도 계약에 연계된 활성 위험회피관계 조회.
     *
     * <p>중복 지정 방지 검증에 사용됩니다.
     * 한 계약은 동시에 하나의 위험회피관계에만 지정될 수 있습니다.
     *
     * @param fxForwardContractId 통화선도 계약 ID
     * @param status              위험회피관계 상태
     * @return 해당 계약에 연계된 DESIGNATED 상태 위험회피관계
     */
    Optional<HedgeRelationship> findByFxForwardContractIdAndStatus(
            String fxForwardContractId, HedgeStatus status);

    /**
     * 특정 IRS/CRS 등 금리상품 계약 기준의 활성 위험회피관계 조회.
     *
     * <p>FX Forward 외 상품의 중복 지정 방지 검증에 사용합니다.
     *
     * @param instrumentId 내부 금리상품 계약 ID
     * @param status       위험회피관계 상태
     * @return 해당 계약의 활성 위험회피관계
     */
    Optional<HedgeRelationship> findByInstrumentIdAndStatus(
            String instrumentId, HedgeStatus status);
}
