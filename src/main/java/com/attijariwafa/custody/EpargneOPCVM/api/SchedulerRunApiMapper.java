package com.attijariwafa.custody.EpargneOPCVM.api;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.PageResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.SchedulerRunResponse;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class SchedulerRunApiMapper {

    private final Clock clock;

    public SchedulerRunApiMapper(Clock clock) {
        this.clock = clock;
    }

    public SchedulerRunResponse toResponse(SchedulerRun run) {
        if (run == null) {
            return null;
        }
        return new SchedulerRunResponse(
                run.getId(),
                run.getTrigger(),
                run.getOutcome(),
                toOffset(run.getStartedAt()),
                toOffset(run.getFinishedAt()),
                run.getDurationMs(),
                run.getBatchSize(),
                run.getProcessedCount(),
                run.getSuccessCount(),
                run.getInsufficientFundsCount(),
                run.getBelowThresholdCount(),
                run.getOrderRejectedCount(),
                run.getTechnicalFailureCount(),
                run.getRetriesScheduledCount(),
                run.getCyclesSkippedCount(),
                run.getNonBusinessDayDeferredCount(),
                run.getBatchSize() > 0 && run.getProcessedCount() >= run.getBatchSize(),
                splitReferences(run.getProcessedReferences()),
                run.getCorrelationId(),
                run.getTriggeredBy(),
                run.getErrorMessage()
        );
    }

    public PageResponse<SchedulerRunResponse> toPageResponse(Page<SchedulerRun> page) {
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.stream().map(this::toResponse).toList()
        );
    }

    /** Timestamps are stored in the JVM zone; the API states that zone so clients render them right. */
    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(clock.getZone()).toOffsetDateTime();
    }

    private static List<String> splitReferences(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
