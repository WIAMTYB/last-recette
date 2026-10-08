# Epargne programmee

Single Spring Boot service for recurring OPCVM subscription plans.

## Stack

- Java 25 target
- Spring Boot 4.0.6
- Spring Framework 7.0.7
- Oracle

## Scope

The service exposes recurring-plan APIs only:

- `POST /api/v1/plans`
- `GET /api/v1/plans/{reference}`
- `GET /api/v1/plans` (`secAccount`, `status`, plus back-office search `q`, `dueToday`, `inRetry`, `sort`, `direction`, `page`, `size` ≤ 200)
- `GET /api/v1/plans/stats` (counts by status, due today, in retry)
- `PATCH /api/v1/plans/{reference}`
- `POST /api/v1/plans/{reference}/pause`
- `POST /api/v1/plans/{reference}/resume`
- `POST /api/v1/plans/{reference}/cancel`
- `GET /api/v1/plans/{reference}/executions`
- `POST /api/v1/plans/execute-due` (`wait=false` runs in the background and answers 202 with the RUNNING run)
- `GET /api/v1/plans/scheduler-runs` (`trigger`, `outcome`, `page`, `size`)
- `GET /api/v1/plans/scheduler-runs/status` (running run, last run, next cron fire time)
- `GET /api/v1/plans/scheduler-runs/{id}`

Every scheduler batch, cron or manual, is recorded in `EPARGNE_SCHEDULER_RUN` with its
start/end time, duration, batch size and per-outcome counters (succeeded, insufficient
funds, below NAV threshold, rejected, technical failure, retries scheduled, cycles
skipped, deferred on a non-business day) plus the processed references. A row is written
as `RUNNING` before the batch starts; one older than `plans.scheduler.stale-run-after`
(default 1 h) is treated as abandoned and no longer blocks a manual run.

It also runs one end-of-day scheduler that:

1. locks due plans in Oracle with `FOR UPDATE SKIP LOCKED`
2. checks balance
3. creates the order when funds are available
4. retries on the next business day for up to 5 business days
5. moves the plan to `SUSPENDED` when the retry window is exhausted

## Recurrence anchor

`currentNominalCycleDate` stores the cycle's date before weekend adjustment.
Subsequent cycles add the frequency to that nominal month and cap the preferred
day at the target month's end. `currentCycleDueDate` remains the executable
business date used for retry deadlines and order idempotency. For example, monthly
day 31 created on January 1, 2026 executes February 2, March 2, then March 31;
quarterly executes February 2, April 30, then July 31.

Creation and future schedule changes initialize both dates. Due-cycle PATCH and
retries preserve the nominal anchor; success and cycle skips advance it. Resume
starts a fresh nominal cycle on the resume date, then applies weekend adjustment.
Duplicate prevention continues to use Oracle row locking, a transaction per claimed
plan and the existing executable-date order key and success lookup.

## PATCH semantics

Omitted fields and explicit JSON `null` leave existing values unchanged. To switch
to quantity mode, send `{"byAmount": false, "recurringQuantity": 10}`; to switch to
amount mode, send `{"byAmount": true, "recurringAmount": 500}`. A mode change
requires the new mode's positive value and clears the previous mode's stored value.
An explicitly supplied incompatible value is rejected, and the fund's minimum
recurring amount still applies. Changing the asset remains unsupported.

Only an actual change to `frequency` or `preferredDayOfMonth` recalculates a future
cycle using the initial scheduling policy from today. Empty updates, repeated
schedule values, cash-account changes and investment-value changes preserve all
cycle dates. Once `currentCycleDueDate` is today or earlier, schedule changes keep
that cycle's due date, next execution date and retry deadline, including during
retries or after the deadline. The updated frequency and preferred day are used
when execution advances the cycle after success or a skip. PATCH never changes
status; the separate resume operation retains its existing fresh-cycle behavior.

Preserving a due cycle also preserves its `planReference:cycleDueDate` idempotency
key. Execution continues to use Oracle row locking, a transaction per claimed plan,
and the unique success constraint; plan updates retain JPA optimistic versioning.

## Oracle schema

Hibernate DDL must stay disabled or set to `validate`. The schema is manual.

Existing databases without `CURRENT_NOMINAL_CYCLE_DATE` require
`03-nominal-cycle-date-upgrade.sql`, a reviewed backfill from schedule and lifecycle
audit history, then `04-nominal-cycle-date-finalize.sql`. Stop all writers for this
upgrade and deploy the new version to every pod before restarting. The old adjusted
date is ambiguous after rollover, resume or schedule edits, so the migration does
not guess anchors. Keep existing executable dates, retry deadlines and execution
keys unchanged. The final script enforces that every plan has an anchor. These two
scripts are unnecessary for databases created using the updated `01` script.

For a new database, run this script before starting the application:

- [01-recurring-plan-schema.sql](/C:/Hamza/dev/epargne-programmee/src/main/resources/sql/oracle/01-recurring-plan-schema.sql)

For an existing database created before sequence-based references, stop all pods and
other plan writers, then run `src/main/resources/sql/oracle/02-plan-reference-sequence-upgrade.sql`
once as the application schema owner. It starts the sequence above the largest existing
numeric `EP` reference. Deploy the updated application to every pod before restarting
writers; do not mix the old count-based allocator with the sequence allocator.
Do not run the upgrade script on a database created with the updated `01` script.

Databases created before the scheduler run history need
`src/main/resources/sql/oracle/05-scheduler-run.sql` (additive, no writer stop required).

Plan references use `EPARGNE_PLAN_REFERENCE_SEQ.NEXTVAL`, keeping the `EP000001`
format (a minimum of six digits). Allocation is atomic across pods, and the reference
unique constraint remains in place. Gaps are expected after rollbacks or loss of cached
sequence values. The sequence never cycles and is capped at 18 digits to fit the column.

## Configuration

Runtime properties are externalized. A template is available here:

- [application-example.yml](/C:/Hamza/dev/epargne-programmee/src/main/resources/application-example.yml)

Important groups:

- `spring.datasource.*`
- `spring.datasource.hikari.*`
- `spring.jpa.*`
- `app.security.*`
- `plans.scheduler.*`
- `clients.api-titres.*` (single api-titres client for accounts, OPCVM catalog, and order booking)

Console logs are ECS JSON by default for deployed environments. For readable local logs, run with the
`dev` or `local` Spring profile, for example:

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
./mvnw.cmd spring-boot:run
```

## Local build

```powershell
./mvnw.cmd test
```

Stress and load testing are externalized and are not part of this repository's Maven lifecycle.

## Notes

- Business days are Monday to Friday only. There is no holiday calendar.
- Hikari pool sizing and timeouts are configurable in `application.properties` through env vars.
- Outbound rate limiting is local in-memory only, with one Resilience4j limiter per external service.
- Setting `clients.<service>.max-calls-per-minute=0` disables rate limiting for that service.
- External integrations use typed Spring HTTP interface clients.
- Order idempotency is enforced with a deterministic execution key: `planReference:cycleDueDate`.

## Swagger

- OpenAPI JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui/index.html`
