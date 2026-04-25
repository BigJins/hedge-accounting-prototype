package com.hedge.prototype.valuation.adapter.web;

import com.hedge.prototype.valuation.adapter.web.dto.CrsContractRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationRequest;
import com.hedge.prototype.valuation.adapter.web.dto.CrsValuationResponse;
import com.hedge.prototype.valuation.application.CrsValuationUseCase;
import com.hedge.prototype.valuation.domain.crs.CrsContract;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * CRS(통화스왑) 계약 및 공정가치 평가 REST API 컨트롤러.
 *
 * <p>K-IFRS 1113호 Level 2 기반 CRS 공정가치 평가 및 계약 CRUD를 제공합니다.
 *
 * @see K-IFRS 1109호 6.5.11 (현금흐름 위험회피 — CRS)
 * @see K-IFRS 1109호 B6.4.9 (통화스왑의 헤지비율 산정)
 * @see K-IFRS 1113호 72~90항 (Level 2 공정가치 측정)
 */
@Slf4j
@RestController
@RequestMapping("/api/crs")
@RequiredArgsConstructor
public class CrsValuationController {

    private final CrsValuationUseCase crsValuationService;

    // -----------------------------------------------------------------------
    // 계약 등록
    // -----------------------------------------------------------------------

    /**
     * CRS 계약 등록 (신규) 또는 업데이트.
     *
     * <p>동일 contractId로 재제출 시 기존 계약을 업데이트합니다 (upsert).
     *
     * @param request 계약 등록 요청
     * @return 등록된 CRS 계약
     */
    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public CrsContract registerContract(@Valid @RequestBody CrsContractRequest request) {
        log.info("CRS 계약 등록 요청: contractId={}", request.contractId());
        return crsValuationService.registerContract(request);
    }

    /**
     * 전체 CRS 계약 목록 조회 (생성일시 내림차순 페이징).
     *
     * @param pageable 페이지네이션 파라미터
     * @return CRS 계약 목록 페이지
     */
    @GetMapping("/contracts")
    public Page<CrsContract> getAllContracts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return crsValuationService.findAllContracts(pageable);
    }

    /**
     * CRS 계약 단건 조회.
     *
     * @param contractId 계약 ID
     * @return CRS 계약
     */
    @GetMapping("/contracts/{contractId}")
    public CrsContract getContract(@PathVariable String contractId) {
        return crsValuationService.findContractById(contractId);
    }

    /**
     * CRS 계약 삭제 (연관 평가 이력 포함 계층적 삭제).
     *
     * @param contractId 삭제할 계약 ID
     */
    @DeleteMapping("/contracts/{contractId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContract(@PathVariable String contractId) {
        log.info("CRS 계약 삭제 요청: contractId={}", contractId);
        crsValuationService.deleteContract(contractId);
    }

    // -----------------------------------------------------------------------
    // 평가
    // -----------------------------------------------------------------------

    /**
     * CRS 공정가치 평가 실행 — Append-Only.
     *
     * <p>K-IFRS 1113호 Level 2 기반으로 원화 다리 PV, 외화 다리 PV(원화 환산), 공정가치를 계산합니다.
     * 매 호출마다 새로운 평가 레코드가 INSERT됩니다 (이력 보존).
     *
     * @param request 평가 요청 (계약 ID, 평가기준일, 환율, 원화/외화 할인율)
     * @return 평가 결과
     */
    @PostMapping("/valuate")
    @ResponseStatus(HttpStatus.CREATED)
    public CrsValuationResponse valuate(@Valid @RequestBody CrsValuationRequest request) {
        log.info("CRS 공정가치 평가 요청: contractId={}, valuationDate={}", request.contractId(), request.valuationDate());
        return crsValuationService.valuate(request);
    }

    /**
     * CRS 평가 결과 단건 조회.
     *
     * @param valuationId 평가 결과 ID
     * @return 평가 결과
     */
    @GetMapping("/valuations/{valuationId}")
    public CrsValuationResponse getValuation(@PathVariable Long valuationId) {
        return crsValuationService.findById(valuationId);
    }

    /**
     * 계약별 CRS 평가 이력 조회 (최신순 페이징).
     *
     * @param contractId 계약 ID
     * @param pageable   페이지네이션 파라미터
     * @return 평가 이력 페이지
     */
    @GetMapping("/contracts/{contractId}/valuations")
    public Page<CrsValuationResponse> getValuationsByContract(
            @PathVariable String contractId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return crsValuationService.findByContractId(contractId, pageable);
    }
}
