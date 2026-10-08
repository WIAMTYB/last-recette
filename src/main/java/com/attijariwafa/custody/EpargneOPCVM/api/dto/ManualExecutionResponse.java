package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import java.util.List;

public record ManualExecutionResponse(
        int processedCount,
        List<String> references,
        SchedulerRunResponse run
) {
}
