package com.hedge.prototype.valuation.adapter.web;

import com.hedge.prototype.valuation.adapter.web.dto.FxForwardContractResponse;
import com.hedge.prototype.valuation.adapter.web.dto.FxForwardValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.FxForwardValuationResponse;
import com.hedge.prototype.valuation.application.FxForwardValuationUseCase;
import com.hedge.prototype.valuation.application.ValuationResult;
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
 * 통화선도 공정가치 평가 REST API 컨트롤러.
 *
 * <p>K-IFRS 1113호 기준 공정가치 평가 결과를 제공합니다.
 * 민감한 계약 정보(금액, 환율)는 로깅하지 않습니다.
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 — 평가손익 P&L 인식)
 * @see K-IFRS 1113호 (공정가치 수준별 분류 및 공시)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/valuations/fx-forward")
@RequiredArgsConstructor
public class FxForwardValuationController {

    private final FxForwardValuationUseCase valuationService;

    /**
     * 공정가치 평가 실행.
     *
     * <p>신규 평가: HTTP 201 Created 반환.
     * <p>동일 계약 + 동일 평가기준일 중복 요청: HTTP 200 OK 반환 (idempotent).
     *
     * @param request 평가 요청 (계약 정보 + 시장 데이터)
     * @return 평가 결과 DTO (신규: 201, 중복: 200)
     */
    @PostMapping
    public ResponseEntity<FxForwardValuationResponse> valuate(
            @Valid @RequestBody FxForwardValuationRequest request) {

        log.info("공정가치 평가 요청: contractId={}, valuationDate={}",
                request.contractId(), request.valuationDate());

        ValuationResult result = valuationService.valuate(request);
        FxForwardValuationResponse response = FxForwardValuationResponse.fromEntity(result.valuation());

        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 평가 결과 단건 조회.
     *
     * @param id 평가 ID
     * @return 평가 결과 DTO
     * @throws com.hedge.prototype.common.exception.BusinessException FX_004 — 존재하지 않는 ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<FxForwardValuationResponse> findById(@PathVariable Long id) {
        var valuation = valuationService.findById(id);
        return ResponseEntity.ok(FxForwardValuationResponse.fromEntity(valuation));
    }

    /**
     * 전체 평가 이력 목록 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @param pageable 페이지네이션 파라미터 (기본: size=10, sort=createdAt DESC)
     * @return 전체 평가 이력 페이지 (빈 목록 가능)
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 공시 의무)
     */
    @GetMapping
    public ResponseEntity<Page<FxForwardValuationResponse>> findAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                valuationService.findAll(pageable)
                        .map(FxForwardValuationResponse::fromEntity)
        );
    }

    /**
     * 계약별 평가 이력 조회 (생성일시 내림차순 페이징) — Append-Only 최신 기록 우선.
     *
     * @param contractId 계약번호
     * @param pageable   페이지네이션 파라미터 (기본: size=10, sort=createdAt DESC)
     * @return 계약의 평가 이력 페이지
     * @throws com.hedge.prototype.common.exception.BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1109호 B6.4.12 (유효성 평가 이력 보존)
     */
    @GetMapping("/contract/{contractId}")
    public ResponseEntity<Page<FxForwardValuationResponse>> findByContractId(
            @PathVariable String contractId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(
                valuationService.findByContractId(contractId, pageable)
                        .map(FxForwardValuationResponse::fromEntity)
        );
    }

    // -------------------------------------------------------------------------
    // 계약 CRUD 엔드포인트
    // -------------------------------------------------------------------------

    /**
     * 전체 계약 목록 조회 (생성일시 내림차순 페이징).
     *
     * @param pageable 페이지네이션 파라미터 (기본: size=10, sort=createdAt DESC)
     * @return 전체 통화선도 계약 페이지 (최신순, 빈 페이지 가능)
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 — 계약 현황 파악)
     */
    @GetMapping("/contracts")
    public ResponseEntity<Page<FxForwardContractResponse>> findAllContracts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                valuationService.findAllContracts(pageable)
                        .map(FxForwardContractResponse::fromEntity)
        );
    }

    /**
     * 계약 단건 조회.
     *
     * @param contractId 계약번호
     * @return 통화선도 계약 DTO
     * @throws com.hedge.prototype.common.exception.BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 — 계약 정보 확인)
     */
    @GetMapping("/contracts/{contractId}")
    public ResponseEntity<FxForwardContractResponse> findContractById(
            @PathVariable String contractId) {

        var contract = valuationService.findContractById(contractId);
        return ResponseEntity.ok(FxForwardContractResponse.fromEntity(contract));
    }

    /**
     * 계약 삭제 — 연관 평가 이력도 함께 삭제.
     *
     * <p>PoC 환경 전용. 실무에서는 K-IFRS 1107호 공시 의무에 따라
     * 평가 이력 보존이 원칙이므로 삭제 대신 상태 전환을 사용하세요.
     *
     * @param contractId 삭제할 계약번호
     * @return HTTP 204 No Content
     * @throws com.hedge.prototype.common.exception.BusinessException FX_004 — 존재하지 않는 계약
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @DeleteMapping("/contracts/{contractId}")
    public ResponseEntity<Void> deleteContract(@PathVariable String contractId) {
        log.info("계약 삭제 요청: contractId={}", contractId);
        valuationService.deleteContract(contractId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // 평가 삭제 엔드포인트
    // -------------------------------------------------------------------------

    /**
     * 평가 결과 삭제.
     *
     * <p>PoC 환경 전용. 실무에서는 평가 이력은 영구 보존이 원칙입니다.
     *
     * @param id 삭제할 평가 ID
     * @return HTTP 204 No Content
     * @throws com.hedge.prototype.common.exception.BusinessException FX_004 — 존재하지 않는 평가 ID
     * @see K-IFRS 1107호 (금융상품 공시 — 평가 이력 보존 원칙)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteValuation(@PathVariable Long id) {
        log.info("평가 결과 삭제 요청: valuationId={}", id);
        valuationService.deleteValuation(id);
        return ResponseEntity.noContent().build();
    }
}
