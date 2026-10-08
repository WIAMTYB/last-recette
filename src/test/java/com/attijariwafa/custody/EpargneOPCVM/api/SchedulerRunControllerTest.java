package com.attijariwafa.custody.EpargneOPCVM.api;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanManagementService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanQueryService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.SchedulerRunQueryService;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.SchedulerProperties;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
@ExtendWith(MockitoExtension.class)
class SchedulerRunControllerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    @Mock
    private SchedulerRunQueryService schedulerRunQueryService;
    @Mock
    private PlanQueryService planQueryService;
    @Mock
    private PlanManagementService planManagementService;
    @Mock
    private OpcvmCatalogPort opcvmCatalogPort;
    private MockMvc mockMvc;
    @BeforeEach
    void setUp() {
        SchedulerProperties properties = new SchedulerProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SchedulerRunController(schedulerRunQueryService, new SchedulerRunApiMapper(CLOCK), properties),
                        new PlanController(planManagementService, planQueryService, new PlanApiMapper(opcvmCatalogPort)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
    @Test
    void shouldListRunsWithFiltersAndTakePrecedenceOverPlanReference() throws Exception {
        when(schedulerRunQueryService.listRuns("manual", SchedulerRunOutcome.SUCCESS, 1, 10))
                .thenReturn(new PageImpl<>(List.of(sampleRun()), PageRequest.of(1, 10), 11));
        mockMvc.perform(get("/api/v1/plans/scheduler-runs")
                        .param("trigger", "manual")
                        .param("outcome", "SUCCESS")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].trigger").value("manual"))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.items[0].startedAt").value("2026-09-16T11:58:00Z"))
                .andExpect(jsonPath("$.items[0].durationMs").value(8400))
                .andExpect(jsonPath("$.items[0].processedCount").value(25))
                .andExpect(jsonPath("$.items[0].successCount").value(23))
                .andExpect(jsonPath("$.items[0].insufficientFundsCount").value(2))
                .andExpect(jsonPath("$.items[0].batchExhausted").value(true))
                .andExpect(jsonPath("$.items[0].processedReferences[1]").value("EP000002"))
                .andExpect(jsonPath("$.items[0].triggeredBy").value("CARGO"));
        verify(planQueryService, never()).getPlan(anyString());
    }
    @Test
    void shouldExposeSchedulerStatus() throws Exception {
        when(schedulerRunQueryService.currentRun()).thenReturn(Optional.empty());
        when(schedulerRunQueryService.lastFinishedRun()).thenReturn(Optional.of(sampleRun()));
        when(schedulerRunQueryService.nextScheduledAt())
                .thenReturn(ZonedDateTime.parse("2026-09-16T18:00:00+01:00[Africa/Casablanca]"));
        mockMvc.perform(get("/api/v1/plans/scheduler-runs/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").doesNotExist())
                .andExpect(jsonPath("$.lastRun.id").value(42))
                .andExpect(jsonPath("$.nextScheduledAt").value("2026-09-16T18:00:00+01:00"))
                .andExpect(jsonPath("$.cron").value("0 0 18 * * *"))
                .andExpect(jsonPath("$.zone").value("Africa/Casablanca"))
                .andExpect(jsonPath("$.batchSize").value(25));
        verify(planQueryService, never()).getPlan(anyString());
    }
    @Test
    void shouldReturnNotFoundForUnknownRun() throws Exception {
        when(schedulerRunQueryService.getRun(9L))
                .thenThrow(new ApplicationException(ErrorCode.SCHEDULER_RUN_NOT_FOUND, "Scheduler run not found: 9"));
        mockMvc.perform(get("/api/v1/plans/scheduler-runs/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULER_RUN_NOT_FOUND"));
        verify(planQueryService, never()).getPlan(any());
    }
    static SchedulerRun sampleRun() {
        SchedulerRun run = new SchedulerRun();
        run.setId(42L);
        run.setTrigger(SchedulerRun.TRIGGER_MANUAL);
        run.setOutcome(SchedulerRunOutcome.SUCCESS);
        run.setStartedAt(LocalDateTime.of(2026, 9, 16, 11, 58));
        run.setFinishedAt(LocalDateTime.of(2026, 9, 16, 11, 58, 8));
        run.setDurationMs(8400L);
        run.setBatchSize(25);
        run.setProcessedCount(25);
        run.setSuccessCount(23);
        run.setInsufficientFundsCount(2);
        run.setRetriesScheduledCount(2);
        run.setProcessedReferences("EP000001,EP000002");
        run.setCorrelationId("corr-1");
        run.setTriggeredBy("CARGO");
        return run;
    }
}