package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * MegaTrade maps {@code clientReference} to {@code linkedMsgRef} with a 16-character limit.
 * Internal idempotency keeps the readable {@code planReference:cycleDueDate} execution key in Oracle.
 */
public final class MegaTradeClientReferencePolicy {

    static final int MAX_LENGTH = 16;
    private static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private MegaTradeClientReferencePolicy() {
    }

    public static String format(String planReference, LocalDate cycleDueDate) {
        String clientReference = planReference + cycleDueDate.format(COMPACT_DATE);
        if (clientReference.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "MegaTrade clientReference exceeds " + MAX_LENGTH + " characters: " + clientReference);
        }
        return clientReference;
    }
}
