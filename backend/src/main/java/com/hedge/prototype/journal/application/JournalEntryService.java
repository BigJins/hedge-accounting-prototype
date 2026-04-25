package com.hedge.prototype.journal.application;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.journal.domain.AccountCode;
import com.hedge.prototype.journal.domain.CashFlowHedgeJournalGenerator;
import com.hedge.prototype.journal.domain.FairValueHedgeJournalGenerator;
import com.hedge.prototype.journal.domain.IrsFairValueHedgeJournalGenerator;
import com.hedge.prototype.journal.domain.IrsFvhAmortizationJournalGenerator;
import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.domain.JournalEntryType;
import com.hedge.prototype.journal.domain.OciReclassificationJournalGenerator;
import com.hedge.prototype.journal.domain.ReclassificationReason;
import com.hedge.prototype.journal.adapter.web.dto.IrsFvhAmortizationRequest;
import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.application.port.JournalEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 헤지회계 분개 서비스.
 *
 * <p>공정가치 위험회피(FVH)와 현금흐름 위험회피(CFH) 분개를 자동 생성하고,
 * Excel(.xlsx) 및 PDF 다운로드 기능을 제공합니다.
 *
 * <p><b>분개 불변성 원칙</b>: 생성된 분개는 수정하지 않습니다.
 * 취소가 필요한 경우 역분개({@link JournalEntryType#REVERSING_ENTRY}) 패턴을 사용합니다.
 *
 * @see K-IFRS 1109호 6.5.8   (공정가치위험회피 분개)
 * @see K-IFRS 1109호 6.5.11  (현금흐름위험회피 분개)
 * @see K-IFRS 1109호 6.5.11(다) (OCI 재분류 분개)
 * @see K-IFRS 1107호           (헤지회계 공시)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalEntryService implements JournalEntryUseCase {

    private final JournalEntryRepository journalEntryRepository;

    // -----------------------------------------------------------------------
    // 분개 생성
    // -----------------------------------------------------------------------

    /**
     * 헤지회계 분개 생성 및 저장.
     *
     * <p>hedgeType에 따라 공정가치 위험회피 또는 현금흐름 위험회피 분개를 생성합니다.
     * isReclassification=true이면 OCI 재분류 분개를 추가로 생성합니다.
     *
     * <p>저장 전 차대변 합계를 검증합니다 (단일 분개 내 amount 일치 보장).
     *
     * @param request 분개 생성 요청
     * @return 생성된 분개 목록
     * @throws BusinessException JE_001 — 분개 무결성 위반 (amount ≤ 0 또는 차대변 계정 동일)
     * @throws BusinessException JE_002 — 유형별 필수 필드 누락
     *                                    (FVH: instrumentFvChange 또는 hedgedItemFvChange가 null;
     *                                     CFH: effectiveAmount 또는 ineffectiveAmount가 null)
     * @throws BusinessException JE_003 — OCI 재분류 시 필수 필드 누락 또는 유효하지 않은 enum 값
     * @throws BusinessException JE_004 — 지원하지 않는 hedgeType
     * @see K-IFRS 1109호 6.5.8  (공정가치위험회피)
     * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피)
     */
    @Transactional
    public List<JournalEntry> createEntries(JournalEntryRequest request) {
        log.info("분개 생성 요청: hedgeRelationshipId={}, entryDate={}, hedgeType={}",
                request.hedgeRelationshipId(), request.entryDate(), request.hedgeType());

        List<JournalEntry> entries = new ArrayList<>();

        // 1. 위험회피 유형별 분개 생성
        if (request.hedgeType() == HedgeType.FAIR_VALUE) {
            validateFairValueFields(request);

            // IRS FVH: 2단계 엔진 — IRS 전용 분개 생성기로 라우팅
            // FX Forward FVH: 1단계 엔진 — 기존 FVH 생성기 유지 (null = FX_FORWARD 하위호환)
            // K-IFRS 1109호 6.5.8: 계정과목 구조는 동일, 적요·참조조항이 수단 유형별로 특화됨
            if (InstrumentType.IRS == request.instrumentType()) {
                entries.addAll(IrsFairValueHedgeJournalGenerator.generate(
                        request.hedgeRelationshipId(),
                        request.entryDate(),
                        request.instrumentFvChange(),
                        request.hedgedItemFvChange()));
            } else {
                // FX_FORWARD (null 포함) — 기존 1단계 경로 유지
                entries.addAll(FairValueHedgeJournalGenerator.generate(
                        request.hedgeRelationshipId(),
                        request.entryDate(),
                        request.instrumentFvChange(),
                        request.hedgedItemFvChange()));
            }

        } else if (request.hedgeType() == HedgeType.CASH_FLOW) {
            validateCashFlowFields(request);
            entries.addAll(CashFlowHedgeJournalGenerator.generate(
                    request.hedgeRelationshipId(),
                    request.entryDate(),
                    request.effectiveAmount(),
                    request.ineffectiveAmount()));

        } else {
            throw new BusinessException("JE_004",
                    "지원하지 않는 위험회피 유형입니다: " + request.hedgeType());
        }

        // 2. OCI 재분류 분개 추가 (선택적)
        if (Boolean.TRUE.equals(request.isReclassification())) {
            validateReclassificationFields(request);
            JournalEntry reclassEntry = OciReclassificationJournalGenerator.generate(
                    request.hedgeRelationshipId(),
                    request.entryDate(),
                    request.reclassificationAmount(),
                    AccountCode.valueOf(request.plAccountCode()),
                    ReclassificationReason.valueOf(request.reclassificationReason()),
                    request.originalOciEntryDate());
            entries.add(reclassEntry);
        }

        // 3. 각 분개 차대변 무결성 검증 (단일 분개 내 amount > 0, debit ≠ credit)
        validateEntries(entries);

        // 4. 저장 (Append-Only)
        List<JournalEntry> saved = journalEntryRepository.saveAll(entries);

        log.info("분개 저장 완료: hedgeRelationshipId={}, count={}",
                request.hedgeRelationshipId(), saved.size());

        return saved;
    }

    /**
     * IRS FVH 장부금액 조정 상각 분개 생성 및 저장.
     *
     * <p>K-IFRS 1109호 §6.5.9: 위험회피 관계 중단 또는 만기 후 잔여 HEDGED_ITEM_ADJ
     * 누계 잔액을 잔여 만기에 걸쳐 직선법으로 상각합니다.
     * 1회 호출 = 1기간 상각 분개 1건 생성.
     *
     * <p>다음 기간 상각 시에는 갱신된 {@code cumulativeAdjBalance}(이전 잔액 − 이번 상각액)와
     * 감소된 {@code remainingPeriods}(이전 값 − 1)로 재호출합니다.
     *
     * @param request 상각 요청 DTO
     * @return 생성·저장된 상각 분개 (단일)
     * @throws BusinessException JE_005 — cumulativeAdjBalance = 0 (상각 불필요)
     * @throws BusinessException JE_001 — 분개 무결성 위반
     * @see K-IFRS 1109호 §6.5.9
     */
    @Transactional
    public JournalEntry createAmortizationEntry(IrsFvhAmortizationRequest request) {
        log.info("IRS FVH 상각 요청: hedgeRelationshipId={}, amortizationDate={}, remainingPeriods={}",
                request.hedgeRelationshipId(), request.amortizationDate(), request.remainingPeriods());

        JournalEntry entry;
        try {
            entry = IrsFvhAmortizationJournalGenerator.generate(
                    request.hedgeRelationshipId(),
                    request.amortizationDate(),
                    request.cumulativeAdjBalance(),
                    request.remainingPeriods());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("JE_005", e.getMessage());
        }

        // 분개 무결성 검증 (amount > 0, debit ≠ credit)
        if (entry.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException("JE_001",
                    "상각 분개 금액은 0보다 커야 합니다.");
        }
        if (entry.getDebitAccount() == entry.getCreditAccount()) {
            throw new BusinessException("JE_001",
                    "차변 계정과 대변 계정이 동일합니다: " + entry.getDebitAccount());
        }

        JournalEntry saved = journalEntryRepository.save(entry);

        log.info("IRS FVH 상각 분개 저장 완료: hedgeRelationshipId={}, journalEntryId={}",
                request.hedgeRelationshipId(), saved.getJournalEntryId());

        return saved;
    }

    /**
     * 분개 단건 조회.
     *
     * @param id 분개 ID
     * @return 분개 엔티티
     * @throws BusinessException JE_NOT_FOUND — 존재하지 않는 ID
     */
    @Transactional(readOnly = true)
    public JournalEntry findById(Long id) {
        return journalEntryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("JE_NOT_FOUND",
                        "분개를 찾을 수 없습니다. id=" + id));
    }

    /**
     * 위험회피관계별 분개 이력 조회 (페이징).
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션
     * @return 분개 목록 (페이지)
     */
    @Transactional(readOnly = true)
    public Page<JournalEntry> findByHedgeRelationshipId(String hedgeRelationshipId, Pageable pageable) {
        return journalEntryRepository
                .findByHedgeRelationshipIdOrderByEntryDateDescCreatedAtDesc(hedgeRelationshipId, pageable);
    }

    /**
     * 전체 분개 목록 조회 (페이징).
     *
     * @param pageable 페이지네이션
     * @return 전체 분개 목록 (페이지)
     */
    @Transactional(readOnly = true)
    public Page<JournalEntry> findAll(Pageable pageable) {
        return journalEntryRepository.findAllByOrderByEntryDateDescCreatedAtDesc(pageable);
    }

    // -----------------------------------------------------------------------
    // Excel 다운로드
    // -----------------------------------------------------------------------

    /**
     * 헤지관계별 분개장 Excel(.xlsx) 파일 생성.
     *
     * <p>3개 시트로 구성:
     * <ol>
     *   <li>분개장 — 전체 분개 이력</li>
     *   <li>OCI적립금변동 — 현금흐름위험회피적립금 변동 내역</li>
     *   <li>재분류이력 — OCI 재분류 분개 이력</li>
     * </ol>
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return Excel 파일 바이트 배열
     * @throws BusinessException JE_EXPORT_ERROR — 파일 생성 오류
     * @see K-IFRS 1107호 (헤지회계 공시 — 분개 이력)
     */
    @Transactional(readOnly = true)
    public byte[] exportToExcel(String hedgeRelationshipId) {
        List<JournalEntry> entries =
                journalEntryRepository.findByHedgeRelationshipIdOrderByEntryDateAsc(hedgeRelationshipId);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 공통 스타일 생성
            ExcelStyles styles = new ExcelStyles(workbook);

            // 시트 1: 분개장
            createJournalSheet(workbook, styles, entries);

            // 시트 2: OCI 적립금 변동
            createOciReserveSheet(workbook, styles, entries, hedgeRelationshipId);

            // 시트 3: 재분류 이력
            createReclassificationSheet(workbook, styles, entries);

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            log.error("Excel 파일 생성 오류: hedgeRelationshipId={}", hedgeRelationshipId, e);
            throw new BusinessException("JE_EXPORT_ERROR", "Excel 파일 생성 중 오류가 발생했습니다.");
        }
    }

    /**
     * 시트 1: 분개장 생성.
     *
     * <p>Row 0: 보고서 타이틀 (병합), Row 1: 헤더, Row 2+: 데이터
     */
    private void createJournalSheet(XSSFWorkbook workbook, ExcelStyles styles, List<JournalEntry> entries) {
        XSSFSheet sheet = workbook.createSheet("분개장");

        String[] headers = {
                "분개ID", "기준일", "분개유형",
                "차변계정", "차변금액",
                "대변계정", "대변금액",
                "적요", "K-IFRS근거", "생성일시"
        };
        int lastCol = headers.length - 1;

        // 타이틀 행 (병합)
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("헤지회계 분개장 (Hedge Accounting Journal Entry Ledger)");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        // 헤더 행
        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // 데이터 행
        int rowNum = 2;
        for (JournalEntry entry : entries) {
            Row row = sheet.createRow(rowNum++);
            CellStyle textStyle  = styles.getEntryTypeStyle(workbook, entry.getEntryType(), false);
            CellStyle numStyle   = styles.getEntryTypeStyle(workbook, entry.getEntryType(), true);

            createCell(row, 0, entry.getJournalEntryId().toString(), textStyle);
            createDateCell(row, 1, entry.getEntryDate(), styles.dateCellStyle);
            createCell(row, 2, entry.getEntryType().getKoreanName(), textStyle);
            createCell(row, 3, entry.getDebitAccount().getKoreanName(), textStyle);
            createNumericCell(row, 4, entry.getAmount(), numStyle);
            createCell(row, 5, entry.getCreditAccount().getKoreanName(), textStyle);
            createNumericCell(row, 6, entry.getAmount(), numStyle);
            createCell(row, 7, entry.getDescription(), textStyle);
            createCell(row, 8, entry.getIfrsReference(), textStyle);
            createCell(row, 9,
                    entry.getCreatedAt() != null
                            ? entry.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            : "",
                    textStyle);
        }

        // 컬럼 너비 설정
        int[] colWidths = {12, 14, 22, 26, 18, 26, 18, 60, 28, 22};
        for (int i = 0; i < colWidths.length; i++) {
            sheet.setColumnWidth(i, colWidths[i] * 256);
        }
    }

    /**
     * 시트 2: OCI 적립금 변동 시트 생성.
     *
     * <p>기말OCI잔액 = 기초잔액 + 당기유효부분인식 - 당기재분류
     */
    private void createOciReserveSheet(XSSFWorkbook workbook, ExcelStyles styles,
                                       List<JournalEntry> entries, String hedgeRelationshipId) {
        XSSFSheet sheet = workbook.createSheet("OCI적립금변동");

        String[] headers = {
                "기준일", "기초OCI잔액", "당기유효부분인식", "당기재분류", "기말OCI잔액", "헤지관계ID"
        };

        // 타이틀 행
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("현금흐름위험회피적립금 변동 내역 (OCI Reserve Movement)");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row headerRow = sheet.createRow(1);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        // 날짜별 그룹화하여 OCI 누계 계산
        Map<LocalDate, List<JournalEntry>> byDate = entries.stream()
                .collect(Collectors.groupingBy(JournalEntry::getEntryDate,
                        java.util.TreeMap::new, Collectors.toList()));

        BigDecimal runningBalance = BigDecimal.ZERO;
        int rowNum = 2;

        for (Map.Entry<LocalDate, List<JournalEntry>> dateGroup : byDate.entrySet()) {
            LocalDate date = dateGroup.getKey();
            List<JournalEntry> dayEntries = dateGroup.getValue();

            BigDecimal beginBalance = runningBalance;
            BigDecimal effectiveRecognized = BigDecimal.ZERO;
            BigDecimal reclassified = BigDecimal.ZERO;

            for (JournalEntry e : dayEntries) {
                if (e.getEntryType() == JournalEntryType.CASH_FLOW_HEDGE_EFFECTIVE) {
                    // 유효부분: 차변=DRV_ASSET(이익) → OCI 증가, 차변=CFHR_OCI(손실) → OCI 감소
                    if (e.getDebitAccount() == AccountCode.DRV_ASSET) {
                        effectiveRecognized = effectiveRecognized.add(e.getAmount());
                    } else {
                        effectiveRecognized = effectiveRecognized.subtract(e.getAmount());
                    }
                } else if (e.getEntryType() == JournalEntryType.OCI_RECLASSIFICATION) {
                    // 재분류: 차변=CFHR_OCI → OCI 감소(재분류 이익), 대변=CFHR_OCI → OCI 증가(재분류 손실 취소)
                    if (e.getDebitAccount() == AccountCode.CFHR_OCI) {
                        reclassified = reclassified.add(e.getAmount());
                    } else {
                        reclassified = reclassified.subtract(e.getAmount());
                    }
                }
            }

            runningBalance = beginBalance.add(effectiveRecognized).subtract(reclassified);

            Row row = sheet.createRow(rowNum++);
            createDateCell(row, 0, date, styles.dateCellStyle);
            createNumericCell(row, 1, beginBalance, styles.amountCellStyle);
            createNumericCell(row, 2, effectiveRecognized, styles.amountCellStyle);
            createNumericCell(row, 3, reclassified, styles.amountCellStyle);
            createNumericCell(row, 4, runningBalance, styles.amountCellStyle);
            createCell(row, 5, hedgeRelationshipId, null);
        }

        int[] ociColWidths = {14, 18, 20, 18, 18, 28};
        for (int i = 0; i < ociColWidths.length; i++) {
            sheet.setColumnWidth(i, ociColWidths[i] * 256);
        }
    }

    /**
     * 시트 3: 재분류 이력 시트 생성.
     */
    private void createReclassificationSheet(XSSFWorkbook workbook, ExcelStyles styles,
                                              List<JournalEntry> entries) {
        XSSFSheet sheet = workbook.createSheet("재분류이력");

        String[] headers = {
                "재분류일", "원인", "최초OCI인식일", "재분류금액", "대응P&L계정", "헤지관계ID"
        };

        // 타이틀 행
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(22f);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("OCI 재분류 이력 (OCI Reclassification History)");
        titleCell.setCellStyle(styles.titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));

        Row headerRow = sheet.createRow(1);
        headerRow.setHeightInPoints(18f);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.headerStyle);
        }

        int rowNum = 2;
        for (JournalEntry entry : entries) {
            if (entry.getEntryType() != JournalEntryType.OCI_RECLASSIFICATION) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            createDateCell(row, 0, entry.getEntryDate(), styles.dateCellStyle);
            createCell(row, 1,
                    entry.getReclassificationReason() != null
                            ? entry.getReclassificationReason().name() : "", null);
            createDateCell(row, 2, entry.getOriginalOciEntryDate(), styles.dateCellStyle);
            createNumericCell(row, 3, entry.getAmount(), styles.amountCellStyle);
            // P&L 계정: 차변이 CFHR_OCI이면 대변이 P&L 계정, 아니면 차변
            AccountCode plAccount = entry.getDebitAccount() == AccountCode.CFHR_OCI
                    ? entry.getCreditAccount() : entry.getDebitAccount();
            createCell(row, 4, plAccount.getKoreanName(), styles.dateCellStyle);
            createCell(row, 5, entry.getHedgeRelationshipId(), styles.dateCellStyle);
        }

        int[] reclassColWidths = {14, 28, 14, 18, 24, 28};
        for (int i = 0; i < reclassColWidths.length; i++) {
            sheet.setColumnWidth(i, reclassColWidths[i] * 256);
        }
    }

    // -----------------------------------------------------------------------
    // PDF 다운로드
    // -----------------------------------------------------------------------

    /**
     * 헤지관계별 분개장 PDF 파일 생성.
     *
     * <p>구성:
     * <ol>
     *   <li>표지 — 제목, 헤지관계ID, 생성일시, K-IFRS 기준</li>
     *   <li>분개 테이블 — 전체 분개 이력</li>
     *   <li>OCI 재분류 이력 섹션</li>
     *   <li>각주 — K-IFRS 1109호 조항 설명</li>
     * </ol>
     *
     * <p>한글 폰트: 클래스패스 NanumGothic.ttf 우선 사용,
     * 없으면 Helvetica fallback (PoC 허용 — 한글 깨질 수 있음).
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return PDF 파일 바이트 배열
     * @throws BusinessException JE_EXPORT_ERROR — 파일 생성 오류
     * @see K-IFRS 1107호 (헤지회계 공시 보고서)
     */
    @Transactional(readOnly = true)
    public byte[] exportToPdf(String hedgeRelationshipId) {
        List<JournalEntry> entries =
                journalEntryRepository.findByHedgeRelationshipIdOrderByEntryDateAsc(hedgeRelationshipId);

        try {
            return PdfExporter.export(hedgeRelationshipId, entries);
        } catch (Exception e) {
            log.error("PDF 파일 생성 오류: hedgeRelationshipId={}", hedgeRelationshipId, e);
            throw new BusinessException("JE_EXPORT_ERROR", "PDF 파일 생성 중 오류가 발생했습니다.");
        }
    }

    // -----------------------------------------------------------------------
    // 검증 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 공정가치 위험회피 분개 필수 필드 검증.
     *
     * <p>instrumentFvChange, hedgedItemFvChange는 DTO에서 @NotNull을 제거하고
     * 서비스 계층에서 hedgeType 분기 후 검증합니다. CFH 요청에서 이 필드들을
     * 강제로 요구하는 API 계약 혼란을 방지하기 위함입니다.
     *
     * @throws BusinessException JE_002 — FVH 필수 필드 누락
     */
    private void validateFairValueFields(JournalEntryRequest request) {
        if (request.instrumentFvChange() == null) {
            throw new BusinessException("JE_002",
                    "공정가치 위험회피 분개에는 헤지수단 공정가치 변동(instrumentFvChange)이 필수입니다.");
        }
        if (request.hedgedItemFvChange() == null) {
            throw new BusinessException("JE_002",
                    "공정가치 위험회피 분개에는 피헤지항목 공정가치 변동(hedgedItemFvChange)이 필수입니다.");
        }
    }

    /**
     * 현금흐름 위험회피 분개 필수 필드 검증.
     *
     * <p>effectiveAmount, ineffectiveAmount는 DTO에서 @NotNull을 사용하지 않고
     * 서비스 계층에서 hedgeType 분기 후 검증합니다. FVH 요청에서 이 필드들을
     * 강제로 요구하는 API 계약 혼란을 방지하기 위함입니다.
     *
     * <p>비유효 부분이 없는 경우 {@code ineffectiveAmount}를 null이 아닌
     * {@code BigDecimal.ZERO}로 전달해야 합니다.
     *
     * @throws BusinessException JE_002 — CFH 필수 필드 누락
     */
    private void validateCashFlowFields(JournalEntryRequest request) {
        if (request.effectiveAmount() == null) {
            throw new BusinessException("JE_002",
                    "현금흐름 위험회피 분개에는 유효 부분 금액(effectiveAmount)이 필수입니다.");
        }
        if (request.ineffectiveAmount() == null) {
            throw new BusinessException("JE_002",
                    "현금흐름 위험회피 분개에는 비유효 부분 금액(ineffectiveAmount)이 필수입니다.");
        }
    }

    /**
     * OCI 재분류 필수 필드 검증.
     */
    private void validateReclassificationFields(JournalEntryRequest request) {
        if (request.reclassificationAmount() == null) {
            throw new BusinessException("JE_003", "OCI 재분류 시 재분류 금액(reclassificationAmount)은 필수입니다.");
        }
        if (request.reclassificationReason() == null || request.reclassificationReason().isBlank()) {
            throw new BusinessException("JE_003", "OCI 재분류 시 재분류 사유(reclassificationReason)는 필수입니다.");
        }
        if (request.plAccountCode() == null || request.plAccountCode().isBlank()) {
            throw new BusinessException("JE_003", "OCI 재분류 시 대응 P&L 계정(plAccountCode)은 필수입니다.");
        }
        try {
            AccountCode.valueOf(request.plAccountCode());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("JE_003",
                    "유효하지 않은 P&L 계정 코드입니다: " + request.plAccountCode());
        }
        try {
            ReclassificationReason.valueOf(request.reclassificationReason());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("JE_003",
                    "유효하지 않은 재분류 사유입니다: " + request.reclassificationReason());
        }
    }

    /**
     * 분개 목록 무결성 검증.
     * 각 분개의 amount > 0, debitAccount ≠ creditAccount 검증.
     *
     * @throws BusinessException JE_001 — 분개 무결성 위반
     */
    private void validateEntries(List<JournalEntry> entries) {
        for (JournalEntry entry : entries) {
            if (entry.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("JE_001",
                        "분개 금액은 0보다 커야 합니다. 계정=" + entry.getDebitAccount() + "/" + entry.getCreditAccount());
            }
            if (entry.getDebitAccount() == entry.getCreditAccount()) {
                throw new BusinessException("JE_001",
                        "차변 계정과 대변 계정이 동일합니다: " + entry.getDebitAccount());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Excel 셀 생성 헬퍼
    // -----------------------------------------------------------------------

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void createDateCell(Row row, int col, LocalDate date, CellStyle style) {
        Cell cell = row.createCell(col);
        if (date != null) {
            cell.setCellValue(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void createNumericCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    // -----------------------------------------------------------------------
    // Excel 스타일 내부 클래스
    // -----------------------------------------------------------------------

    /**
     * Excel 스타일 모음.
     */
    private static class ExcelStyles {

        final CellStyle titleStyle;
        final CellStyle headerStyle;
        final CellStyle amountCellStyle;
        final CellStyle dateCellStyle;

        ExcelStyles(XSSFWorkbook workbook) {
            titleStyle      = createTitleStyle(workbook);
            headerStyle     = createHeaderStyle(workbook);
            amountCellStyle = createAmountStyle(workbook);
            dateCellStyle   = createDateStyle(workbook);
        }

        /** 타이틀 스타일: 진한 남색 배경, 흰 글자, 굵게, 14pt */
        private CellStyle createTitleStyle(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 13);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x17, (byte)0x37, (byte)0x5E}, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        /** 헤더 스타일: #1F497D 배경, 흰 글자, 굵게, 테두리 */
        private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x1F, (byte)0x49, (byte)0x7D}, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.MEDIUM);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /** 금액 스타일: #,##0.00 포맷, 우측 정렬, 테두리 */
        private CellStyle createAmountStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            style.setDataFormat(format.getFormat("#,##0"));
            style.setAlignment(HorizontalAlignment.RIGHT);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /** 날짜/텍스트 공통 스타일: 가운데 정렬, 테두리 */
        private CellStyle createDateStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /**
         * 분개유형별 배경색 + 테두리 스타일 반환.
         *
         * @param workbook  워크북
         * @param entryType 분개 유형
         * @param isAmount  금액 셀이면 true (우측 정렬 + 숫자 포맷 적용)
         */
        CellStyle getEntryTypeStyle(XSSFWorkbook workbook, JournalEntryType entryType, boolean isAmount) {
            XSSFCellStyle style = workbook.createCellStyle();

            XSSFColor bgColor = switch (entryType) {
                case FAIR_VALUE_HEDGE_INSTRUMENT,
                     FAIR_VALUE_HEDGE_ITEM,
                     IRS_FVH_AMORTIZATION       ->
                        new XSSFColor(new byte[]{(byte)0xCC, (byte)0xE5, (byte)0xFF}, null); // 연파랑 (FVH 계열)
                case CASH_FLOW_HEDGE_EFFECTIVE,
                     CASH_FLOW_HEDGE_INEFFECTIVE ->
                        new XSSFColor(new byte[]{(byte)0xCC, (byte)0xFF, (byte)0xCC}, null); // 연초록
                case OCI_RECLASSIFICATION        ->
                        new XSSFColor(new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xCC}, null); // 연노랑
                case HEDGE_DISCONTINUATION,
                     REVERSING_ENTRY             ->
                        new XSSFColor(new byte[]{(byte)0xFF, (byte)0xCC, (byte)0xCC}, null); // 연빨강
            };
            style.setFillForegroundColor(bgColor);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);

            if (isAmount) {
                DataFormat fmt = workbook.createDataFormat();
                style.setDataFormat(fmt.getFormat("#,##0"));
                style.setAlignment(HorizontalAlignment.RIGHT);
            } else {
                style.setAlignment(HorizontalAlignment.LEFT);
            }
            return style;
        }
    }
}
