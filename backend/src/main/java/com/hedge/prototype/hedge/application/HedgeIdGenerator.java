package com.hedge.prototype.hedge.application;

import java.time.LocalDate;
import java.util.UUID;

final class HedgeIdGenerator {

    private HedgeIdGenerator() {
    }

    static String nextHedgeRelationshipId(LocalDate date) {
        return buildId("HR", date);
    }

    static String nextHedgedItemId(LocalDate date) {
        return buildId("HI", date);
    }

    private static String buildId(String prefix, LocalDate date) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return prefix + "-" + date.getYear() + "-" + suffix;
    }
}
