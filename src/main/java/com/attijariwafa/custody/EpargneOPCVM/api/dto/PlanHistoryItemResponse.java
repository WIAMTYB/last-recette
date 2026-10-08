package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PlanHistoryItemResponse(
        LocalDateTime occuredAt,
        String event ,
        String message,
        List < PlanFieldChange> changes

) {
}
