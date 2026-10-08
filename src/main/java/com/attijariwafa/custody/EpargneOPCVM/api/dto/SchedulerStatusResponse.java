package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import java.time.OffsetDateTime;

public record SchedulerStatusResponse(
        SchedulerRunResponse running,
        SchedulerRunResponse lastRun,
        OffsetDateTime nextScheduledAt,
        String cron,
        String zone,
        int batchSize
) {
}
