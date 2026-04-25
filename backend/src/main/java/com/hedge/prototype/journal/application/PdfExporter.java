package com.hedge.prototype.journal.application;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.draw.LineSeparator;
import com.hedge.prototype.journal.domain.AccountCode;
import com.hedge.prototype.journal.domain.JournalEntry;
import com.hedge.prototype.journal.domain.JournalEntryType;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 헤지회계 분개장 PDF 생성기.
 *
 * <p>iText 5를 사용하여 PDF 보고서를 생성합니다.
 * 한글 폰트: 클래스패스의 fonts/NanumGothic.ttf 우선 사용,
 * 없으면 Helvetica fallback (PoC 허용 — 한글 깨질 수 있음).
 *
 * @see K-IFRS 1107호 (헤지회계 공시 보고서)
 */
@Slf4j
final class PdfExporter {

    private static final String FONT_PATH = "/fonts/NanumGothic.ttf";
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

    private PdfExporter() {
        // 유틸리티 클래스 — 인스턴스화 금지
    }

    /**
     * 분개장 PDF 파일 생성.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param entries             분개 목록 (날짜 오름차순)
     * @return PDF 바이트 배열
     * @throws DocumentException PDF 생성 오류
     */
    static byte[] export(String hedgeRelationshipId, List<JournalEntry> entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 40, 30);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont   = resolveFont(18f, Font.BOLD);
            Font sectionFont = resolveFont(13f, Font.BOLD);
            Font normalFont  = resolveFont(9f,  Font.NORMAL);
            Font smallFont   = resolveFont(7f,  Font.NORMAL);

            // 1. 표지
            addCoverPage(document, hedgeRelationshipId, titleFont, normalFont);

            // 2. 분개 테이블
            document.newPage();
            addSectionTitle(document, "분개장 (Journal Entry Ledger)", sectionFont);
            addJournalTable(document, entries, normalFont, smallFont);

            // 3. OCI 재분류 이력
            List<JournalEntry> reclassEntries = entries.stream()
                    .filter(e -> e.getEntryType() == JournalEntryType.OCI_RECLASSIFICATION)
                    .toList();
            if (!reclassEntries.isEmpty()) {
                document.newPage();
                addSectionTitle(document, "OCI 재분류 이력 (OCI Reclassification History)", sectionFont);
                addReclassTable(document, reclassEntries, normalFont, smallFont);
            }

