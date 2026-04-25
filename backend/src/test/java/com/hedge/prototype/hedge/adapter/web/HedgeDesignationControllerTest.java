package com.hedge.prototype.hedge.adapter.web;

import com.hedge.prototype.hedge.domain.common.CreditRating;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import com.hedge.prototype.hedge.domain.common.HedgedItemType;
import com.hedge.prototype.hedge.domain.common.HedgedRisk;
import com.hedge.prototype.hedge.adapter.web.dto.HedgeDesignationRequest;
import com.hedge.prototype.hedge.adapter.web.dto.HedgedItemRequest;
import com.hedge.prototype.valuation.domain.fxforward.FxForwardContract;
import com.hedge.prototype.valuation.application.port.FxForwardContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 헤지 지정 컨트롤러 통합 테스트.
 *
 * <p>데모 시나리오(hedge-designation.md 섹션 8) 기준으로 검증합니다.
 * 박지영 과장 케이스: USD 예금 + 통화선도 매도 (CASH_FLOW, FOREIGN_CURRENCY)
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피회계 적용 조건)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("HedgeDesignationController — 통합 테스트 (데모 시나리오)")
class HedgeDesignationControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private FxForwardContractRepository fxForwardContractRepository;

    private MockMvc mockMvc;

    // 데모 시나리오 고정값
    private static final String CONTRACT_ID = "INS-2026-001";
    private static final LocalDate DESIGNATION_DATE = LocalDate.of(2026, 4, 1);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // 데모용 FxForwardContract 사전 등록 (INS-2026-001)
        if (!fxForwardContractRepository.existsById(CONTRACT_ID)) {
            fxForwardContractRepository.save(
                    FxForwardContract.designate(
                            CONTRACT_ID,
                            new BigDecimal("10000000"),
                            new BigDecimal("1350.0000"),
                            DESIGNATION_DATE,
                            MATURITY_DATE,
                            DESIGNATION_DATE,
                            "가나은행",
                            CreditRating.AA
                    )
            );
        }
    }

    // =========================================================================
    // POST /api/v1/hedge-relationships — 헤지 지정
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/hedge-relationships — 헤지 지정")
    class Designate {

        @Test
        @DisplayName("데모 시나리오 — 3조건 모두 PASS → HTTP 201 + ELIGIBLE")
        void demoScenario_allPass_returns201() throws Exception {
            HedgeDesignationRequest request = buildDemoRequest(
                    CreditRating.A,
                    new BigDecimal("1.00")
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eligibilityStatus").value("ELIGIBLE"))
                    .andExpect(jsonPath("$.hedgeRelationshipId").isNotEmpty())
                    .andExpect(jsonPath("$.eligibilityCheckResult.overallResult").value("PASS"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition1EconomicRelationship.result").value("PASS"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition2CreditRisk.result").value("PASS"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition3HedgeRatio.result").value("PASS"))
                    .andExpect(jsonPath("$.documentationGenerated").value(true))
                    .andExpect(jsonPath("$.documentationSummary.effectivenessAssessmentMethod").value(containsString("Dollar-offset")))
                    .andExpect(jsonPath("$.hedgeType").value("CASH_FLOW"))
                    .andExpect(jsonPath("$.hedgedItem.currency").value("USD"))
                    .andExpect(jsonPath("$.hedgingInstrument.contractId").value(CONTRACT_ID));
        }

        @Test
        @DisplayName("조건 2 실패 (BB등급 비투자등급) → HTTP 422 + INELIGIBLE + HD_002 에러")
        void condition2Fail_bbRating_returns422() throws Exception {
            HedgeDesignationRequest request = buildDemoRequest(
                    CreditRating.BB,
                    new BigDecimal("1.00")
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.eligibilityStatus").value("INELIGIBLE"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.overallResult").value("FAIL"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition2CreditRisk.result").value("FAIL"))
                    .andExpect(jsonPath("$.errors[0].errorCode").value("HD_002"))
                    .andExpect(jsonPath("$.errors[0].kifrsReference").value(containsString("6.4.1(3)(나)")));
        }

        @Test
        @DisplayName("조건 3 WARNING (헤지비율 0.70 — 80% 참고 범위 이탈) → HTTP 201 ELIGIBLE + condition3 PASS(WARNING)")
        void condition3Warning_hedgeRatioBelowReferenceRange_returns201() throws Exception {
            // K-IFRS 1109호 BC6.234: 80~125% 정량 기준 폐지 — 범위 이탈은 자동 FAIL 사유 아님
            // 헤지비율 0.70은 참고 범위(80~125%) 이탈이지만 위험관리 목적이 유지되면 적격
            // → 조건 3 PASS(WARNING), 전체 ELIGIBLE → HTTP 201
            HedgeDesignationRequest request = buildDemoRequest(
                    CreditRating.A,
                    new BigDecimal("0.70")
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())       // BC6.234: 범위 이탈은 FAIL 아님 → 201 Created
                    .andExpect(jsonPath("$.eligibilityStatus").value("ELIGIBLE"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition3HedgeRatio.result").value("PASS"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition3HedgeRatio.details")
                            .value(containsString("WARNING")));  // WARNING 메시지 포함
        }

        @Test
        @DisplayName("조건 3 FAIL (헤지비율 0.05 — 극단적 저비율, 위험관리 목적 부합성 의심) → HTTP 422 + INELIGIBLE")
        void condition3Fail_extremelyLowHedgeRatio_returns422() throws Exception {
            // K-IFRS 1109호 B6.4.9: 이익 극대화 목적 배제 — 10% 미만은 극단적 비율로 FAIL
            HedgeDesignationRequest request = buildDemoRequest(
                    CreditRating.A,
                    new BigDecimal("0.05")
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.eligibilityStatus").value("INELIGIBLE"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition3HedgeRatio.result").value("FAIL"));
        }

        @Test
        @DisplayName("HD_004 — 조건 1 실패(명목금액 커버율 200% 초과) → HTTP 422 + INELIGIBLE + HD_004 에러")
        void condition1Fail_notionalCoverageExceeds200Percent_returns422() throws Exception {
            // 헤지수단: $10M, 헤지대상: $4M → 커버율 250% (상한 200% 초과)
            HedgeDesignationRequest request = new HedgeDesignationRequest(
                    HedgeType.CASH_FLOW,
                    HedgedRisk.FOREIGN_CURRENCY,
                    DESIGNATION_DATE,
                    MATURITY_DATE,
                    new BigDecimal("1.00"),
                    "USD 정기예금 원화 환산 손실 위험 관리",
                    "USD/KRW 통화선도 매도를 통한 헤지",
                    new HedgedItemRequest(
                            HedgedItemType.FX_DEPOSIT,
                            "USD",
                            new BigDecimal("4000000"),   // $4M → 커버율 250%
                            new BigDecimal("5400000000"),
                            MATURITY_DATE,
                            "미국은행",
                            CreditRating.A,
                            null,
                            null,
                            "USD 정기예금 $4,000,000"
                    ),
                    null,        // instrumentType (null → FX_FORWARD 기본값)
                    CONTRACT_ID, // instrumentContractId
                    null         // fxForwardContractId (하위 호환용, instrumentContractId 우선)
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.eligibilityStatus").value("INELIGIBLE"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.overallResult").value("FAIL"))
                    .andExpect(jsonPath("$.eligibilityCheckResult.condition1EconomicRelationship.result").value("FAIL"))
                    .andExpect(jsonPath("$.errors[0].errorCode").value("HD_004"))
                    .andExpect(jsonPath("$.errors[0].kifrsReference").value(containsString("6.4.1(3)(가)")));
        }

        @Test
        @DisplayName("존재하지 않는 contractId → HTTP 400 + HD_001")
        void nonExistentContractId_returns400() throws Exception {
            HedgeDesignationRequest request = buildCustomRequest(
                    "NON-EXIST-999",
                    CreditRating.A,
                    new BigDecimal("1.00")
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("HD_001"));
        }

        @Test
        @DisplayName("헤지기간 종료일이 지정일보다 이전 → HTTP 400 + HD_007")
        void hedgePeriodEndBeforeDesignationDate_returns400() throws Exception {
            HedgeDesignationRequest request = new HedgeDesignationRequest(
                    HedgeType.CASH_FLOW,
                    HedgedRisk.FOREIGN_CURRENCY,
                    DESIGNATION_DATE,
                    DESIGNATION_DATE.minusDays(1),
                    new BigDecimal("1.00"),
                    "환율 변동 위험 관리",
                    "통화선도 매도를 통한 USD 예금 환율 위험 헤지",
                    buildDefaultHedgedItemRequest(CreditRating.A),
                    null,        // instrumentType (null → FX_FORWARD 기본값)
                    CONTRACT_ID, // instrumentContractId
                    null         // fxForwardContractId (하위 호환용)
            );

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("HD_007"));
        }

        @Test
        @DisplayName("필수 필드 누락 (@Valid) → HTTP 400")
        void missingRequiredField_returns400() throws Exception {
            String invalidJson = """
                    {
                        "hedgeType": "CASH_FLOW"
                    }
                    """;

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("프런트 OTHER itemType 입력은 HTTP 400으로 거절한다")
        void unsupportedOtherItemType_returns400() throws Exception {
            String invalidJson = """
                    {
                        "hedgeType": "CASH_FLOW",
                        "hedgedRisk": "FOREIGN_CURRENCY",
                        "designationDate": "2026-04-01",
                        "hedgePeriodEnd": "2026-07-01",
                        "hedgeRatio": 1.00,
                        "riskManagementObjective": "프런트 OTHER 옵션 검증",
                        "hedgeStrategy": "지원하지 않는 피헤지항목 유형 방어",
                        "hedgedItem": {
                            "itemType": "OTHER",
                            "currency": "USD",
                            "notionalAmount": 10000000,
                            "notionalAmountKrw": 13500000000,
                            "maturityDate": "2026-07-01",
                            "counterpartyName": "테스트 거래상대방",
                            "counterpartyCreditRating": "A",
                            "description": "OTHER 입력 방어"
                        },
                        "instrumentContractId": "INS-2026-001"
                    }
                    """;

            mockMvc.perform(post("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // GET /api/v1/hedge-relationships/{id} — 단건 조회
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/hedge-relationships/{id} — 단건 조회")
    class FindById {

        @Test
        @DisplayName("존재하지 않는 ID → HTTP 400 + HD_009")
        void nonExistentId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/hedge-relationships/HR-9999-999")
                            .with(httpBasic("test", "test")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("HD_009"));
        }
    }

    // =========================================================================
    // GET /api/v1/hedge-relationships — 목록 조회
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/hedge-relationships — 목록 조회")
    class FindAll {

        @Test
        @DisplayName("빈 목록 조회 → HTTP 200 + 빈 페이지")
        void emptyList_returns200WithEmptyPage() throws Exception {
            mockMvc.perform(get("/api/v1/hedge-relationships")
                            .with(httpBasic("test", "test")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    // =========================================================================
    // 헬퍼 메서드
    // =========================================================================

 
    // =========================================================================
    // Helper methods
    // =========================================================================

    private HedgeDesignationRequest buildDemoRequest(
            CreditRating creditRating,
            BigDecimal hedgeRatio) {

        return buildCustomRequest(CONTRACT_ID, creditRating, hedgeRatio);
    }

    private HedgeDesignationRequest buildCustomRequest(
            String contractId,
            CreditRating creditRating,
            BigDecimal hedgeRatio) {

        return new HedgeDesignationRequest(
                HedgeType.CASH_FLOW,
                HedgedRisk.FOREIGN_CURRENCY,
                DESIGNATION_DATE,
                MATURITY_DATE,
                hedgeRatio,
                "USD 현금흐름 변동위험을 관리합니다.",
                "USD/KRW 통화선도 매도를 통해 환율변동위험을 헤지합니다.",
                buildDefaultHedgedItemRequest(creditRating),
                null,
                contractId,
                null
        );
    }

    private HedgedItemRequest buildDefaultHedgedItemRequest(CreditRating creditRating) {
        return new HedgedItemRequest(
                HedgedItemType.FX_DEPOSIT,
                "USD",
                new BigDecimal("10000000"),
                new BigDecimal("13500000000"),
                MATURITY_DATE,
                "테스트거래상대방",
                creditRating,
                null,
                null,
                "USD 정기예금 10,000,000"
        );
    }
}
