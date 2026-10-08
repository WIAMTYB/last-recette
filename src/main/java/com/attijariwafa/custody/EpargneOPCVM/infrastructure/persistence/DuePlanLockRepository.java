package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;
import java.util.List;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;


import java.time.LocalDate;
import java.util.Optional;

public interface DuePlanLockRepository {

    Optional<Long> lockNextDuePlanId(LocalDate today);
    List<SubscriptionPlan>lockDuePlansForCashAccount(String clientCashAccount,LocalDate today);
}
