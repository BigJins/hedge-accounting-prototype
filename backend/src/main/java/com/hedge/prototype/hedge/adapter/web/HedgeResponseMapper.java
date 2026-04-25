package com.hedge.prototype.hedge.adapter.web;

import com.hedge.prototype.hedge.adapter.web.dto.DocumentationSummary;
import com.hedge.prototype.hedge.adapter.web.dto.EligibilityCheckResultResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeRelationshipSummaryResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgedItemResponse;
import com.hedge.prototype.hedge.adapter.web.dto.HedgingInstrumentResponse;
import com.hedge.prototype.hedge.domain.common.EligibilityStatus;
import com.hedge.prototype.hedge.domain.common.InstrumentType;
import com.hedge.prototype.hedge.domain.model.HedgedItem;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.policy.EligibilityCheckResult;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * 헤지 API 응답 조립 전용 매퍼.
 *
 * <p>DTO는 표현만 담당하고, 도메인 객체를 HTTP 응답 형식으로 조립하는 책임은
 * 이 매퍼에 집중합니다.
 */
public final class HedgeResponseMapper {

    private HedgeResponseMapper() {}

    public static HedgeDesignationResponse toSuccess(
            HedgeRelationship hedgeRelationship,
            HedgedItem hedgedItem,
            FxForwardContract instrument,
            EligibilityCheckResult eligibilityCheckResult) {

        DocumentationSummary documentation = buildDocumentation(hedgeRelationship, hedgedItem, instrument);

        return new HedgeDesignationResponse(
                hedgeRelationship.getHedgeRelationshipId(),
                hedgeRelationship.getDesignationDate(),
                hedgeRelationship.getHedgeType(),
                hedgeRelationship.getHedgedRisk(),
                hedgeRelationship.getHedgeRatio(),
                EligibilityStatus.ELIGIBLE.name(),
                EligibilityCheckResultResponse.from(eligibilityCheckResult),
                true,
                documentation,
                HedgedItemResponse.from(hedgedItem),
                HedgingInstrumentResponse.from(instrument),
                List.of()
        );
    }

    public static HedgeDesignationResponse toIneligible(
            EligibilityCheckResult eligibilityCheckResult,
            HedgedItem hedgedItem,
            FxForwardContract instrument) {

        return new HedgeDesignationResponse(
                null,
                null,
                null,
                null,
                null,
                EligibilityStatus.INELIGIBLE.name(),
                EligibilityCheckResultResponse.from(eligibilityCheckResult),
                false,
                null,
                HedgedItemResponse.from(hedgedItem),
                HedgingInstrumentResponse.from(instrument),
                buildErrors(eligibilityCheckResult)
        );
    }

    public static HedgeDesignationResponse toSuccessWithInstrument(
            HedgeRelationship hedgeRelationship,
            HedgedItem hedgedItem,
            InstrumentType instrumentType,
            String instrumentContractId,
            EligibilityCheckResult eligibilityCheckResult) {

        requireNonNull(instrumentType, "위험회피수단 유형은 필수입니다.");
        requireNonNull(instrumentContractId, "위험회피수단 계약 ID는 필수입니다.");

        DocumentationSummary documentation = buildDocumentationForInstrument(
                hedgeRelationship, hedgedItem, instrumentType, instrumentContractId);

        HedgingInstrumentResponse instrumentResponse = new HedgingInstrumentResponse(
                instrumentContractId, null, null, null,
                instrumentType.getKoreanName() + " 계약", null
        );

        return new HedgeDesignationResponse(
                hedgeRelationship.getHedgeRelationshipId(),
                hedgeRelationship.getDesignationDate(),
                hedgeRelationship.getHedgeType(),
                hedgeRelationship.getHedgedRisk(),
                hedgeRelationship.getHedgeRatio(),
                EligibilityStatus.ELIGIBLE.name(),
                EligibilityCheckResultResponse.from(eligibilityCheckResult),
                true,
                documentation,
                HedgedItemResponse.from(hedgedItem),
                instrumentResponse,
                List.of()
        );
    }

