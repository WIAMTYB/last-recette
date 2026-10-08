package com.attijariwafa.custody.EpargneOPCVM.api;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.*;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanManagementService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanQueryService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanSearchCriteria;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanManagementService planManagementService;
    private final PlanQueryService planQueryService;
    private final PlanApiMapper mapper;

    public PlanController(PlanManagementService planManagementService,
                          PlanQueryService planQueryService,
                          PlanApiMapper mapper) {
        this.planManagementService = planManagementService;
        this.planQueryService = planQueryService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(planManagementService.createPlan(request)));
    }

    @GetMapping("/exists")
    public PlanExistsResponse exists(@RequestParam String secAccount,
                                     @RequestParam String assetCode) {
        return planQueryService.findExistingPlan(secAccount, assetCode);
    }

    @GetMapping("/stats")
    public PlanStatsResponse stats() {
        PlanQueryService.PlanStats stats = planQueryService.stats();
        return new PlanStatsResponse(stats.byStatus(), stats.dueToday(), stats.inRetry());
    }

    @GetMapping("/{reference}")
    public PlanResponse get(@PathVariable String reference) {
        return mapper.toResponse(planQueryService.getPlan(reference));
    }

    /**
     * {@code secAccount} and {@code status} are the original filters kept for the
     * client app; {@code q}, {@code dueToday}, {@code inRetry} and the sort parameters
     * serve the back-office search.
     */
    @GetMapping
    public PageResponse<PlanResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String secAccount,
                                           @RequestParam(required = false) PlanStatus status,
                                           @RequestParam(defaultValue = "false") boolean dueToday,
                                           @RequestParam(defaultValue = "false") boolean inRetry,
                                           @RequestParam(defaultValue = PlanSearchCriteria.DEFAULT_SORT) String sort,
                                           @RequestParam(defaultValue = "asc") String direction,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        PlanSearchCriteria criteria = new PlanSearchCriteria(
                q, secAccount, status, dueToday, inRetry,
                sort, PlanSearchCriteria.resolveDirection(direction), page, size);
        return mapper.toPageResponse(planQueryService.listPlans(criteria));
    }

    @PatchMapping("/{reference}")
    public PlanResponse update(@PathVariable String reference, @Valid @RequestBody UpdatePlanRequest request) {
        return mapper.toResponse(planManagementService.updatePlan(reference, request));
    }

    @PostMapping("/{reference}/pause")
    public PlanResponse pause(@PathVariable String reference) {
        return mapper.toResponse(planManagementService.pausePlan(reference));
    }

    @PostMapping("/{reference}/suspend")
    public PlanResponse suspend(@PathVariable String reference) {
        return mapper.toResponse(planManagementService.suspendPlan(reference));
    }

    @PostMapping("/{reference}/resume")
    public PlanResponse resume(@PathVariable String reference) {
        return mapper.toResponse(planManagementService.resumePlan(reference));
    }

    @PostMapping("/{reference}/cancel")
    public PlanResponse cancel(@PathVariable String reference) {
        return mapper.toResponse(planManagementService.cancelPlan(reference));
    }

    @GetMapping("/{reference}/executions")
    public List<PlanExecutionResponse> executions(@PathVariable String reference) {
        return mapper.toExecutionResponses(planQueryService.listExecutions(reference));
    }

    @GetMapping("/{reference}/history")
    public List<PlanHistoryItemResponse> history (@PathVariable String reference) {
        return mapper.toHistoryResponses(planQueryService.listHistory(reference));
    }
}
