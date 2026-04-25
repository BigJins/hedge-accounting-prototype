package com.hedge.prototype.hedge.application;

import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDiscontinuationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeRelationshipSummaryResponse;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 헤지 지정 인바운드 포트 (UseCase 인터페이스).
 *
 * <p>Controller는 이 인터페이스를 통해 Application Service를 호출합니다.
 * 구현체: {@link HedgeDesignationService}
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 원칙)
 */
public interface HedgeCommandUseCase {

    /**
     * 헤지 지정 — K-IFRS 1109호 6.4.1 적격요건 자동 검증 포함.
     *
     * @param request 헤지 지정 요청
     * @return 헤지 지정 응답 (적격요건 검증 결과 포함)
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    HedgeDesignationResponse designate(@Valid HedgeDesignationRequest request);

    /**
     * 헤지회계 중단 — K-IFRS 1109호 6.5.6 허용 사유 코드 검증 포함.
     *
     * <p>K-IFRS 1109호 6.5.6: 자발적 중단은 허용되지 않습니다.
     * 허용된 사유 코드({@link com.hedge.prototype.hedge.domain.common.HedgeDiscontinuationReason})로만 중단 가능합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param request             중단 요청 (사유 코드 + 상세 설명)
     * @throws com.hedge.prototype.common.exception.BusinessException HD_011 — 이미 중단된 경우
     * @throws com.hedge.prototype.common.exception.BusinessException HD_012 — 자발적 중단 시도 (6.5.6 위반)
     * @see K-IFRS 1109호 6.5.6 (위험회피관계 자발적 취소 불가)
     * @see K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)
     */
    void discontinue(String hedgeRelationshipId, @Valid HedgeDiscontinuationRequest request);

    /**
     * 위험회피관계 단건 조회.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 헤지 지정 상세 응답
     */
    HedgeDesignationResponse findById(String hedgeRelationshipId);

    /**
     * 위험회피관계 목록 조회 (필터 + 페이지네이션).
     *
     * @param hedgeType         헤지 유형 필터 (nullable)
     * @param status            상태 필터 (nullable)
     * @param eligibilityStatus 적격요건 상태 필터 (nullable)
     * @param pageable          페이지네이션
     * @return 위험회피관계 요약 페이지
     */
    Page<HedgeRelationshipSummaryResponse> findAll(
            HedgeType hedgeType,
            HedgeStatus status,
            EligibilityStatus eligibilityStatus,
            Pageable pageable);
}
