package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import java.time.OffsetDateTime;
import java.util.List;

public record SchedulerRunResponse(
        Long id,
        String trigger,
        SchedulerRunOutcome outcome,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Long durationMs,
        int batchSize,
        int processedCount,
        int successCount,
        int insufficientFundsCount,
        int belowThresholdCount,
        int orderRejectedCount,
        int technicalFailureCount,
        int retriesScheduledCount,
        int cyclesSkippedCount,
        int nonBusinessDayDeferredCount,
        boolean batchExhausted,
        List<String> processedReferences,
        String correlationId,
        String triggeredBy,
        String errorMessage
) {
}
