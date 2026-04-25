package com.hedge.prototype.effectiveness.application;

import com.hedge.prototype.effectiveness.adapter.web.dto.EffectivenessTestRequest;
import com.hedge.prototype.effectiveness.application.port.EffectivenessTestRepository;
import com.hedge.prototype.effectiveness.domain.EffectivenessTest;
import com.hedge.prototype.hedge.application.port.HedgeRelationshipRepository;
import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.effectiveness.domain.EffectivenessTestType;
import com.hedge.prototype.hedge.domain.model.HedgeRelationship;
import com.hedge.prototype.hedge.domain.common.HedgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("EffectivenessTestService 회계유형 무결성 테스트")
class EffectivenessTestServiceTest {

    @Mock
    private EffectivenessTestRepository effectivenessTestRepository;

    @Mock
    private HedgeRelationshipRepository hedgeRelationshipRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private HedgeRelationship hedgeRelationship;

    @Mock
    private EffectivenessTest mockSavedTest;

    private EffectivenessTestService service;

    @BeforeEach
    void setUp() {
        service = new EffectivenessTestService(
                effectivenessTestRepository,
                hedgeRelationshipRepository,
                eventPublisher);
    }

    // -----------------------------------------------------------------------
    // 이상치 차단 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("수단·피헤지항목 당기 변동이 모두 0이면 ET_004 예외 — 저장 전 차단")
    void runTest_rejectsBothZeroChanges() {
        EffectivenessTestRequest request = new EffectivenessTestRequest(
                "HR-2026-001",
                LocalDate.of(2026, 4, 30),
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                HedgeType.FAIR_VALUE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
        );

        given(hedgeRelationshipRepository.findById("HR-2026-001"))
                .willReturn(Optional.of(hedgeRelationship));
        given(hedgeRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);

        // BusinessException.errorCode = "ET_004" (getMessage()는 한글 메시지 반환)
        assertThatThrownBy(() -> service.runTest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ET_004"));

        then(effectivenessTestRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("수단 변동만 0이면 통과 — 피헤지항목 변동 있으면 유효성 계산 진행")
    void runTest_allowsOnlyInstrumentZero() {
        EffectivenessTestRequest request = new EffectivenessTestRequest(
                "HR-2026-001",
                LocalDate.of(2026, 4, 30),
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                HedgeType.FAIR_VALUE,
                BigDecimal.ZERO,                        // 수단 변동 0
                new BigDecimal("-500000"),               // 피헤지항목 변동 있음
                null
        );

        given(hedgeRelationshipRepository.findById("HR-2026-001"))
                .willReturn(Optional.of(hedgeRelationship));
        given(hedgeRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE);
        given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc("HR-2026-001"))
                .willReturn(Optional.empty());
        given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);

        // ET_004 예외 없이 정상 저장 — 수단 0, 피헤지항목 변동 있음
        EffectivenessTest result = service.runTest(request);
        assertThat(result).isNotNull();
        then(effectivenessTestRepository).should().save(any());
    }

    // -----------------------------------------------------------------------
    // hedgeType 불일치 차단 테스트
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("hedgeType 불일치 — ET_003")
    class HedgeTypeMismatchTests {

        @Test
        @DisplayName("요청 hedgeType이 저장된 hedgeType과 다르면 ET_003 예외")
        void runTest_rejectsHedgeTypeMismatch() {
            EffectivenessTestRequest request = new EffectivenessTestRequest(
                    "HR-2026-002",
                    LocalDate.of(2026, 4, 30),
                    EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                    HedgeType.CASH_FLOW,                // 요청: CFH
                    new BigDecimal("390000000"),
                    new BigDecimal("-386000000"),
                    null
            );

            given(hedgeRelationshipRepository.findById("HR-2026-002"))
                    .willReturn(Optional.of(hedgeRelationship));
            given(hedgeRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE); // 저장: FVH

            // ET_003: errorCode 검증 + 메시지에 "mismatch" 포함 확인
            assertThatThrownBy(() -> service.runTest(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getErrorCode()).isEqualTo("ET_003");
                        assertThat(be.getMessage()).contains("mismatch");
                    });

            then(effectivenessTestRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("요청 hedgeType이 저장된 hedgeType과 같으면 정상 진행")
        void runTest_acceptsMatchingHedgeType() {
            EffectivenessTestRequest request = new EffectivenessTestRequest(
                    "HR-2026-002",
                    LocalDate.of(2026, 4, 30),
                    EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                    HedgeType.FAIR_VALUE,               // 요청: FVH
                    new BigDecimal("390000000"),
                    new BigDecimal("-386000000"),
                    null
            );

            given(hedgeRelationshipRepository.findById("HR-2026-002"))
                    .willReturn(Optional.of(hedgeRelationship));
            given(hedgeRelationship.getHedgeType()).willReturn(HedgeType.FAIR_VALUE); // 저장: FVH (일치)
            given(effectivenessTestRepository.findTopByHedgeRelationshipIdOrderByTestDateDesc("HR-2026-002"))
                    .willReturn(Optional.empty());
            given(effectivenessTestRepository.save(any())).willReturn(mockSavedTest);

            EffectivenessTest result = service.runTest(request);
            assertThat(result).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // 위험회피관계 미존재 차단 테스트
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("존재하지 않는 hedgeRelationshipId이면 ET_001 예외")
    void runTest_rejectsUnknownHedgeRelationship() {
        EffectivenessTestRequest request = new EffectivenessTestRequest(
                "HR-UNKNOWN",
                LocalDate.of(2026, 4, 30),
                EffectivenessTestType.DOLLAR_OFFSET_PERIODIC,
                HedgeType.FAIR_VALUE,
                new BigDecimal("390000000"),
                new BigDecimal("-386000000"),
                null
        );

        given(hedgeRelationshipRepository.findById("HR-UNKNOWN"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.runTest(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("ET_001"));

        then(effectivenessTestRepository).should(never()).save(any());
    }
}
