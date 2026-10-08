package com.attijariwafa.custody.EpargneOPCVM.application.port.out.model;

public record AccountRelationship(
        String secAccount,
        String cashAccount,
        boolean valid
) {
}
