package com.attijariwafa.custody.EpargneOPCVM.api.dto;

import java.util.List;

public record PageResponse<T>(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<T> items
) {
}
