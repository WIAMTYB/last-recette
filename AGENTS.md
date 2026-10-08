# AGENTS.md

## Purpose

This repository is a single Spring Boot service for `epargne programmee`.

Its scope is intentionally narrow:

- manage recurring OPCVM subscription plans
- execute due plans at end of day
- stay safe when deployed on OpenShift with up to 3 pods
- integrate with api-titres for:
  - sec account / cash account relationship lookup
  - OPCVM catalog lookup
  - SR subscription order booking
  - cash balance pre-check is optional until api-titres exposes a balance endpoint

The expected business flow is:

1. A client creates a plan: every `N` months buy `X` amount or quantity of OPCVM `Y`.
2. At end of day, the scheduler scans `ACTIVE` plans where `nextExecutionDate <= today`.
3. For each due plan, the app checks available cash balance.
4. If funds are available, the app creates the buy order.
5. If funds are missing, the app retries on the next business day.
6. After 5 business days from the cycle due date, the plan moves to `SUSPENDED`.

## Current Architecture

- Oracle
- recurring-plan only
- Spring scheduling only
- REST-client based for all business integrations
- manual-schema only

## Tech Snapshot

- Java 25
- Spring Boot 4.0.6
- Spring Framework 7.0.7
- Spring Web, Validation, Security, Actuator
- Spring Data JPA + JDBC
- Oracle JDBC
- Springdoc / Swagger UI
- Resilience4j rate limiter for outbound service throttling
- HikariCP via Spring Boot datasource configuration
- Jib for container image creation

## Package Map

- `src/main/java/com/attijariwafa/custody/EpargneOPCVM/api`
  - REST controllers
  - request / response DTOs
  - exception to HTTP mapping
- `.../application`
  - use-case services
  - outbound ports
- `.../domain`
  - minimal recurring-plan model
  - scheduling policy
  - statuses and execution result enums
- `.../infrastructure`
  - Oracle repositories
  - HTTP-interface clients
  - scheduler adapter
  - configuration properties
  - outbound rate limiting
- `.../support`
  - shared application exceptions and error codes

The minimal persisted model is:

- `SubscriptionPlan`
- `PlanExecution`
- `AuditEvent`
- `SchedulerRun` (one row per execution batch, cron or manual)

## API Surface

The public API is:

- `POST /api/v1/plans`
- `GET /api/v1/plans/{reference}`
- `GET /api/v1/plans` (`secAccount`, `status`, `q`, `dueToday`, `inRetry`, `sort`, `direction`, `page`, `size`)
- `GET /api/v1/plans/stats`
- `PATCH /api/v1/plans/{reference}`
- `POST /api/v1/plans/{reference}/pause`
- `POST /api/v1/plans/{reference}/resume`
- `POST /api/v1/plans/{reference}/cancel`
- `GET /api/v1/plans/{reference}/executions`
- `POST /api/v1/plans/execute-due` (`wait=false` for a background run answered with 202)
- `GET /api/v1/plans/scheduler-runs`, `/scheduler-runs/status`, `/scheduler-runs/{id}`

Swagger endpoints:

- `/v3/api-docs`
- `/swagger-ui/index.html`

## Scheduling And Locking

Scheduler behavior is simple on purpose:

- every pod may trigger the Spring `@Scheduled` job
- there is no single leader pod
- coordination happens in Oracle at plan-row level

Duplicate execution is prevented by the combination of:

- due-plan selection with `FOR UPDATE SKIP LOCKED`
- one claimed plan processed in one real `REQUIRES_NEW` transaction
- deterministic order idempotency key: `planReference:cycleDueDate`
- unique success constraint on execution key in Oracle

Important implementation notes:

- there is no ShedLock table or distributed scheduler lease
- there is no persisted claim token anymore
- multi-pod safety currently depends on Oracle row locking plus execution-key idempotency
- expected catalog, balance, and order failures are recorded as execution attempts with the existing retry/cycle-skip policy, allowing the batch to continue; each attempt and plan update commit in the same `REQUIRES_NEW` transaction while holding the plan-row lock, with the deterministic order key and unique success constraint still preventing duplicates

Run history and manual triggers:

- `PlanBatchRunner` wraps every batch (cron and `execute-due`) and records it in `EPARGNE_SCHEDULER_RUN`
- the row is inserted as `RUNNING` in its own transaction before the batch, then closed with counters
- duplicate-execution safety is unchanged: it still comes from the plan-row lock and the execution key
- the runner only refuses to stack runs: a per-pod in-memory flag plus a best-effort check for a `RUNNING` row younger than `plans.scheduler.stale-run-after`; both are advisory, never a correctness guarantee
- a pod that dies mid-batch leaves a `RUNNING` row; it is ignored once stale, there is no restart-time repair (multi-pod safe)

If scheduler behavior changes, document exactly how duplicate execution is prevented.

## Business Rules

These rules are current and intentional:

