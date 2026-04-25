package com.hedge.prototype.journal.application.port;

import com.hedge.prototype.journal.domain.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 헤지회계 분개 JPA 리포지토리.
 *
 * <p>K-IFRS 1107호 공시 목적의 이력 조회 및 Excel/PDF 다운로드를 위한
 * 페이징 및 전체 목록 조회 메서드를 제공합니다.
 *
 * @see K-IFRS 1107호 (금융상품 공시 — 분개 이력 조회)
 */
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    /**
     * 위험회피관계별 분개 이력 조회 (최신순, 페이징).
     *
     * <p>K-IFRS 1107호 공시 목적의 헤지관계별 분개 이력을 제공합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @param pageable            페이지네이션 정보
     * @return 분개 목록 (페이지)
     * @see K-IFRS 1107호 (헤지회계 공시)
     */
    Page<JournalEntry> findByHedgeRelationshipIdOrderByEntryDateDescCreatedAtDesc(
            String hedgeRelationshipId, Pageable pageable);

    /**
     * 위험회피관계별 분개 전체 목록 조회 (날짜 오름차순).
     *
     * <p>Excel/PDF 다운로드 시 전체 분개 이력을 시간순으로 출력하기 위해 사용합니다.
     *
     * @param hedgeRelationshipId 위험회피관계 ID
     * @return 분개 목록 (전체, 날짜 오름차순)
     */
    List<JournalEntry> findByHedgeRelationshipIdOrderByEntryDateAsc(String hedgeRelationshipId);

    /**
     * 전체 분개 목록 조회 (최신순, 페이징).
     *
     * @param pageable 페이지네이션 정보
     * @return 전체 분개 목록 (페이지)
     */
    Page<JournalEntry> findAllByOrderByEntryDateDescCreatedAtDesc(Pageable pageable);
}
