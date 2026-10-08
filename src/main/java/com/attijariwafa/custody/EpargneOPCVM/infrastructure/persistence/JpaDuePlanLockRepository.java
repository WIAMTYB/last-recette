package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.hibernate.LockMode;
import org.hibernate.query.Query;
import org.springframework.stereotype.Repository;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;

@Repository
public class JpaDuePlanLockRepository implements DuePlanLockRepository {

    private static final String DUE_PLAN_ID_QUERY = """
            select p.id
            from SubscriptionPlan p
            where p.status = :status
              and p.nextExecutionDate <= :today
            order by p.nextExecutionDate asc, p.id asc
            """;

    private static final String DUE_PLANS_FOR_CASH_QUERY = """
            select p
            from SubscriptionPlan p
            where p.status = :status
              and p.nextExecutionDate <= :today
              and p.clientCashAccount = :cashAccount
            order by p.id asc
            """;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Long> lockNextDuePlanId(LocalDate today) {
        List<Long> planIds = entityManager.createQuery(DUE_PLAN_ID_QUERY, Long.class)
                .setParameter("status", PlanStatus.ACTIVE)
                .setParameter("today", today)
                .setMaxResults(1)
                .unwrap(Query.class)
                .setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED)
                .getResultList();
        return planIds.stream().findFirst();
    }

    @Override
    public List<SubscriptionPlan> lockDuePlansForCashAccount(String clientCashAccount, LocalDate today) {
        return entityManager.createQuery(DUE_PLANS_FOR_CASH_QUERY, SubscriptionPlan.class)
                .setParameter("status", PlanStatus.ACTIVE)
                .setParameter("today", today)
                .setParameter("cashAccount", clientCashAccount)
                .unwrap(Query.class)
                .setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED)
                .getResultList();
    }

}
