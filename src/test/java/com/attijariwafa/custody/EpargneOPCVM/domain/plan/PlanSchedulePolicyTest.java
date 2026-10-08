package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PlanSchedulePolicyTest {

    @Test
    void shouldMoveWeekendDateToNextBusinessDay() {
        PlanSchedulePolicy policy = new PlanSchedulePolicy();

        LocalDate next = policy.calculateInitialNominalCycleDate(
                LocalDate.of(2026, 5, 2),
                PlanFrequency.MONTHLY,
                4
        );

        assertThat(next).isEqualTo(LocalDate.of(2026, 5, 4));
    }

    @Test
    void shouldUseTodayWhenPreferredDayIsToday() {
        PlanSchedulePolicy policy = new PlanSchedulePolicy();
        LocalDate next = policy.calculateInitialNominalCycleDate(
                LocalDate.of(2026, 10, 1),
                PlanFrequency.MONTHLY,
                1
        );
        assertThat(next).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-01, MONTHLY, 31, 2026-01-31, 2026-02-02, 2026-02-28, 2026-03-02, 2026-03-31, 2026-03-31",
            "2026-01-01, QUARTERLY, 31, 2026-01-31, 2026-02-02, 2026-04-30, 2026-04-30, 2026-07-31, 2026-07-31",
            "2026-10-01, QUARTERLY, 31, 2026-10-31, 2026-11-02, 2027-01-31, 2027-02-01, 2027-04-30, 2027-04-30",
            "2022-12-01, MONTHLY, 31, 2022-12-31, 2023-01-02, 2023-01-31, 2023-01-31, 2023-02-28, 2023-02-28",
            "2024-01-01, MONTHLY, 31, 2024-01-31, 2024-01-31, 2024-02-29, 2024-02-29, 2024-03-31, 2024-04-01",
            "2026-08-01, MONTHLY, 30, 2026-08-30, 2026-08-31, 2026-09-30, 2026-09-30, 2026-10-30, 2026-10-30"
    })
    void shouldAdvanceFromNominalCycleAcrossMonthAndYearBoundaries(
            LocalDate created, PlanFrequency frequency, int day,
            LocalDate firstNominal, LocalDate firstDue, LocalDate secondNominal, LocalDate secondDue,
            LocalDate thirdNominal, LocalDate thirdDue) {
        PlanSchedulePolicy policy = new PlanSchedulePolicy();

        LocalDate nominal = policy.calculateInitialNominalCycleDate(created, frequency, day);
        assertThat(nominal).isEqualTo(firstNominal);
        assertThat(policy.firstExecutableDate(nominal)).isEqualTo(firstDue);
        nominal = policy.calculateNextNominalCycleDate(nominal, frequency, day);
        assertThat(nominal).isEqualTo(secondNominal);
        assertThat(policy.firstExecutableDate(nominal)).isEqualTo(secondDue);
        nominal = policy.calculateNextNominalCycleDate(nominal, frequency, day);
        assertThat(nominal).isEqualTo(thirdNominal);
        assertThat(policy.firstExecutableDate(nominal)).isEqualTo(thirdDue);
    }

    @Test
    void shouldCalculateRetryDeadlineOnBusinessDaysOnly() {
        PlanSchedulePolicy policy = new PlanSchedulePolicy();

        LocalDate deadline = policy.calculateRetryDeadline(LocalDate.of(2026, 5, 5));

        assertThat(deadline).isEqualTo(LocalDate.of(2026, 5, 12));
    }
}
