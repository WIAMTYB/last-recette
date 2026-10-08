package com.attijariwafa.custody.EpargneOPCVM.application.port.out.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OrderSubmission(
        String planReference,
        String executionKey,
        LocalDate cycleDueDate,
        String secAccount,
        String cashAccount,
        String assetCode,
        boolean byAmount,
        BigDecimal amount,
        BigDecimal quantity
) {
}
