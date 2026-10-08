package com.attijariwafa.custody.EpargneOPCVM.support;

import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationIds {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 120;

    private CorrelationIds() {
    }

    public static String establish(String candidate) {
        String correlationId = normalize(candidate);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        return correlationId;
    }

    public static String currentOrCreate() {
        return establish(MDC.get(MDC_KEY));
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    private static String normalize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String value = candidate.trim();
        if (value.isBlank() || value.length() > MAX_LENGTH) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return null;
            }
        }
        return value;
    }
}
