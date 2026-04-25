package com.hedge.prototype.journal.adapter.web;

import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.adapter.web.dto.IrsFvhAmortizationRequest;
import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.adapter.web.dto.JournalEntryResponse;
import com.hedge.prototype.journal.application.JournalEntryUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 헤지회계 분개 REST API 컨트롤러.
 *
 * <p>K-IFRS 1109호에 따라 공정가치 위험회피 및 현금흐름 위험회피 분개를
 * 자동 생성하고, Excel(.xlsx) 및 PDF 다운로드 기능을 제공합니다.
 *
 * <p>민감한 금액 정보는 로깅하지 않습니다.
 *
 * @see K-IFRS 1109호 6.5.8   (공정가치위험회피 분개)
 * @see K-IFRS 1109호 6.5.11  (현금흐름위험회피 분개)
 * @see K-IFRS 1107호          (헤지회계 공시)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryUseCase journalEntryService;

    /**
     * 헤지회계 분개 생성.
     *
     * <p>위험회피 유형(hedgeType)에 따라 공정가치 위험회피 또는
     * 현금흐름 위험회피 분개를 자동으로 생성합니다.
     * isReclassification=true이면 OCI 재분류 분개도 함께 생성됩니다.
     *
     * @param request 분개 생성 요청
     * @return HTTP 201 Created + 생성된 분개 목록
     * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 — FVH)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 — CFH)
     */
    @PostMapping
    public ResponseEntity<List<JournalEntryResponse>> createEntries(
            @Valid @RequestBody JournalEntryRequest request) {

        log.info("분개 생성 요청: hedgeRelationshipId={}, entryDate={}, hedgeType={}",
                request.hedgeRelationshipId(), request.entryDate(), request.hedgeType());

        List<JournalEntry> entries = journalEntryService.createEntries(request);
        List<JournalEntryResponse> responses = entries.stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();

        log.info("분개 생성 완료: hedgeRelationshipId={}, count={}",
                request.hedgeRelationshipId(), responses.size());

        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    /**
     * IRS FVH 장부금액 조정 상각 분개 생성.
     *
     * <p>K-IFRS 1109호 §6.5.9: 위험회피 중단/만기 후 HEDGED_ITEM_ADJ 잔여 누계 잔액을
     * 잔여 만기에 걸쳐 직선법으로 1기간씩 상각합니다.
     * 1회 호출 = 1기간 상각 분개 1건 반환.
     *
     * <p><b>호출 순서 예시 (3기간 상각)</b>:
     * <ol>
     *   <li>1기: cumulativeAdjBalance=-386M, remainingPeriods=3 → periodAmount ≈ 128.67M</li>
     *   <li>2기: cumulativeAdjBalance=-257.33M, remainingPeriods=2 → periodAmount ≈ 128.67M</li>
     *   <li>3기: cumulativeAdjBalance=-128.67M, remainingPeriods=1 → periodAmount = 128.67M</li>
     * </ol>
     *
     * @param request 상각 요청 DTO
     * @return HTTP 201 Created + 상각 분개
     * @throws com.hedge.prototype.common.exception.BusinessException JE_005 — 잔액 0 (상각 불필요)
     * @see K-IFRS 1109호 §6.5.9
     */
    @PostMapping("/irs-fvh-amortization")
    public ResponseEntity<JournalEntryResponse> createAmortizationEntry(
            @Valid @RequestBody IrsFvhAmortizationRequest request) {

        log.info("IRS FVH 상각 분개 생성 요청: hedgeRelationshipId={}, amortizationDate={}",
                request.hedgeRelationshipId(), request.amortizationDate());

        JournalEntry entry = journalEntryService.createAmortizationEntry(request);
        JournalEntryResponse response = JournalEntryResponse.fromEntity(entry);

        log.info("IRS FVH 상각 분개 생성 완료: hedgeRelationshipId={}",
                request.hedgeRelationshipId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 분개 단건 조회.
     *
     * @param id 분개 ID
     * @return HTTP 200 OK + 분개 정보
     * @throws com.hedge.prototype.common.exception.BusinessException JE_NOT_FOUND — 존재하지 않는 ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponse> findById(@PathVariable Long id) {
        log.info("분개 단건 조회: id={}", id);
        JournalEntry entity = journalEntryService.findById(id);
        return ResponseEntity.ok(JournalEntryResponse.fromEntity(entity));
    }

    /**
     * 분개 목록 조회 (페이징).
     *
     * <p>hedgeRelationshipId 파라미터가 있으면 해당 위험회피관계의 분개만 조회하고,
     * 없으면 전체 분개를 조회합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID 필터 (선택)
     * @param pageable            페이지네이션 (기본: size=20, 최신순)
     * @return HTTP 200 OK + 분개 목록 (페이지)
     * @see K-IFRS 1107호 (헤지회계 공시 이력 조회)
     */
    @GetMapping
    public ResponseEntity<Page<JournalEntryResponse>> findAll(
            @RequestParam(required = false) String hedgeRelationshipId,
            @PageableDefault(size = 20, sort = "entryDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<JournalEntryResponse> result;

        if (hedgeRelationshipId != null && !hedgeRelationshipId.isBlank()) {
            log.info("헤지관계별 분개 조회: hedgeRelationshipId={}", hedgeRelationshipId);
            result = journalEntryService
                    .findByHedgeRelationshipId(hedgeRelationshipId, pageable)
                    .map(JournalEntryResponse::fromEntity);
        } else {
            log.info("전체 분개 조회: pageable={}", pageable);
            result = journalEntryService.findAll(pageable)
                    .map(JournalEntryResponse::fromEntity);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 분개장 Excel(.xlsx) 다운로드.
     *
     * <p>3개 시트(분개장, OCI적립금변동, 재분류이력)로 구성된
     * Excel 파일을 다운로드합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID (필수)
     * @return HTTP 200 OK + Excel 파일 바이트 스트림
     * @see K-IFRS 1107호 (헤지회계 공시 보고서)
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam String hedgeRelationshipId) {

        log.info("Excel 다운로드 요청: hedgeRelationshipId={}", hedgeRelationshipId);

        byte[] excelBytes = journalEntryService.exportToExcel(hedgeRelationshipId);

        String filename = "journal_entries_" + hedgeRelationshipId + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());
        headers.setContentLength(excelBytes.length);

        log.info("Excel 다운로드 완료: hedgeRelationshipId={}, size={}bytes",
                hedgeRelationshipId, excelBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelBytes);
    }

    /**
     * 분개장 PDF 다운로드.
     *
     * <p>표지, 분개 테이블, OCI 재분류 이력, 각주로 구성된
     * PDF 보고서를 다운로드합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID (필수)
     * @return HTTP 200 OK + PDF 파일 바이트 스트림
     * @see K-IFRS 1107호 (헤지회계 공시 보고서)
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam String hedgeRelationshipId) {

        log.info("PDF 다운로드 요청: hedgeRelationshipId={}", hedgeRelationshipId);

        byte[] pdfBytes = journalEntryService.exportToPdf(hedgeRelationshipId);

        String filename = "journal_entries_" + hedgeRelationshipId + "_"
                + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8)
                        .build());
        headers.setContentLength(pdfBytes.length);

        log.info("PDF 다운로드 완료: hedgeRelationshipId={}, size={}bytes",
                hedgeRelationshipId, pdfBytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