- statuses are `ACTIVE`, `PAUSED`, `SUSPENDED`, `CANCELLED`
- business day means Monday to Friday only
- persist `currentNominalCycleDate` separately from the executable `currentCycleDueDate`; recurrence advances from the nominal month, including after retries, skips and due-cycle schedule edits; resume anchors the fresh cycle on the resume date before weekend adjustment
- there is no holiday calendar and no market-calendar integration
- retry deadline is 5 business days after the cycle due date
- insufficient funds keeps the plan active until the retry window is exhausted
- after the retry window, the plan becomes `SUSPENDED`
- resuming a paused or suspended plan reactivates it with a fresh executable date
- PATCH treats omitted/null fields as unchanged; mode changes require the new mode's positive value, clear the old value, and reject explicitly supplied incompatible values
- PATCH recalculates future cycle dates only for actual frequency/preferred-day changes; once the cycle is due (including today), preserve its dates, retry window and execution key and use the updated schedule when execution advances it after success or a skip; status is unchanged

## Persistence Rules

- Oracle is the only application database
- Hibernate auto-DDL must stay disabled or `validate`
- schema changes are manual and checked in as SQL
- existing schemas without the nominal cycle column require `03-nominal-cycle-date-upgrade.sql`, a reviewed backfill from lifecycle history and `04-nominal-cycle-date-finalize.sql` with all writers stopped; do not infer ambiguous anchors from adjusted dates or change existing execution keys
- the current schema script is under `src/main/resources/sql/oracle`
- auditability is mandatory for lifecycle and execution attempts
- plan references are allocated atomically with `EPARGNE_PLAN_REFERENCE_SEQ.NEXTVAL`; keep the reference unique constraint and allow sequence gaps
- existing schemas require `02-plan-reference-sequence-upgrade.sql` with all plan writers stopped; replace all count-based application instances before restarting writers

Avoid adding new persistence complexity unless justified:

- no Flyway unless explicitly requested
- no second datasource
- no new “temporary” tables for scheduling if Oracle row locking already solves the need

## External Integrations

All business integrations must stay explicit and typed:

- `AccountsCatalogPort`
- `OpcvmCatalogPort`
- `CashBalancePort`
- `OrderCreationPort`

Current implementation rules:

- use one Spring HTTP interface client (`ApiTitresHttpApi`) behind `RestClient`
- keep request / response models typed for order booking
- authenticate api-titres with `X-Api-Key`
- cash balance pre-check stays disabled by default until api-titres exposes a balance endpoint

Outbound rate limiting:

- each external service has its own local in-memory Resilience4j rate limiter
- `max-calls-per-minute=0` means disabled
- this is not distributed across pods
- total cluster call rate is therefore roughly per-pod limit times pod count

## Configuration

Runtime configuration is environment-driven.

Current local convention:

- `src/main/resources/application.properties` reads from env vars
- `.env` can be imported locally by Spring through `spring.config.import`
- `.env` is gitignored
- `src/main/resources/application.properties` is gitignored
- `src/main/resources/application-example.yml` is the committed config template

Important config groups:

- `spring.datasource.*`
- `spring.datasource.hikari.*`
- `spring.jpa.*`
- `app.security.*`
- `plans.scheduler.*`
- `clients.api-titres.base-url`
- `clients.api-titres.api-key`
- `clients.api-titres.balance-check-enabled`
- `clients.api-titres.max-calls-per-minute`

Never commit real secrets to tracked files.

## Working Rules For Future Agents

### Preserve these invariants

- Oracle is the single source of truth for recurring-plan state
- the scheduler must remain safe for multi-pod deployment
- order creation must remain idempotent from the application point of view
- auditability is mandatory
- the service scope must stay inside the recurring-plan bounded context

### Prefer these change patterns

- small vertical refactors around one use case at a time
- focused application services instead of large god services
- typed outbound adapters instead of implicit integration logic
- tests added or updated with business-rule changes
- configuration externalized through env-backed properties

### Avoid these patterns

- reintroducing PostgreSQL
- reintroducing Batch, JobRunr, or ShedLock for this scheduler
- adding a holiday/calendar abstraction unless explicitly requested
- adding new raw SQL business lookups for recurring-plan logic
- adding `Map<String, Object>` contracts in new code
- hiding business rules inside controllers
- relying on in-memory scheduler state that must survive pod restarts

## Operational Notes

- security is basic-auth based with in-memory users
- swagger is enabled
- container build uses Jib
- stress and load testing are externalized outside this application repository
- Java 25 may emit warnings from Maven toolchain dependencies like Jansi, Guava, and Mockito; those warnings are currently tooling-related, not application failures

## Recommended Near-Term Backlog

1. Add repository tests against a prepared Oracle schema for lock behavior and uniqueness guarantees.
2. Add metrics around scheduler throughput and outbound rate-limit rejections.
3. Decide realistic per-service `max-calls-per-minute` values per environment.
4. Add richer operational documentation for deployment and rollback steps.

## Notes For Contributors

- Keep the existing root package unless a deliberate repo-wide rename is approved.
- Update this file when architecture or operational guidance changes.
- If you add transitional code, mark its removal condition clearly.
