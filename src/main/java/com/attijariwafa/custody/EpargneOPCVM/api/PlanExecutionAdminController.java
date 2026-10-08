package com.attijariwafa.custody.EpargneOPCVM.api;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.ManualExecutionResponse;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanBatchRunner;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.security.ApiClientPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanExecutionAdminController {

    private final PlanBatchRunner planBatchRunner;
    private final SchedulerRunApiMapper mapper;

    public PlanExecutionAdminController(PlanBatchRunner planBatchRunner, SchedulerRunApiMapper mapper) {
        this.planBatchRunner = planBatchRunner;
        this.mapper = mapper;
    }

    /**
     * Runs the same batch as the end-of-day cron. With {@code wait=true} (the default,
     * kept for existing callers) the response carries the finished run; with
     * {@code wait=false} the batch runs in the background and the RUNNING run is
     * returned with 202 so callers behind short HTTP timeouts can poll
     * {@code /scheduler-runs/{id}} instead of holding the request open.
     */
    @PostMapping("/execute-due")
    @PreAuthorize("hasRole('API_CLIENT')")
    public ResponseEntity<ManualExecutionResponse> executeDuePlans(
            @RequestParam(defaultValue = "true") boolean wait) {
        String triggeredBy = currentActor();
        if (wait) {
            PlanBatchRunner.RunReport report = planBatchRunner.run(SchedulerRun.TRIGGER_MANUAL, triggeredBy);
            return ResponseEntity.ok(new ManualExecutionResponse(
                    report.result().processedCount(),
                    report.result().references(),
                    mapper.toResponse(report.run())));
        }
        SchedulerRun run = planBatchRunner.submit(SchedulerRun.TRIGGER_MANUAL, triggeredBy);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ManualExecutionResponse(0, List.of(), mapper.toResponse(run)));
    }

    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof ApiClientPrincipal principal && principal.clientCode() != null) {
            return principal.clientCode();
        }
        return authentication.getName();
    }
}
