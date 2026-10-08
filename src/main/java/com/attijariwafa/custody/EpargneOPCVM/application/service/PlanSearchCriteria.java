package com.attijariwafa.custody.EpargneOPCVM.application.service;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Search parameters of the plan list. Sort keys are whitelisted against entity
 * properties so a caller can never sort by an arbitrary column.
 */
public record PlanSearchCriteria(
        String query,
        String secAccount,
        PlanStatus status,
        boolean dueToday,
        boolean inRetry,
        String sortProperty,
        Sort.Direction direction,
        int page,
        int size
) {

    public static final String DEFAULT_SORT = "nextExecutionDate";
    public static final int MAX_PAGE_SIZE = 200;

    private static final Map<String, String> SORT_PROPERTIES = Map.of(
            "nextexecutiondate", "nextExecutionDate",
            "createdat", "createdAt",
            "updatedat", "updatedAt",
            "lastattemptdate", "lastAttemptDate",
            "lastexecutiondate", "lastExecutionDate",
            "currentcycleduedate", "currentCycleDueDate",
            "reference", "reference",
            "status", "status"
    );

    public PlanSearchCriteria {
        query = blankToNull(query);
        secAccount = blankToNull(secAccount);
        sortProperty = resolveSortProperty(sortProperty);
        direction = direction == null ? Sort.Direction.ASC : direction;
        page = Math.max(page, 0);
        size = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
    }

    public static PlanSearchCriteria of(String secAccount, PlanStatus status, int page, int size) {
        return new PlanSearchCriteria(null, secAccount, status, false, false, DEFAULT_SORT, Sort.Direction.ASC, page, size);
    }

    public static String resolveSortProperty(String candidate) {
        if (candidate == null) {
            return DEFAULT_SORT;
        }
        return SORT_PROPERTIES.getOrDefault(candidate.trim().toLowerCase(Locale.ROOT), DEFAULT_SORT);
    }

    public static Sort.Direction resolveDirection(String candidate) {
        return candidate != null && candidate.trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
    }

    public Sort sort() {
        Sort primary = Sort.by(direction, sortProperty);
        return sortProperty.equals("reference") ? primary : primary.and(Sort.by(Sort.Direction.ASC, "reference"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
