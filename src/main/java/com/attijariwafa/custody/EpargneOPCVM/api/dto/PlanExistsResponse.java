package com.attijariwafa.custody.EpargneOPCVM.api.dto;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;

public record PlanExistsResponse(
        boolean exists,
        String reference,
        PlanStatus status,
        String message
) {
}
