package com.attijariwafa.custody.EpargneOPCVM.application.port.out.model;

import java.math.BigDecimal;

public record CashBalance(
        String cashAccount,
        BigDecimal availableAmount
) {
}
