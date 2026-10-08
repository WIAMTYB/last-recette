package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalizedFundThresholdPolicyTest {

    private final DecimalizedFundThresholdPolicy policy = new DecimalizedFundThresholdPolicy();

    @Test
    void shouldComputeMinimumAsVlOverThousandTimesOnePointTwo() {
        BigDecimal nav = new BigDecimal("620000");

        assertThat(policy.minimumInvestmentAmount(nav)).isEqualByComparingTo("744.00");
    }

    @Test
    void shouldAcceptInvestmentEqualToThreshold() {
        BigDecimal nav = new BigDecimal("620000");
        BigDecimal investment = new BigDecimal("744.00");

        assertThat(policy.isInvestmentSufficient(investment, nav)).isTrue();
    }

    @Test
    void shouldAcceptInvestmentAboveThreshold() {
        BigDecimal nav = new BigDecimal("620000");
        BigDecimal investment = new BigDecimal("800");

        assertThat(policy.isInvestmentSufficient(investment, nav)).isTrue();
    }

    @Test
    void shouldRejectInvestmentBelowThreshold() {
        BigDecimal nav = new BigDecimal("620000");
        BigDecimal investment = new BigDecimal("500");

        assertThat(policy.isInvestmentSufficient(investment, nav)).isFalse();
    }

    @Test
    void shouldRejectNullOrZeroInvestment() {
        BigDecimal nav = new BigDecimal("620000");

        assertThat(policy.isInvestmentSufficient(null, nav)).isFalse();
        assertThat(policy.isInvestmentSufficient(BigDecimal.ZERO, nav)).isFalse();
    }
}
