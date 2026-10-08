package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long>,
        JpaSpecificationExecutor<SubscriptionPlan> {

    @Query(value = "SELECT EPARGNE_PLAN_REFERENCE_SEQ.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextReferenceNumber();

    Optional<SubscriptionPlan> findByReference(String reference);



    //
    Optional<SubscriptionPlan> findFirstByClientSecAccountAndAssetCodeAndStatusNot(
            String clientSecAccount,
            String assetCode,
            PlanStatus status
    );

    //
    boolean existsByClientSecAccountAndAssetCodeAndStatusNot(
            String clientSecAccount,
            String assetCode,
            PlanStatus status
    );
    boolean existsByReference(String reference);

    Page<SubscriptionPlan> findByStatusAndClientSecAccountContainingIgnoreCase(
            PlanStatus status,
            String clientSecAccount,
            Pageable pageable
    );

    Page<SubscriptionPlan> findByClientSecAccountContainingIgnoreCase(String clientSecAccount, Pageable pageable);

    Page<SubscriptionPlan> findByStatus(PlanStatus status, Pageable pageable);

    List<SubscriptionPlan> findByStatusNot(PlanStatus status);

    long countByStatus(PlanStatus status);

    long countByStatusAndNextExecutionDateLessThanEqual(PlanStatus status, LocalDate date);

    /**
     * Plans whose last attempt failed and that are waiting for a retry: a success advances
     * the cycle past the attempt date, a retry leaves the cycle where it is.
     */
    @Query("select count(p) from SubscriptionPlan p "
            + "where p.status = :status and p.lastAttemptDate is not null "
            + "and p.lastAttemptDate >= p.currentCycleDueDate")
    long countInRetry(@Param("status") PlanStatus status);
}


