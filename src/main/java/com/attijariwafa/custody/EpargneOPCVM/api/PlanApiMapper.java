package com.attijariwafa.custody.EpargneOPCVM.api;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.*;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanUpdatedAudit;
import com.attijariwafa.custody.EpargneOPCVM.domain.audit.AuditEvent;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanExecution;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PlanApiMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlanApiMapper.class);
    private final OpcvmCatalogPort opcvmCatalogPort;
    private final JsonMapper jsonMapper ;

    public PlanApiMapper(OpcvmCatalogPort opcvmCatalogPort , JsonMapper jsonMapper) {
        this.opcvmCatalogPort = opcvmCatalogPort;
        this.jsonMapper= jsonMapper;
    }
    public PlanResponse toResponse(SubscriptionPlan plan) {
        return toResponse(plan, fundNames(List.of(plan)));
    }
    public PageResponse<PlanResponse> toPageResponse(Page<SubscriptionPlan> page) {
        Map<String, String> names = fundNames(page.getContent());
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.stream().map(plan -> toResponse(plan, names)).toList()
        );
    }
    public PlanExecutionResponse toResponse(PlanExecution execution) {
        return new PlanExecutionResponse(
                execution.getExecutedAt(),
                execution.getCycleDueDate(),
                execution.getAttemptNumber(),
                execution.getResultStatus(),
                execution.getExecutionKey(),
                execution.getExternalOrderReference(),
                execution.getMessage()
        );
    }
    public List<PlanExecutionResponse> toExecutionResponses(List<PlanExecution> executions) {
        return executions.stream().map(this::toResponse).toList();
    }

    public List<PlanHistoryItemResponse> toHistoryResponses(List<AuditEvent> events) {
        return events.stream().map(this::toHistoryResponse).toList();
    }
    private PlanHistoryItemResponse toHistoryResponse(AuditEvent event) {
        List<PlanFieldChange> changes = parseChanges(event);
        return new PlanHistoryItemResponse(
                event.getEventTime(),
                toClientEvent(event.getEventType()),
                toClientMessage(event.getEventType(), changes),
                changes
        );
    }
    private List<PlanFieldChange> parseChanges(AuditEvent event) {
        if (!"PLAN_UPDATED".equals(event.getEventType()) || event.getPayload() == null || event.getPayload().isBlank()) {
            return List.of();
        }
        try {
            PlanUpdatedAudit audit = jsonMapper.readValue(event.getPayload(), PlanUpdatedAudit.class);
            return audit == null || audit.changes() == null ? List.of() : audit.changes();
        } catch (RuntimeException ex) {
            LOGGER.warn("Unable to parse plan update audit payload for {}", event.getEntityReference(), ex);
            return List.of();
        }
    }
    private String toClientEvent(String eventType) {
        return switch (eventType) {
            case "PLAN_CREATED" -> "CREATED";
            case "PLAN_UPDATED" -> "UPDATED";
            case "PLAN_PAUSED" -> "PAUSED";
            case "PLAN_SUSPENDED" -> "SUSPENDED";
            case "PLAN_RESUMED" -> "RESUMED";
            case "PLAN_CANCELLED" -> "CANCELLED";
            case "PLAN_EXECUTION_SUCCEEDED" -> "EXECUTED";
            case "PLAN_EXECUTION_RETRY_SCHEDULED" -> "INSUFFICIENT_FUNDS";
            case "PLAN_CYCLE_SKIPPED" -> "SKIPPED";
            default -> eventType;
        };
    }
    private String toClientMessage(String eventType, List<PlanFieldChange> changes) {
        if ("PLAN_UPDATED".equals(eventType) && !changes.isEmpty()) {
            return changes.stream()
                    .map(change -> change.field() + " " + nullToEmpty(change.from()) + " → " + nullToEmpty(change.to()))
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("Plan modified");
        }
        return switch (eventType) {
            case "PLAN_CREATED" -> "Plan created";
            case "PLAN_UPDATED" -> "Plan modified";
            case "PLAN_PAUSED" -> "Plan paused";
            case "PLAN_SUSPENDED" -> "Plan suspended";
            case "PLAN_RESUMED" -> "Plan resumed";
            case "PLAN_CANCELLED" -> "Plan cancelled";
            case "PLAN_EXECUTION_SUCCEEDED" -> "Subscription order placed";
            case "PLAN_EXECUTION_RETRY_SCHEDULED" -> "Insufficient funds, retry scheduled";
            case "PLAN_CYCLE_SKIPPED" -> "Cycle skipped";
            default -> eventType;
        };
    }
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }





    private PlanResponse toResponse(SubscriptionPlan plan, Map<String, String> names) {
        return new PlanResponse(
                plan.getReference(),
                plan.getClientSecAccount(),
                plan.getClientCashAccount(),
                plan.getAssetCode(),
                names.get(plan.getAssetCode()),
                plan.isByAmount(),
                plan.getAmount(),
                plan.getQuantity(),
                plan.getFrequency(),
                plan.getPreferredDayOfMonth(),
                plan.getStatus(),
                plan.getCurrentCycleDueDate(),
                plan.getNextExecutionDate(),
                plan.getRetryDeadlineDate(),
                plan.getLastAttemptDate(),
                plan.getLastExecutionDate(),
                toOffset(plan.getCreatedAt()),
                toOffset(plan.getUpdatedAt())
        );
    }
    private Map<String, String> fundNames(Collection<SubscriptionPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, FundDetails> funds = opcvmCatalogPort.listFundsByIsin();
            HashMap<String, String> names = new HashMap<>();
            for (SubscriptionPlan plan : plans) {
                String assetCode = plan.getAssetCode();
                if (assetCode == null || names.containsKey(assetCode)) {
                    continue;
                }
                FundDetails fund = funds.get(assetCode);
                names.put(assetCode, fund == null ? null : fund.name());
            }
            return names;
        } catch (RuntimeException ex) {
            LOGGER.warn("Unable to enrich plan responses with OPCVM names", ex);
            return Map.of();
        }
    }
    /**
     * The entity stamps createdAt/updatedAt with {@code LocalDateTime.now()} in the JVM
     * zone; the API states that zone so clients in another zone render them correctly.
     */
    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}