package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.AccountsCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.AccountRelationship;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class AccountsCatalogClient implements AccountsCatalogPort {

    private static final String OPERATION = "accounts-relationship";

    private final ApiTitresHttpApi httpApi;
    private final ExternalServiceRateLimiter rateLimiter;
    private final PlanMetrics planMetrics;

    public AccountsCatalogClient(ApiTitresHttpApi httpApi,
                                 ExternalServiceRateLimiter rateLimiter,
                                 PlanMetrics planMetrics) {
        this.httpApi = httpApi;
        this.rateLimiter = rateLimiter;
        this.planMetrics = planMetrics;
    }

    @Override
    public AccountRelationship findRelationship(String secAccount, String cashAccount) {
        long startedAt = System.nanoTime();
        try {
            AccountRelationship relationship = rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, () -> {
                List<Map<String, Object>> rows = fetchSecAccountsByCashAccount(cashAccount);
                boolean valid = rows.stream().anyMatch(row ->
                        secAccount.equals(ApiTitresMapKeys.value(row, "secAccountID"))
                                && cashAccount.equals(ApiTitresMapKeys.value(row, "cashAccountID")));
                return new AccountRelationship(secAccount, cashAccount, valid);
            });
            planMetrics.recordExternalCall(OPERATION, "success", elapsedSince(startedAt));
            return relationship;
        } catch (ApplicationException ex) {
            planMetrics.recordExternalCall(OPERATION, outcome(ex), elapsedSince(startedAt));
            throw ex;
        } catch (RuntimeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, "Account relationship lookup failed", ex);
        }
    }

    private String outcome(ApplicationException ex) {
        return ex.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED ? "rate_limited" : "failure";
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private List<Map<String, Object>> fetchSecAccountsByCashAccount(String cashAccount) {
        try {
            List<Map<String, Object>> rows = httpApi.getSecAccountsByCashAccounts(List.of(cashAccount));
            return rows == null ? Collections.emptyList() : rows;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NO_CONTENT) {
                return Collections.emptyList();
            }
            throw ex;
        }
    }
}
