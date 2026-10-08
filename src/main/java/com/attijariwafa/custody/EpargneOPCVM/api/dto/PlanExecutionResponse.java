package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.ExecutionResultStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PlanExecutionResponse(
        LocalDateTime executedAt,
        LocalDate cycleDueDate,
        int attemptNumber,
        ExecutionResultStatus resultStatus,
        String executionKey,
        String externalOrderReference,
        String message
) {
}
