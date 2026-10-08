package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PlanResponse(
        String reference,
        String clientSecAccount,
        String clientCashAccount,
        String assetCode,
        String assetName,
        boolean byAmount,
        BigDecimal recurringAmount,
        BigDecimal recurringQuantity,
        PlanFrequency frequency,
        int preferredDayOfMonth,
        PlanStatus status,
        LocalDate currentCycleDueDate,
        LocalDate nextExecutionDate,
        LocalDate retryDeadlineDate,
        LocalDate lastAttemptDate,
        LocalDate lastExecutionDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
