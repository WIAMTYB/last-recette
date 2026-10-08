package com.attijariwafa.custody.EpargneOPCVM.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.attijariwafa.custody.EpargneOPCVM.domain.plan.PlanStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PlanSearchCriteriaTest {

    @Test
    void shouldWhitelistSortPropertyAndFallBackToDefault() {
        assertThat(PlanSearchCriteria.resolveSortProperty("createdAt")).isEqualTo("createdAt");
        assertThat(PlanSearchCriteria.resolveSortProperty("CREATEDAT")).isEqualTo("createdAt");
        assertThat(PlanSearchCriteria.resolveSortProperty("amount")).isEqualTo("nextExecutionDate");
        assertThat(PlanSearchCriteria.resolveSortProperty("id; drop table")).isEqualTo("nextExecutionDate");
        assertThat(PlanSearchCriteria.resolveSortProperty(null)).isEqualTo("nextExecutionDate");
    }

    @Test
    void shouldNormaliseBlankValuesAndClampPaging() {
        PlanSearchCriteria criteria = new PlanSearchCriteria(
                "  ", " 0000123456 ", PlanStatus.ACTIVE, true, false, "reference", null, -3, 5000);

        assertThat(criteria.query()).isNull();
        assertThat(criteria.secAccount()).isEqualTo("0000123456");
        assertThat(criteria.direction()).isEqualTo(Sort.Direction.ASC);
        assertThat(criteria.page()).isZero();
        assertThat(criteria.size()).isEqualTo(PlanSearchCriteria.MAX_PAGE_SIZE);
        assertThat(criteria.sort()).isEqualTo(Sort.by(Sort.Direction.ASC, "reference"));
    }

    @Test
    void shouldTieBreakOnReference() {
        PlanSearchCriteria criteria = new PlanSearchCriteria(
                "EP", null, null, false, false, "createdAt", Sort.Direction.DESC, 0, 0);

        assertThat(criteria.size()).isEqualTo(20);
        assertThat(criteria.sort()).isEqualTo(
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "reference")));
        assertThat(PlanSearchCriteria.resolveDirection("DESC")).isEqualTo(Sort.Direction.DESC);
        assertThat(PlanSearchCriteria.resolveDirection("sideways")).isEqualTo(Sort.Direction.ASC);
    }
}
