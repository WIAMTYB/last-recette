package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.PlanFieldChange;

import java.util.List;

public record PlanUpdatedAudit(List<PlanFieldChange> changes ) {
}
