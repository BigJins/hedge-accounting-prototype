package com.hedge.prototype.effectiveness.application;

import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestRequest;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 위험회피 유효성 테스트 인바운드 포트 (UseCase 인터페이스).
 *
 * <p>구현체: {@link EffectivenessTestService}
 *
 * @see K-IFRS 1109호 B6.4.12 (매 보고기간 말 유효성 평가)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법)
 */
public interface EffectivenessTestUseCase {

    /**
     * 유효성 테스트 실행 및 결과 저장.
     *
     * @param request 테스트 요청
     * @return 유효성 테스트 결과 엔티티
     */
    EffectivenessTest runTest(@Valid EffectivenessTestRequest request);

    /**
     * 유효성 테스트 결과 단건 조회.
     *
     * @param effectivenessTestId 테스트 ID
     * @return 테스트 결과 엔티티
     */
    EffectivenessTest findById(Long effectivenessTestId);

    /**
     * 위험회피관계별 유효성 테스트 이력 조회.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션
     * @return 테스트 이력 페이지 (최신순)
     */
    Page<EffectivenessTest> findByHedgeRelationshipId(String hedgeRelationshipId, Pageable pageable);
}
