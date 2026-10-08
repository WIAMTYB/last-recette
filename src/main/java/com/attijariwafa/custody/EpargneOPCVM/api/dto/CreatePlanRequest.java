package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank @Pattern(regexp = "\\d{10}") String clientSecAccount,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{10,30}") String clientCashAccount,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{5,20}") String assetCode,
        boolean byAmount,
        BigDecimal recurringAmount,
        BigDecimal recurringQuantity,
        @NotNull PlanFrequency frequency,
        @Min(1) @Max(31) int preferredDayOfMonth
) {
}
