package com.hedge.prototype.journal.application;

import com.hedge.prototype.journal.adapter.web.dto.IrsFvhAmortizationRequest;
import com.hedge.prototype.journal.adapter.web.dto.JournalEntryRequest;
import com.hedge.prototype.journal.domain.JournalEntry;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 헤지회계 분개 인바운드 포트 (UseCase 인터페이스).
 *
 * <p>구현체: {@link JournalEntryService}
 *
 * @see K-IFRS 1109호 6.5.8  (공정가치위험회피 분개)
 * @see K-IFRS 1109호 6.5.11 (현금흐름위험회피 분개)
 */
public interface JournalEntryUseCase {

    /**
     * 헤지회계 분개 생성.
     *
     * @param request 분개 생성 요청
     * @return 생성된 분개 목록
     */
    List<JournalEntry> createEntries(@Valid JournalEntryRequest request);

    /**
     * 분개 단건 조회.
     *
     * @param id 분개 ID
     * @return 분개 엔티티
     */
    JournalEntry findById(Long id);

    /**
     * 위험회피관계별 분개 목록 조회.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션
     * @return 분개 페이지
     */
    Page<JournalEntry> findByHedgeRelationshipId(String hedgeRelationshipId, Pageable pageable);

    /**
     * 전체 분개 목록 조회.
     *
     * @param pageable 페이지네이션
     * @return 전체 분개 페이지
     */
    Page<JournalEntry> findAll(Pageable pageable);

    /**
     * IRS FVH 장부금액 조정 상각 분개 생성.
     *
     * <p>K-IFRS 1109호 §6.5.9: 위험회피 중단/만기 후 HEDGED_ITEM_ADJ 잔액을
     * 잔여 만기에 걸쳐 직선법으로 상각합니다.
     *
     * @param request 상각 요청 (hedgeRelationshipId, amortizationDate, cumulativeAdjBalance, remainingPeriods)
     * @return 생성된 상각 분개 (단일)
     * @see K-IFRS 1109호 §6.5.9 (공정가치헤지 중단 후 장부금액 조정 상각)
     */
    JournalEntry createAmortizationEntry(@Valid IrsFvhAmortizationRequest request);

    /**
     * 분개 Excel 다운로드.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return Excel 파일 바이트 배열
     */
    byte[] exportToExcel(String hedgeRelationshipId);

    /**
     * 분개 PDF 다운로드.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return PDF 파일 바이트 배열
     */
    byte[] exportToPdf(String hedgeRelationshipId);
}
