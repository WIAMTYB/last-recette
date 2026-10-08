package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.CashBalancePort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.CashBalance;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceRequest;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.config.ExternalServiceProperties;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.CorrelationIds;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class CashBalanceClient implements CashBalancePort {

    private static final Logger LOGGER = LoggerFactory.getLogger(CashBalanceClient.class);
    private static final String OPERATION = "cash-balance";
    private static final BigDecimal SKIPPED_BALANCE = new BigDecimal("999999999999");
    private static final BigDecimal CENTIMES_PER_DIRHAM = new BigDecimal("100");

    private final ApiTitresHttpApi httpApi;
    private final ExternalServiceProperties properties;
    private final ExternalServiceRateLimiter rateLimiter;
    private final PlanMetrics planMetrics;

    public CashBalanceClient(ApiTitresHttpApi httpApi,
                             ExternalServiceProperties properties,
                             ExternalServiceRateLimiter rateLimiter,
                             PlanMetrics planMetrics) {
        this.httpApi = httpApi;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.planMetrics = planMetrics;
    }

    @Override
    public CashBalance getBalance(String cashAccount) {
        long startedAt = System.nanoTime();
        if (!properties.getApiTitres().isBalanceCheckEnabled()) {
            LOGGER.debug("Cash balance pre-check skipped for {}", cashAccount);
            planMetrics.recordExternalCall(OPERATION, "skipped", elapsedSince(startedAt));
            return new CashBalance(cashAccount, SKIPPED_BALANCE);
        }

        try {
            CashBalance balance = rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, () -> {
                CashBalanceRequest request = new CashBalanceRequest(
                        properties.getApiTitres().getBalanceCaller(),
                        cashAccount,
                        CorrelationIds.currentOrCreate());
                CashBalanceResponse response = httpApi.getCashBalance(request);
                return toCashBalance(cashAccount, response);
            });
            planMetrics.recordExternalCall(OPERATION, "success", elapsedSince(startedAt));
            return balance;
        } catch (ApplicationException ex) {
            planMetrics.recordExternalCall(OPERATION, outcome(ex), elapsedSince(startedAt));
            throw ex;
        } catch (HttpStatusCodeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, "Cash balance check failed", ex);
        }
    }

    private CashBalance toCashBalance(String cashAccount, CashBalanceResponse response) {
        if (response.returnCode() != 0) {
            String message = response.errorMessage() == null || response.errorMessage().isBlank()
                    ? "Cash balance check failed with returnCode=" + response.returnCode()
                    : response.errorMessage();
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, message);
        }
        if (response.soldeDisponible() == null) {
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, "Cash balance response missing soldeDisponible");
        }
        return new CashBalance(cashAccount, toDirhams(response.soldeDisponible()));
    }

    private BigDecimal toDirhams(BigDecimal amountInCentimes) {
        return amountInCentimes.divide(CENTIMES_PER_DIRHAM, 2, RoundingMode.HALF_UP);
    }

    private String outcome(ApplicationException ex) {
        return ex.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED ? "rate_limited" : "failure";
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
