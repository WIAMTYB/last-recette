package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import java.util.Map;

public record PlanStatsResponse(
        Map<PlanStatus, Long> byStatus,
        long dueToday,
        long inRetry
) {
}
