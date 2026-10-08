package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PlanSchedulePolicy {

    public LocalDate calculateInitialNominalCycleDate(LocalDate today, PlanFrequency frequency, int preferredDayOfMonth) {
        LocalDate candidate = alignDay(today, preferredDayOfMonth);
        if (candidate.isBefore(today)) {
            candidate = alignDay(today.plusMonths(frequency.months()), preferredDayOfMonth);
        }
        return candidate;
    }

    public LocalDate calculateNextNominalCycleDate(LocalDate currentNominalCycleDate, PlanFrequency frequency, int preferredDayOfMonth) {
        return alignDay(currentNominalCycleDate.plusMonths(frequency.months()), preferredDayOfMonth);
    }

    public LocalDate calculateRetryDeadline(LocalDate cycleDueDate) {
        return addBusinessDays(cycleDueDate, 5);
    }

    public LocalDate calculateNextRetryDate(LocalDate today) {
        return nextBusinessDay(today.plusDays(1));
    }

    public LocalDate firstExecutableDate(LocalDate date) {
        return isBusinessDay(date) ? date : nextBusinessDay(date);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public LocalDate nextBusinessDay(LocalDate date) {
        LocalDate candidate = date;
        while (!isBusinessDay(candidate)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    public LocalDate addBusinessDays(LocalDate date, int days) {
        LocalDate candidate = date;
        int remaining = days;
        while (remaining > 0) {
            candidate = candidate.plusDays(1);
            if (isBusinessDay(candidate)) {
                remaining--;
            }
        }
        return candidate;
    }

    private LocalDate alignDay(LocalDate date, int preferredDayOfMonth) {
        return date.withDayOfMonth(Math.min(preferredDayOfMonth, date.lengthOfMonth()));
    }
}
