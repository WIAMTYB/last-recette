package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

@Schema(description = "Partial plan update: omitted or null fields leave existing values unchanged. "
        + "Changing byAmount requires the new mode's positive recurring value and clears the old value; "
        + "supplying a value incompatible with the resulting mode is rejected. "
        + "Only actual frequency or preferredDayOfMonth changes reschedule a future cycle. "
        + "A cycle due today or earlier keeps its due date, next execution and retry deadline; "
        + "the updated schedule applies when that cycle advances. Status is unchanged.")
public record UpdatePlanRequest(
        @Pattern(regexp = "[A-Za-z0-9]{10,30}") String clientCashAccount,
        @Pattern(regexp = "[A-Za-z0-9]{5,20}") String assetCode,
        Boolean byAmount,
        BigDecimal recurringAmount,
        BigDecimal recurringQuantity,
        PlanFrequency frequency,
        @Min(1) @Max(31) Integer preferredDayOfMonth
) {
}
