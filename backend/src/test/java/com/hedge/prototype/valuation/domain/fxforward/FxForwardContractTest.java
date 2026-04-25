package com.hedge.prototype.valuation.domain.fxforward;

import com.hedge.prototype.common.exception.BusinessException;
import com.hedge.prototype.valuation.domain.common.ContractStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * FxForwardContract 팩토리 메서드 및 도메인 로직 단위 테스트.
 *
 * <p>K-IFRS 1109호 6.4.1 요건에 따른 헤지 지정 검증,
 * 6.5.10 기준 헤지 중단 로직을 검증합니다.
 *
 * @see K-IFRS 1109호 6.4.1 (위험회피관계 지정 요건)
 * @see K-IFRS 1109호 6.5.10 (공정가치위험회피 중단)
 */
@DisplayName("FxForwardContract — 팩토리 메서드 및 도메인 로직")
class FxForwardContractTest {

    // 정상 테스트 데이터
    private static final String CONTRACT_ID = "FX-2024-001";
    private static final BigDecimal NOTIONAL_USD = new BigDecimal("10000000");
    private static final BigDecimal CONTRACT_FORWARD_RATE = new BigDecimal("1380.0000");
    private static final LocalDate CONTRACT_DATE = LocalDate.of(2024, 1, 2);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2024, 4, 3);
    private static final LocalDate DESIGNATION_DATE = LocalDate.of(2024, 1, 2);

    // =========================================================================
    // designate() 팩토리 메서드 — 정상 생성
    // =========================================================================

    @Nested
    @DisplayName("designate() — 정상 생성")
    class DesignateSuccess {

        @Test
        @DisplayName("정상 입력 시 ACTIVE 상태의 계약 생성")
        void successfulDesignation_shouldReturnActiveContract() {
            FxForwardContract contract = FxForwardContract.designate(
                    CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                    CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE);

            assertThat(contract.getContractId()).isEqualTo(CONTRACT_ID);
            assertThat(contract.getNotionalAmountUsd())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(NOTIONAL_USD);
            assertThat(contract.getContractForwardRate())
                    .usingComparator(BigDecimal::compareTo)
                    .isEqualTo(CONTRACT_FORWARD_RATE);
            assertThat(contract.getContractDate()).isEqualTo(CONTRACT_DATE);
            assertThat(contract.getMaturityDate()).isEqualTo(MATURITY_DATE);
            assertThat(contract.getHedgeDesignationDate()).isEqualTo(DESIGNATION_DATE);
            assertThat(contract.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        }
    }

    // =========================================================================
    // designate() 팩토리 메서드 — 입력 검증 실패
    // =========================================================================

    @Nested
    @DisplayName("designate() — 입력 검증")
    class DesignateValidation {

        @Test
        @DisplayName("contractId null → NullPointerException")
        void nullContractId_shouldThrow() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            null, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                            CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("명목원금 0 → FX_002 예외")
        void zeroNotional_shouldThrowFX_002() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            CONTRACT_ID, BigDecimal.ZERO, CONTRACT_FORWARD_RATE,
                            CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_002");
        }

        @Test
        @DisplayName("명목원금 음수 → FX_002 예외")
        void negativeNotional_shouldThrowFX_002() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            CONTRACT_ID, new BigDecimal("-1000000"), CONTRACT_FORWARD_RATE,
                            CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_002");
        }

        @Test
        @DisplayName("계약 선물환율 0 → FX_002 예외")
        void zeroForwardRate_shouldThrowFX_002() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            CONTRACT_ID, NOTIONAL_USD, BigDecimal.ZERO,
                            CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_002");
        }

        @Test
        @DisplayName("만기일 = 계약일 → FX_001 예외 (만기일은 계약일 이후여야 함)")
        void maturityDateEqualContractDate_shouldThrowFX_001() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                            CONTRACT_DATE, CONTRACT_DATE, DESIGNATION_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_001");
        }

        @Test
        @DisplayName("만기일 < 계약일 → FX_001 예외")
        void maturityDateBeforeContractDate_shouldThrowFX_001() {
            assertThatThrownBy(() ->
                    FxForwardContract.designate(
                            CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                            CONTRACT_DATE, CONTRACT_DATE.minusDays(1), DESIGNATION_DATE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_001");
        }
    }

    // =========================================================================
    // terminate() / markAsMatured() — 상태 변경
    // =========================================================================

    @Nested
    @DisplayName("상태 변경 — terminate()")
    class Terminate {

        @Test
        @DisplayName("ACTIVE 계약 중단 → TERMINATED")
        void activeContract_shouldBeTerminated() {
            FxForwardContract contract = FxForwardContract.designate(
                    CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                    CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE);

            contract.terminate();

            assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        }

        @Test
        @DisplayName("TERMINATED 계약 중단 시도 → FX_001 예외")
        void terminatedContract_shouldThrowFX_001() {
            FxForwardContract contract = FxForwardContract.designate(
                    CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                    CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE);
            contract.terminate();

            assertThatThrownBy(contract::terminate)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_001");
        }
    }

    @Nested
    @DisplayName("상태 변경 — markAsMatured()")
    class MarkAsMatured {

        @Test
        @DisplayName("ACTIVE 계약 만기 처리 → MATURED")
        void activeContract_shouldBeMatured() {
            FxForwardContract contract = FxForwardContract.designate(
                    CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                    CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE);

            contract.markAsMatured();

            assertThat(contract.getStatus()).isEqualTo(ContractStatus.MATURED);
        }

        @Test
        @DisplayName("MATURED 계약 재처리 시도 → FX_001 예외")
        void maturedContract_shouldThrowFX_001() {
            FxForwardContract contract = FxForwardContract.designate(
                    CONTRACT_ID, NOTIONAL_USD, CONTRACT_FORWARD_RATE,
                    CONTRACT_DATE, MATURITY_DATE, DESIGNATION_DATE);
            contract.markAsMatured();

            assertThatThrownBy(contract::markAsMatured)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo("FX_001");
        }
    }
}
