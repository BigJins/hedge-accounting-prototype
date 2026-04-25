package com.hedge.prototype.hedge.adapter.web;

import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.HedgeStatus;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDiscontinuationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeRelationshipSummaryResponse;
import com.hedge.prototype.hedge.application.HedgeCommandUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 헤지 지정 및 K-IFRS 적격요건 자동 검증 REST API 컨트롤러.
 *
 * <p>K-IFRS 1109호 6.4.1 기준 3가지 적격요건(경제적 관계, 신용위험, 헤지비율)을
 * 자동 검증하고 헤지 지정 결과를 반환합니다.
 *
 * <p>민감한 계약 정보(금액, 환율)는 로깅하지 않습니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 * @see K-IFRS 1109호 6.4.1(2) (공식 지정·문서화 의무)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hedge-relationships")
@RequiredArgsConstructor
public class HedgeDesignationController {

    private final HedgeCommandUseCase hedgeDesignationService;

    /**
     * 헤지 지정 — K-IFRS 1109호 6.4.1 적격요건 자동 검증 포함.
     *
     * <p>적격요건 충족 시: HTTP 201 Created + 지정 결과 반환.
     * <p>적격요건 미충족 시: HTTP 422 Unprocessable Entity + 상세 검증 결과 반환.
     * <p>사전 검증 실패 시(HD_001, HD_005~HD_008): HTTP 400 Bad Request.
     *
     * @param request 헤지 지정 요청 (헤지대상항목 + 헤지수단 + 위험관리 목적 등)
     * @return 헤지 지정 결과 (적격요건 검증 결과 포함)
     * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
     */
    @PostMapping
    public ResponseEntity<HedgeDesignationResponse> designate(
            @Valid @RequestBody HedgeDesignationRequest request) {

        log.info("헤지 지정 요청: hedgeType={}, hedgedRisk={}, instrumentType={}",
                request.hedgeType(), request.hedgedRisk(), request.instrumentType());

        HedgeDesignationResponse response = hedgeDesignationService.designate(request);

        if ("ELIGIBLE".equals(response.eligibilityStatus())) {
            log.info("헤지 지정 완료: hedgeRelationshipId={}", response.hedgeRelationshipId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } else {
            log.warn("헤지 지정 적격요건 미충족: overallResult=FAIL");
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }
    }

    /**
     * 위험회피관계 단건 조회.
     *
     * @param id 위험회피관계 ID (예: HR-2026-001)
     * @return 위험회피관계 상세 정보 (적격요건 검증 결과 포함)
     * @throws com.hedge.prototype.common.exception.BusinessException HD_009 — 존재하지 않는 ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<HedgeDesignationResponse> findById(@PathVariable String id) {
        log.info("위험회피관계 단건 조회: id={}", id);
        return ResponseEntity.ok(hedgeDesignationService.findById(id));
    }

    /**
     * 헤지회계 중단 — K-IFRS 1109호 6.5.6 허용 사유 검증 포함.
     *
     * <p>K-IFRS 1109호 6.5.6: 위험회피관계를 자발적으로 취소할 수 없습니다.
     * 허용된 사유 코드({@link com.hedge.prototype.hedge.domain.common.HedgeDiscontinuationReason})로만 중단 가능합니다.
     * 자발적 중단(VOLUNTARY_DISCONTINUATION) 시도 시 HTTP 400 Bad Request가 반환됩니다.
     *
     * <p>허용 사유 코드:
     * <ul>
     *   <li>RISK_MANAGEMENT_OBJECTIVE_CHANGED — 위험관리 목적 변경</li>
     *   <li>HEDGE_INSTRUMENT_EXPIRED — 헤지수단 만기/소멸</li>
     *   <li>HEDGE_ITEM_NO_LONGER_EXISTS — 피헤지항목 소멸</li>
     *   <li>ELIGIBILITY_CRITERIA_NOT_MET — 적격요건 미충족</li>
     * </ul>
     *
     * @param id      위험회피관계 ID
     * @param request 중단 요청 (사유 코드 필수 + 상세 설명 선택 + 중단일 선택)
     * @return 204 No Content (성공)
     * @see K-IFRS 1109호 6.5.6 (자발적 취소 불가 원칙)
     * @see K-IFRS 1109호 6.5.7 (현금흐름 헤지 중단 시 OCI 처리)
     * @see K-IFRS 1109호 B6.5.26 (중단 후 OCI 잔액 처리)
     */
    @PatchMapping("/{id}/discontinue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discontinue(
            @PathVariable String id,
            @Valid @RequestBody HedgeDiscontinuationRequest request) {

        log.info("헤지회계 중단 요청: hedgeRelationshipId={}, 사유코드={}", id, request.reason());
        hedgeDesignationService.discontinue(id, request);
        log.info("헤지회계 중단 완료: hedgeRelationshipId={}", id);
    }

    /**
     * 위험회피관계 목록 조회 (필터 + 페이지네이션).
     *
     * <p>K-IFRS 1107호 공시 의무에 따라 헤지회계 적용 중인 위험회피관계
     * 목록을 조회하는 데 사용합니다.
     *
     * @param hedgeType         헤지 유형 필터 (FAIR_VALUE / CASH_FLOW, nullable)
     * @param status            상태 필터 (DESIGNATED / DISCONTINUED 등, nullable)
     * @param eligibilityStatus 적격요건 상태 필터 (ELIGIBLE / INELIGIBLE, nullable)
     * @param page              페이지 번호 (0-based, 기본: 0)
     * @param size              페이지 크기 (기본: 20)
     * @return 위험회피관계 요약 목록 (페이지네이션)
     * @see K-IFRS 1107호 (금융상품 공시 — 헤지회계 공시 의무)
     */
    @GetMapping
    public ResponseEntity<Page<HedgeRelationshipSummaryResponse>> findAll(
            @RequestParam(required = false) HedgeType hedgeType,
            @RequestParam(required = false) HedgeStatus status,
            @RequestParam(required = false) EligibilityStatus eligibilityStatus,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("위험회피관계 목록 조회: hedgeType={}, status={}, eligibilityStatus={}",
                hedgeType, status, eligibilityStatus);

        Page<HedgeRelationshipSummaryResponse> result =
                hedgeDesignationService.findAll(hedgeType, status, eligibilityStatus, pageable);

        return ResponseEntity.ok(result);
    }
}
