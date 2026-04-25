package com.hedge.prototype.hedge.adapter.web.dto;

/**
 * 헤지 문서화 요약 정보.
 *
 * <p>K-IFRS 1109호 6.4.1(2)에 따라 위험회피관계 지정 시 자동 생성되는 문서화 정보입니다.
 *
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 */
public record DocumentationSummary(

        /** 헤지대상 요약 설명 */
        String hedgedItem,

        /** 헤지수단 요약 설명 */
        String hedgingInstrument,

        /** 회피 대상 위험 설명 */
        String hedgedRisk,

        /** 위험관리 목적 */
        String riskManagementObjective,

        /** 위험회피 전략 */
        String hedgeStrategy,

        /** 유효성 평가 방법 */
        String effectivenessAssessmentMethod
) {}