            // 4. 각주
            document.newPage();
            addFootnotes(document, sectionFont, normalFont);

        } finally {
            document.close();
        }

        return out.toByteArray();
    }

    // -----------------------------------------------------------------------
    // 섹션별 생성
    // -----------------------------------------------------------------------

    private static void addCoverPage(Document document, String hedgeRelationshipId,
                                     Font titleFont, Font normalFont) throws DocumentException {
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        Paragraph title = new Paragraph("헤지회계 분개장 보고서", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Hedge Accounting Journal Entry Report", normalFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        document.add(subtitle);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(60);
        infoTable.setHorizontalAlignment(Element.ALIGN_CENTER);
        infoTable.setWidths(new float[]{2f, 3f});

        addInfoRow(infoTable, "위험회피관계 ID", hedgeRelationshipId, normalFont);
        addInfoRow(infoTable, "보고서 생성일시",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                normalFont);
        addInfoRow(infoTable, "적용 기준", "K-IFRS 1109호 (금융상품)", normalFont);
        addInfoRow(infoTable, "공시 기준", "K-IFRS 1107호 (금융상품 공시)", normalFont);

        document.add(infoTable);

        document.add(Chunk.NEWLINE);
        document.add(Chunk.NEWLINE);

        Paragraph note = new Paragraph(
                "* 본 보고서는 K-IFRS 1109호에 따른 위험회피회계 분개 자동생성 시스템에서 생성되었습니다.",
                normalFont);
        note.setAlignment(Element.ALIGN_CENTER);
        document.add(note);
    }

    private static void addInfoRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private static void addSectionTitle(Document document, String title, Font font)
            throws DocumentException {
        Paragraph p = new Paragraph(title, font);
        p.setSpacingAfter(8f);
        document.add(p);

        LineSeparator line = new LineSeparator();
        line.setLineColor(new BaseColor(0x1F, 0x49, 0x7D));
        document.add(new Chunk(line));
        document.add(Chunk.NEWLINE);
    }

    private static void addJournalTable(Document document, List<JournalEntry> entries,
                                        Font normalFont, Font smallFont) throws DocumentException {
        String[] headers = {
                "분개ID", "기준일", "분개유형",
                "차변계정", "차변금액",
                "대변계정", "대변금액", "적요"
        };
        float[] widths = {0.5f, 1.1f, 1.6f, 1.6f, 1.3f, 1.6f, 1.3f, 3.0f};

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(4f);

        // 헤더
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, smallFont));
            cell.setBackgroundColor(new BaseColor(0x1F, 0x49, 0x7D));
            cell.getPhrase().getFont().setColor(BaseColor.WHITE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5f);
            table.addCell(cell);
        }

        // 데이터
        for (JournalEntry entry : entries) {
            BaseColor rowColor = getEntryTypeColor(entry.getEntryType());

            addTableCell(table, entry.getJournalEntryId().toString(), smallFont, rowColor, Element.ALIGN_RIGHT);
            addTableCell(table, entry.getEntryDate().toString(), smallFont, rowColor, Element.ALIGN_CENTER);
            addTableCell(table, entry.getEntryType().getKoreanName(), smallFont, rowColor, Element.ALIGN_CENTER);
            addTableCell(table, entry.getDebitAccount().getKoreanName(), smallFont, rowColor, Element.ALIGN_LEFT);
            addTableCell(table, AMOUNT_FORMAT.format(entry.getAmount()), smallFont, rowColor, Element.ALIGN_RIGHT);
            addTableCell(table, entry.getCreditAccount().getKoreanName(), smallFont, rowColor, Element.ALIGN_LEFT);
            addTableCell(table, AMOUNT_FORMAT.format(entry.getAmount()), smallFont, rowColor, Element.ALIGN_RIGHT);
            addTableCell(table, truncate(entry.getDescription(), 50), smallFont, rowColor, Element.ALIGN_LEFT);
        }

        document.add(table);
    }

    private static void addReclassTable(Document document, List<JournalEntry> entries,
                                        Font normalFont, Font smallFont) throws DocumentException {
        String[] headers = {"재분류일", "원인", "최초OCI인식일", "재분류금액", "대응P&L계정", "헤지관계ID"};
        float[] widths = {1.2f, 2f, 1.2f, 1.5f, 1.5f, 2f};

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, smallFont));
            cell.setBackgroundColor(new BaseColor(0x1F, 0x49, 0x7D));
            cell.getPhrase().getFont().setColor(BaseColor.WHITE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4f);
            table.addCell(cell);
        }

        for (JournalEntry entry : entries) {
            BaseColor rowColor = new BaseColor(0xFF, 0xFF, 0xCC); // 연노랑
            AccountCode plAccount = entry.getDebitAccount() == AccountCode.CFHR_OCI
                    ? entry.getCreditAccount() : entry.getDebitAccount();

            addTableCell(table, entry.getEntryDate().toString(), smallFont, rowColor, Element.ALIGN_CENTER);
            addTableCell(table,
                    entry.getReclassificationReason() != null ? entry.getReclassificationReason().name() : "",
                    smallFont, rowColor, Element.ALIGN_LEFT);
            addTableCell(table,
                    entry.getOriginalOciEntryDate() != null ? entry.getOriginalOciEntryDate().toString() : "-",
                    smallFont, rowColor, Element.ALIGN_CENTER);
            addTableCell(table, AMOUNT_FORMAT.format(entry.getAmount()), smallFont, rowColor, Element.ALIGN_RIGHT);
            addTableCell(table, plAccount.getKoreanName(), smallFont, rowColor, Element.ALIGN_LEFT);
            addTableCell(table, entry.getHedgeRelationshipId(), smallFont, rowColor, Element.ALIGN_LEFT);
        }

        document.add(table);
    }

    private static void addFootnotes(Document document, Font sectionFont, Font normalFont)
            throws DocumentException {
        addSectionTitle(document, "각주 (Footnotes)", sectionFont);

        String[] footnotes = {
                "1. 본 분개장은 K-IFRS 1109호 제6장(위험회피회계)에 따라 자동 생성됩니다.",
                "   - 6.5.8(가): 공정가치 위험회피 — 헤지수단 공정가치 변동을 당기손익으로 인식",
                "   - 6.5.8(나): 공정가치 위험회피 — 피헤지항목 장부금액 조정 및 당기손익 인식",
                "   - 6.5.11(가): 현금흐름 위험회피 — 유효 부분을 기타포괄손익(OCI)으로 인식",
                "   - 6.5.11(나): 현금흐름 위험회피 — 비유효 부분을 즉시 당기손익으로 인식",
                "   - 6.5.11(다): OCI(현금흐름위험회피적립금) → 당기손익 재분류 조정",
                "",
                "2. 공시 기준: K-IFRS 1107호 제23조 ~ 제28조 (위험회피회계 공시)",
                "   - 헤지전략, 위험회피관계의 성격 및 위험, 유효성 평가 방법 공시 의무",
                "",
                "3. 분개 불변성: 생성된 분개는 수정되지 않으며, 취소 시 역분개(Reversing Entry) 패턴 사용",
                "",
                "4. 금액: BigDecimal 기반으로 부동소수점 오차 없이 계산됨"
        };

        for (String line : footnotes) {
            Paragraph p = new Paragraph(line, normalFont);
            p.setSpacingAfter(2f);
            document.add(p);
        }
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    /**
     * 한글 폰트 로드 우선순위:
     * 1. 클래스패스 /fonts/NanumGothic.ttf
     * 2. Windows 맑은 고딕 (C:\Windows\Fonts\malgun.ttf)
     * 3. Helvetica fallback (한글 깨짐 — PoC 허용)
     */
    private static Font resolveFont(float size, int style) {
        // 1. 클래스패스 NanumGothic
        try (InputStream fontStream = PdfExporter.class.getResourceAsStream(FONT_PATH)) {
            if (fontStream != null) {
                byte[] fontBytes = fontStream.readAllBytes();
                BaseFont baseFont = BaseFont.createFont(
                        FONT_PATH.substring(1),
                        BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED,
                        true,
                        fontBytes,
                        null);
                return new Font(baseFont, size, style);
            }
        } catch (Exception e) {
            log.debug("NanumGothic 클래스패스 로드 실패: {}", e.getMessage());
        }

        // 2. Windows 맑은 고딕
        String[] windowsFontPaths = {
                "C:/Windows/Fonts/malgun.ttf",
                "C:/Windows/Fonts/NanumGothic.ttf",
                "C:/Windows/Fonts/gulim.ttc,0"
        };
        for (String path : windowsFontPaths) {
            try {
                java.io.File fontFile = new java.io.File(path.contains(",") ? path.substring(0, path.indexOf(',')) : path);
                if (fontFile.exists()) {
                    BaseFont baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    return new Font(baseFont, size, style);
                }
            } catch (Exception e) {
                log.debug("Windows 폰트 로드 실패 [{}]: {}", path, e.getMessage());
            }
        }

        // 3. Helvetica fallback (한글 깨짐 — PoC 허용)
        log.warn("한글 폰트를 찾을 수 없습니다. Helvetica fallback 사용 (한글 깨짐 가능).");
        return new Font(Font.FontFamily.HELVETICA, size, style);
    }

    private static void addTableCell(PdfPTable table, String text, Font font,
                                     BaseColor bgColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        if (bgColor != null) {
            cell.setBackgroundColor(bgColor);
        }
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(3f);
        table.addCell(cell);
    }

    private static BaseColor getEntryTypeColor(JournalEntryType entryType) {
        return switch (entryType) {
            case FAIR_VALUE_HEDGE_INSTRUMENT,
                 IRS_FVH_AMORTIZATION,
                 FAIR_VALUE_HEDGE_ITEM          -> new BaseColor(0xCC, 0xE5, 0xFF); // 연파랑
            case CASH_FLOW_HEDGE_EFFECTIVE,
                 CASH_FLOW_HEDGE_INEFFECTIVE     -> new BaseColor(0xCC, 0xFF, 0xCC); // 연초록
            case OCI_RECLASSIFICATION            -> new BaseColor(0xFF, 0xFF, 0xCC); // 연노랑
            case HEDGE_DISCONTINUATION,
                 REVERSING_ENTRY                 -> new BaseColor(0xFF, 0xCC, 0xCC); // 연빨강
        };
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
