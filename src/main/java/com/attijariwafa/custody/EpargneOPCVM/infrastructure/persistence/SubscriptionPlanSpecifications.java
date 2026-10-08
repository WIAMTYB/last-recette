package com.attijariwafa.custody.EpargneOPCVM.infrastructure.persistence;

import com.attijariwafa.custody.EpargneOPCVM.application.service.PlanSearchCriteria;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.SubscriptionPlan;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

public final class SubscriptionPlanSpecifications {

    private SubscriptionPlanSpecifications() {
    }

    /**
     * Blank criteria add no predicate, so the query never binds a null that Oracle would
     * have to type-guess.
     */
    public static Specification<SubscriptionPlan> matching(PlanSearchCriteria criteria, LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.query() != null) {
                String pattern = "%" + criteria.query().toUpperCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        like(cb, root, "reference", pattern),
                        like(cb, root, "clientSecAccount", pattern),
                        like(cb, root, "clientCashAccount", pattern),
                        like(cb, root, "assetCode", pattern)));
            }
            if (criteria.secAccount() != null) {
                predicates.add(like(cb, root, "clientSecAccount",
                        "%" + criteria.secAccount().toUpperCase(Locale.ROOT) + "%"));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.dueToday()) {
                predicates.add(cb.equal(root.get("status"), PlanStatus.ACTIVE));
                predicates.add(cb.lessThanOrEqualTo(root.get("nextExecutionDate"), today));
            }
            if (criteria.inRetry()) {
                // After a success the cycle advances past the attempt date; after a failed
                // attempt with a retry it does not, so this is exactly "failed and retrying".
                predicates.add(cb.equal(root.get("status"), PlanStatus.ACTIVE));
                predicates.add(cb.isNotNull(root.get("lastAttemptDate")));
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastAttemptDate"), root.get("currentCycleDueDate")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate like(CriteriaBuilder cb, Root<SubscriptionPlan> root, String property, String pattern) {
        return cb.like(cb.upper(root.get(property)), pattern);
    }
}
