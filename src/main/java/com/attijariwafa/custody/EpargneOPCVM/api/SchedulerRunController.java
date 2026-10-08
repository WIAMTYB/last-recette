package com.attijariwafa.custody.EpargneOPCVM.api;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.PageResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.SchedulerRunResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.SchedulerStatusResponse;
import com.attijariwafa.custody.EpargneOPCVM.application.service.SchedulerRunQueryService;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side of the scheduler run history. Lives under the plans prefix so the same API
 * token authorization covers it; the literal segment wins over {@code /plans/{reference}}.
 */
@RestController
@RequestMapping("/api/v1/plans/scheduler-runs")
@PreAuthorize("hasRole('API_CLIENT')")
public class SchedulerRunController {

    private final SchedulerRunQueryService schedulerRunQueryService;
    private final SchedulerRunApiMapper mapper;
    private final SchedulerProperties schedulerProperties;

    public SchedulerRunController(SchedulerRunQueryService schedulerRunQueryService,
                                  SchedulerRunApiMapper mapper,
                                  SchedulerProperties schedulerProperties) {
        this.schedulerRunQueryService = schedulerRunQueryService;
        this.mapper = mapper;
        this.schedulerProperties = schedulerProperties;
    }

    @GetMapping
    public PageResponse<SchedulerRunResponse> list(@RequestParam(required = false) String trigger,
                                                   @RequestParam(required = false) SchedulerRunOutcome outcome,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return mapper.toPageResponse(schedulerRunQueryService.listRuns(trigger, outcome, page, size));
    }

    @GetMapping("/status")
    public SchedulerStatusResponse status() {
        return new SchedulerStatusResponse(
                schedulerRunQueryService.currentRun().map(mapper::toResponse).orElse(null),
                schedulerRunQueryService.lastFinishedRun().map(mapper::toResponse).orElse(null),
                schedulerRunQueryService.nextScheduledAt().toOffsetDateTime(),
                schedulerProperties.getCron(),
                schedulerProperties.getZone(),
                schedulerProperties.getBatchSize()
        );
    }

    @GetMapping("/{id}")
    public SchedulerRunResponse get(@PathVariable long id) {
        return mapper.toResponse(schedulerRunQueryService.getRun(id));
    }
}
