package com.hedge.prototype.effectiveness.adapter.web;

import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestRequest;
import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestResponse;
import com.hedge.prototype.effectiveness.application.EffectivenessTestUseCase;
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
 * 위험회피 유효성 테스트 REST API 컨트롤러.
 *
 * <p>K-IFRS 1109호 B6.4.12에 따라 매 보고기간 말 Dollar-offset 방법으로
 * 위험회피 유효성을 평가하고 비효과적 부분을 산정하는 API를 제공합니다.
 *
 * <p>민감한 금액 정보는 로깅하지 않습니다.
 *
 * @see K-IFRS 1109호 B6.4.12 (유효성 평가 — 매 보고기간 말)
 * @see K-IFRS 1109호 B6.4.13 (Dollar-offset 방법 허용)
 * @see K-IFRS 1109호 6.5.8  (공정가치 헤지 비효과성)
 * @see K-IFRS 1109호 6.5.11 (현금흐름 헤지 OCI/P&L)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/effectiveness-tests")
@RequiredArgsConstructor
public class EffectivenessTestController {

    private final EffectivenessTestUseCase effectivenessTestService;

    /**
     * 유효성 테스트 실행.
     *
     * <p>Dollar-offset 방법으로 위험회피 유효성을 평가하고 결과를 저장합니다.
     * 공정가치 헤지는 비효과성을 직접 P&L로, 현금흐름 헤지는 Lower of Test로 분리합니다.
     *
     * @param request 유효성 테스트 요청 (위험회피관계 ID, 평가기준일, 당기 변동액)
     * @return HTTP 201 Created + 유효성 테스트 결과
     * @see K-IFRS 1109호 B6.4.12 (Dollar-offset 유효성 평가)
     */
    @PostMapping
    public ResponseEntity<EffectivenessTestResponse> runTest(
            @Valid @RequestBody EffectivenessTestRequest request) {

        log.info("유효성 테스트 요청: hedgeRelationshipId={}, testDate={}, testType={}",
                request.hedgeRelationshipId(), request.testDate(), request.testType());

        EffectivenessTest result = effectivenessTestService.runTest(request);
        EffectivenessTestResponse response = EffectivenessTestResponse.fromEntity(result);

        log.info("유효성 테스트 완료: effectivenessTestId={}, result={}, action={}",
                result.getEffectivenessTestId(), result.getTestResult(), result.getActionRequired());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 유효성 테스트 단건 조회.
     *
     * @param id 유효성 테스트 ID
     * @return HTTP 200 OK + 유효성 테스트 결과
     * @throws com.hedge.prototype.common.exception.BusinessException ET_002 — 존재하지 않는 ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EffectivenessTestResponse> findById(@PathVariable Long id) {
        log.info("유효성 테스트 단건 조회: id={}", id);
        EffectivenessTest entity = effectivenessTestService.findById(id);
        return ResponseEntity.ok(EffectivenessTestResponse.fromEntity(entity));
    }

    /**
     * 위험회피관계별 유효성 테스트 이력 조회 (최신순, 페이지네이션).
     *
     * <p>K-IFRS 1107호 공시 목적의 이력 조회에 사용됩니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID 필터 (필수)
     * @param pageable            페이지네이션 (기본: size=10, 최신순)
     * @return HTTP 200 OK + 유효성 테스트 이력 페이지
     * @see K-IFRS 1107호 (헤지회계 공시 — 유효성 테스트 이력)
     */
    @GetMapping
    public ResponseEntity<Page<EffectivenessTestResponse>> findByHedgeRelationshipId(
            @RequestParam String hedgeRelationshipId,
            @PageableDefault(size = 10, sort = "testDate", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("유효성 테스트 이력 조회: hedgeRelationshipId={}", hedgeRelationshipId);

        Page<EffectivenessTestResponse> result = effectivenessTestService
                .findByHedgeRelationshipId(hedgeRelationshipId, pageable)
                .map(EffectivenessTestResponse::fromEntity);

        return ResponseEntity.ok(result);
    }
}
