package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OpcvmCatalogPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrFundAssetResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
@Component
public class OpcvmCatalogClient implements OpcvmCatalogPort {
    private static final String OPERATION = "opcvm-catalog";
    private final ApiTitresHttpApi httpApi;
    private final ExternalServiceRateLimiter rateLimiter;
    private final PlanMetrics planMetrics;
    public OpcvmCatalogClient(ApiTitresHttpApi httpApi,
                              ExternalServiceRateLimiter rateLimiter,
                              PlanMetrics planMetrics) {
        this.httpApi = httpApi;
        this.rateLimiter = rateLimiter;
        this.planMetrics = planMetrics;
    }
    @Override
    public FundDetails getFund(String assetCode) {
        FundDetails fund = listFundsByIsin().get(assetCode);
        if (fund == null) {
            throw new ApplicationException(ErrorCode.ASSET_NOT_FOUND, "Fund not found: " + assetCode);
        }
        return fund;
    }
    @Override
    public Map<String, FundDetails> listFundsByIsin() {
        long startedAt = System.nanoTime();
        try {
            Map<String, FundDetails> funds = rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, this::loadFunds);
            planMetrics.recordExternalCall(OPERATION, "success", elapsedSince(startedAt));
            return funds;
        } catch (ApplicationException ex) {
            planMetrics.recordExternalCall(OPERATION, outcome(ex), elapsedSince(startedAt));
            throw ex;
        } catch (RuntimeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, "OPCVM catalog lookup failed", ex);
        }
    }
    private Map<String, FundDetails> loadFunds() {
        List<SrFundAssetResponse> funds = httpApi.listOpcForSrTrade();
        if (funds == null) {
            return Map.of();
        }
        Map<String, FundDetails> byIsin = new LinkedHashMap<>();
        for (SrFundAssetResponse fund : funds) {
            if (fund.isin() == null || fund.isin().isBlank()) {
                continue;
            }
            byIsin.put(fund.isin(), toFundDetails(fund));
        }
        return Collections.unmodifiableMap(byIsin);
    }
    private static FundDetails toFundDetails(SrFundAssetResponse fund) {
        return new FundDetails(
                fund.isin(),
                Boolean.TRUE.equals(fund.active()),
                Boolean.TRUE.equals(fund.acceptedForScheduledSavings()),
                fund.netAssetValue() == null ? BigDecimal.ZERO : fund.netAssetValue(),
                fund.minimumRecurringAmount(),
                fund.description()
        );
    }
    private String outcome(ApplicationException ex) {
        return ex.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED ? "rate_limited" : "failure";
    }
    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}