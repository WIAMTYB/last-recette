package com.attijariwafa.custody.EpargneOPCVM.api;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.CreatePlanRequest;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.PlanExistsResponse;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.application.service.DuePlanBatchResult;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanBatchRunner;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanManagementService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanQueryService;
import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanSearchCriteria;
import com.attijariwafa.custody.EpargneOPCVM.domain.audit.AuditEvent;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanFrequency;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRun;
import com.attijariwafa.custody.EpargneOPCVM.domain.scheduler.SchedulerRunOutcome;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
@ExtendWith(MockitoExtension.class)
class PlanControllerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    @Mock
    private PlanManagementService planManagementService;
    @Mock
    private PlanQueryService planQueryService;
    @Mock
    private PlanBatchRunner planBatchRunner;
    @Mock
    private OpcvmCatalogPort opcvmCatalogPort;
    private MockMvc mockMvc;
    private JsonMapper objectMapper;
    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new PlanController(planManagementService, planQueryService, new PlanApiMapper(opcvmCatalogPort,objectMapper)),
                        new PlanExecutionAdminController(planBatchRunner, new SchedulerRunApiMapper(CLOCK)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
    @Test
    void shouldCreatePlan() throws Exception {
        when(planManagementService.createPlan(any())).thenReturn(samplePlan());
        when(opcvmCatalogPort.listFundsByIsin()).thenReturn(sampleFunds());
        mockMvc.perform(post("/api/v1/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePlanRequest(
                                "0000000001",
                                "1234567890",
                                "MA0000000001",
                                true,
                                new BigDecimal("100"),
                                null,
                                PlanFrequency.MONTHLY,
                                5
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("EP000001"))
                .andExpect(jsonPath("$.assetCode").value("MA0000000001"))
                .andExpect(jsonPath("$.assetName").value("Wafa Oblig Plus"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }
    @Test
    void shouldReturnPlanHistory() throws Exception {
        AuditEvent created = new AuditEvent();
        created.setEventTime(LocalDateTime.of(2026, 10, 1, 18, 5));
        created.setEventType("PLAN_CREATED");
        AuditEvent updated = new AuditEvent();
        updated.setEventTime(LocalDateTime.of(2026, 10, 7, 16, 2));
        updated.setEventType("PLAN_UPDATED");
        updated.setPayload("""
           {"changes":[{"field":"amount","from":"100","to":"200"},{"field":"preferredDayOfMonth","from":"5","to":"10"}]}
           """);
        when(planQueryService.listHistory("EP000001")).thenReturn(List.of(updated, created));
        mockMvc.perform(get("/api/v1/plans/EP000001/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event").value("UPDATED"))
                .andExpect(jsonPath("$[0].message").value("amount 100 → 200; preferredDayOfMonth 5 → 10"))
                .andExpect(jsonPath("$[0].changes[0].field").value("amount"))
                .andExpect(jsonPath("$[0].changes[0].from").value("100"))
                .andExpect(jsonPath("$[0].changes[0].to").value("200"))
                .andExpect(jsonPath("$[1].event").value("CREATED"))
                .andExpect(jsonPath("$[1].message").value("Plan created"))
                .andExpect(jsonPath("$[0].payload").doesNotExist());
    }

    @Test
    void shouldReportExistingPlanForSecAccountAndOpcvm() throws Exception {
        when(planQueryService.findExistingPlan("0000000001", "MA0000000001"))
                .thenReturn(new PlanExistsResponse(true, "EP000001", PlanStatus.ACTIVE,"An active plan already exists for this fund "));
        mockMvc.perform(get("/api/v1/plans/exists")
                        .param("secAccount", "0000000001")
                        .param("assetCode", "MA0000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.reference").value("EP000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.message").value("An active plan already exists for this fund "));

    }
    @Test
    void shouldListPlansWithDefaultCriteria() throws Exception {
        when(planQueryService.listPlans(any(PlanSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(samplePlan()), PageRequest.of(0, 20), 1));
        when(opcvmCatalogPort.listFundsByIsin()).thenReturn(sampleFunds());
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].reference").value("EP000001"))
                .andExpect(jsonPath("$.items[0].assetName").value("Wafa Oblig Plus"));
        ArgumentCaptor<PlanSearchCriteria> criteria = ArgumentCaptor.forClass(PlanSearchCriteria.class);
        verify(planQueryService).listPlans(criteria.capture());
        assertThat(criteria.getValue().query()).isNull();
        assertThat(criteria.getValue().status()).isNull();
        assertThat(criteria.getValue().dueToday()).isFalse();
        assertThat(criteria.getValue().inRetry()).isFalse();
        assertThat(criteria.getValue().sortProperty()).isEqualTo("nextExecutionDate");
        assertThat(criteria.getValue().direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(criteria.getValue().page()).isZero();
        assertThat(criteria.getValue().size()).isEqualTo(20);
    }
    @Test
    void shouldForwardSearchFiltersAndCapPageSize() throws Exception {
        when(planQueryService.listPlans(any(PlanSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));
        mockMvc.perform(get("/api/v1/plans")
                        .param("q", "0000123456")
                        .param("secAccount", "0000")
                        .param("status", "ACTIVE")
                        .param("dueToday", "true")
                        .param("inRetry", "true")
                        .param("sort", "amount")
                        .param("direction", "desc")
                        .param("page", "2")
                        .param("size", "500"))
                .andExpect(status().isOk());
        ArgumentCaptor<PlanSearchCriteria> criteria = ArgumentCaptor.forClass(PlanSearchCriteria.class);
        verify(planQueryService).listPlans(criteria.capture());
        assertThat(criteria.getValue().query()).isEqualTo("0000123456");
        assertThat(criteria.getValue().secAccount()).isEqualTo("0000");
        assertThat(criteria.getValue().status()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(criteria.getValue().dueToday()).isTrue();
        assertThat(criteria.getValue().inRetry()).isTrue();
        assertThat(criteria.getValue().sortProperty()).isEqualTo("nextExecutionDate");
        assertThat(criteria.getValue().direction()).isEqualTo(Sort.Direction.DESC);
        assertThat(criteria.getValue().page()).isEqualTo(2);
        assertThat(criteria.getValue().size()).isEqualTo(PlanSearchCriteria.MAX_PAGE_SIZE);
    }
    @Test
    void shouldExposePlanStatsBeforeReferenceLookup() throws Exception {
        when(planQueryService.stats()).thenReturn(new PlanQueryService.PlanStats(
                Map.of(PlanStatus.ACTIVE, 12L, PlanStatus.PAUSED, 1L, PlanStatus.SUSPENDED, 0L, PlanStatus.CANCELLED, 4L),
                3L, 2L));
        mockMvc.perform(get("/api/v1/plans/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(12))
                .andExpect(jsonPath("$.byStatus.CANCELLED").value(4))
                .andExpect(jsonPath("$.dueToday").value(3))
                .andExpect(jsonPath("$.inRetry").value(2));
        verify(planQueryService, never()).getPlan(any());
    }
    @Test
    void shouldExposeCreationTimestampsWithOffset() throws Exception {
        when(planQueryService.getPlan("EP000001")).thenReturn(samplePlan());
        when(opcvmCatalogPort.listFundsByIsin()).thenReturn(sampleFunds());
        mockMvc.perform(get("/api/v1/plans/EP000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetName").value("Wafa Oblig Plus"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString());
    }
    @Test
    void shouldRunDuePlansSynchronouslyByDefault() throws Exception {
        DuePlanBatchResult result = new DuePlanBatchResult(25);
        SchedulerRun run = sampleRun(SchedulerRunOutcome.SUCCESS);
        when(planBatchRunner.run("manual", null)).thenReturn(new PlanBatchRunner.RunReport(run, result));
        mockMvc.perform(post("/api/v1/plans/execute-due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(0))
                .andExpect(jsonPath("$.references").isArray())
                .andExpect(jsonPath("$.run.id").value(42))
                .andExpect(jsonPath("$.run.outcome").value("SUCCESS"));
    }
    @Test
    void shouldAcceptBackgroundRunWhenNotWaiting() throws Exception {
        when(planBatchRunner.submit("manual", null)).thenReturn(sampleRun(SchedulerRunOutcome.RUNNING));
        mockMvc.perform(post("/api/v1/plans/execute-due").param("wait", "false"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processedCount").value(0))
                .andExpect(jsonPath("$.run.outcome").value("RUNNING"))
                .andExpect(jsonPath("$.run.finishedAt").doesNotExist());
        verify(planBatchRunner, never()).run(any(), any());
    }
    @Test
    void shouldReportConflictWhenARunIsAlreadyInProgress() throws Exception {
        when(planBatchRunner.submit("manual", null))
                .thenThrow(new ApplicationException(ErrorCode.SCHEDULER_RUN_IN_PROGRESS, "Scheduler run 7 is still in progress"));
        mockMvc.perform(post("/api/v1/plans/execute-due").param("wait", "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULER_RUN_IN_PROGRESS"))
                .andExpect(jsonPath("$.message").value("Scheduler run 7 is still in progress"));
    }
    private SubscriptionPlan samplePlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setReference("EP000001");
        plan.setClientSecAccount("0000000001");
        plan.setClientCashAccount("1234567890");
        plan.setAssetCode("MA0000000001");
        plan.setByAmount(true);
        plan.setAmount(new BigDecimal("100"));
        plan.setFrequency(PlanFrequency.MONTHLY);
        plan.setPreferredDayOfMonth(5);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setCurrentCycleDueDate(LocalDate.of(2026, 5, 5));
        plan.setNextExecutionDate(LocalDate.of(2026, 5, 5));
        plan.setRetryDeadlineDate(LocalDate.of(2026, 5, 12));
        plan.setCreatedAt(LocalDateTime.of(2026, 4, 30, 9, 15));
        plan.setUpdatedAt(LocalDateTime.of(2026, 4, 30, 9, 15));
        return plan;
    }
    private static Map<String, FundDetails> sampleFunds() {
        return Map.of(
                "MA0000000001",
                new FundDetails("MA0000000001", true, true, BigDecimal.TEN, null, "Wafa Oblig Plus")
        );
    }
    private static SchedulerRun sampleRun(SchedulerRunOutcome outcome) {
        SchedulerRun run = new SchedulerRun();
        run.setId(42L);
        run.setTrigger(SchedulerRun.TRIGGER_MANUAL);
        run.setOutcome(outcome);
        run.setStartedAt(LocalDateTime.of(2026, 9, 16, 12, 0));
        run.setBatchSize(25);
        run.setCorrelationId("corr-1");
        if (outcome != SchedulerRunOutcome.RUNNING) {
            run.setFinishedAt(LocalDateTime.of(2026, 9, 16, 12, 0, 5));
            run.setDurationMs(5000L);
        }
        return run;
    }


}