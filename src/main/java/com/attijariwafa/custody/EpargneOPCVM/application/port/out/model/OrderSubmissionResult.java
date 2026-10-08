package com.attijariwafa.custody.EpargneOPCVM.application.port.out.model;

public record OrderSubmissionResult(
        boolean accepted,
        String orderReference,
        String message
) {
}
