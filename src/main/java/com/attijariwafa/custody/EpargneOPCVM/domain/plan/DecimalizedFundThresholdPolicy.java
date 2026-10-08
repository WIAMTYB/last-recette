package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DecimalizedFundThresholdPolicy {

    private static final BigDecimal THOUSAND = new BigDecimal("1000");
    private static final BigDecimal FACTOR = new BigDecimal("1.2");

    public BigDecimal minimumInvestmentAmount(BigDecimal netAssetValue) {
        if (netAssetValue == null || netAssetValue.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netAssetValue.divide(THOUSAND, 10, RoundingMode.HALF_UP)
                .multiply(FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isInvestmentSufficient(BigDecimal investmentAmount, BigDecimal netAssetValue) {
        if (investmentAmount == null || investmentAmount.signum() <= 0) {
            return false;
        }
        return investmentAmount.compareTo(minimumInvestmentAmount(netAssetValue)) >= 0;
    }

    public String belowThresholdMessage(BigDecimal investmentAmount, BigDecimal netAssetValue) {
        BigDecimal required = minimumInvestmentAmount(netAssetValue);
        return "Investment amount " + investmentAmount + " is below VL threshold (required: " + required + " MAD)";
    }
}