    public static HedgeDesignationResponse toIneligibleWithInstrument(
            EligibilityCheckResult eligibilityCheckResult,
            HedgedItem hedgedItem,
            InstrumentType instrumentType,
            String instrumentContractId) {

        HedgingInstrumentResponse instrumentResponse = new HedgingInstrumentResponse(
                instrumentContractId, null, null, null,
                instrumentType != null ? instrumentType.getKoreanName() + " 계약" : "미확인 계약", null
        );

        return new HedgeDesignationResponse(
                null,
                null,
                null,
                null,
                null,
                EligibilityStatus.INELIGIBLE.name(),
                EligibilityCheckResultResponse.from(eligibilityCheckResult),
                false,
                null,
                HedgedItemResponse.from(hedgedItem),
                instrumentResponse,
                buildErrors(eligibilityCheckResult)
        );
    }

    public static HedgeRelationshipSummaryResponse toSummary(HedgeRelationship hr) {
        return new HedgeRelationshipSummaryResponse(
                hr.getHedgeRelationshipId(),
                hr.getHedgeType(),
                hr.getHedgedRisk(),
                hr.getDesignationDate(),
                hr.getHedgePeriodEnd(),
                hr.getHedgeRatio(),
                hr.getStatus(),
                hr.getEligibilityStatus(),
                hr.getFxForwardContractId()
        );
    }

    private static DocumentationSummary buildDocumentationForInstrument(
            HedgeRelationship hr, HedgedItem item,
            InstrumentType instrumentType, String instrumentContractId) {

        String hedgedItemDesc = String.format("%s %s %s (만기 %s)",
                item.getCurrency(), item.getItemType(),
                item.getNotionalAmount().toPlainString(),
                item.getMaturityDate());

        String hedgingInstrumentDesc = String.format("%s (계약ID: %s)",
                instrumentType.getKoreanName(), instrumentContractId);

        String hedgedRiskDesc = String.format("%s 위험 (%s 변동)", hr.getHedgedRisk().name(),
                instrumentType == InstrumentType.IRS ? "금리" : "환율+금리");

        return new DocumentationSummary(
                hedgedItemDesc,
                hedgingInstrumentDesc,
                hedgedRiskDesc,
                hr.getRiskManagementObjective(),
                hr.getHedgeStrategy(),
                "Dollar-offset (B6.4.12)"
        );
    }

    private static DocumentationSummary buildDocumentation(
            HedgeRelationship hr, HedgedItem item, FxForwardContract instrument) {

        String hedgedItemDesc = String.format("%s %s %s (만기 %s)",
                item.getCurrency(), item.getItemType(),
                item.getNotionalAmount().toPlainString(),
                item.getMaturityDate());

        String hedgingInstrumentDesc = String.format("USD/KRW 통화선도 (계약환율: %s원, 만기 %s)",
                instrument.getContractForwardRate().toPlainString(),
                instrument.getMaturityDate());

        String hedgedRiskDesc = String.format("%s 위험 (USD/KRW 환율 변동)", hr.getHedgedRisk().name());

        return new DocumentationSummary(
                hedgedItemDesc,
                hedgingInstrumentDesc,
                hedgedRiskDesc,
                hr.getRiskManagementObjective(),
                hr.getHedgeStrategy(),
                "Dollar-offset (B6.4.12)"
        );
    }

    private static List<HedgeDesignationResponse.ErrorDetail> buildErrors(EligibilityCheckResult result) {
        List<HedgeDesignationResponse.ErrorDetail> errors = new ArrayList<>();

        if (!result.getCondition1EconomicRelationship().isResult()) {
            errors.add(new HedgeDesignationResponse.ErrorDetail(
                    "HD_004",
                    "경제적 관계 적격요건 미충족: " + result.getCondition1EconomicRelationship().getDetails(),
                    "K-IFRS 1109호 6.4.1(3)(가)"
            ));
        }
        if (!result.getCondition2CreditRisk().isResult()) {
            errors.add(new HedgeDesignationResponse.ErrorDetail(
                    "HD_002",
                    "신용위험 적격요건 미충족: " + result.getCondition2CreditRisk().getDetails(),
                    "K-IFRS 1109호 6.4.1(3)(나)"
            ));
        }
        if (!result.getCondition3HedgeRatio().isResult()) {
            errors.add(new HedgeDesignationResponse.ErrorDetail(
                    "HD_003",
                    "헤지비율 적격요건 미충족: " + result.getCondition3HedgeRatio().getDetails(),
                    "K-IFRS 1109호 6.4.1(3)(다)"
            ));
        }
        return List.copyOf(errors);
    }
}
