package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import java.util.Map;

final class ApiTitresMapKeys {

    private ApiTitresMapKeys() {
    }

    static String value(Map<String, Object> row, String key) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue() == null ? "" : String.valueOf(entry.getValue()).trim();
            }
        }
        return "";
    }
}
