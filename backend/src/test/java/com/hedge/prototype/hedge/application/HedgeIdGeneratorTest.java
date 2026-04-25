package com.hedge.prototype.hedge.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HedgeIdGenerator 고유 식별자 생성")
class HedgeIdGeneratorTest {

    @Test
    @DisplayName("헤지관계 ID 는 연도 prefix 를 유지하면서 반복 호출에도 중복되지 않는다")
    void nextHedgeRelationshipId_isUnique() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            ids.add(HedgeIdGenerator.nextHedgeRelationshipId(LocalDate.of(2026, 4, 23)));
        }

        assertThat(ids).hasSize(100);
        assertThat(ids).allMatch(id -> id.startsWith("HR-2026-"));
    }

    @Test
    @DisplayName("헤지대상항목 ID 도 반복 호출 시 중복되지 않는다")
    void nextHedgedItemId_isUnique() {
        Set<String> ids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            ids.add(HedgeIdGenerator.nextHedgedItemId(LocalDate.of(2026, 4, 23)));
        }

        assertThat(ids).hasSize(100);
        assertThat(ids).allMatch(id -> id.startsWith("HI-2026-"));
    }
}
