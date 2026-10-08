package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.OrderCreationPort;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmission;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmissionResult;
import com.attijariwafa.custody.EpargneOPCVM.domain.plan.MegaTradeClientReferencePolicy;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.OrderAckResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrOrderRequest;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.observability.PlanMetrics;
import com.attijariwafa.custody.EpargneOPCVM.support.ApplicationException;
import com.attijariwafa.custody.EpargneOPCVM.support.ErrorCode;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class OrderCreationClient implements OrderCreationPort {

    private static final String OPERATION = "order-book";
    private static final String ORDER_SOURCE = "EPG";
    private static final String SUBSCRIPTION_TYPE = "Subscription";

    private final ApiTitresHttpApi httpApi;
    private final ExternalServiceRateLimiter rateLimiter;
    private final PlanMetrics planMetrics;

    public OrderCreationClient(ApiTitresHttpApi httpApi,
                               ExternalServiceRateLimiter rateLimiter,
                               PlanMetrics planMetrics) {
        this.httpApi = httpApi;
        this.rateLimiter = rateLimiter;
        this.planMetrics = planMetrics;
    }

    @Override
    public OrderSubmissionResult createOrder(OrderSubmission submission) {
        long startedAt = System.nanoTime();
        try {
            OrderSubmissionResult result = rateLimiter.execute(ExternalServiceRateLimiter.API_TITRES_SERVICE, () -> {
                OrderAckResponse response = httpApi.bookSrOrder(toSrOrder(submission));
                return toSubmissionResult(response);
            });
            planMetrics.recordExternalCall(OPERATION, "success", elapsedSince(startedAt));
            return result;
        } catch (ApplicationException ex) {
            planMetrics.recordExternalCall(OPERATION, outcome(ex), elapsedSince(startedAt));
            throw ex;
        } catch (HttpStatusCodeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                return new OrderSubmissionResult(false, null, "Order lock conflict on api-titres");
            }
            return new OrderSubmissionResult(false, null, ex.getMessage());
        } catch (RuntimeException ex) {
            planMetrics.recordExternalCall(OPERATION, "failure", elapsedSince(startedAt));
            throw new ApplicationException(ErrorCode.EXTERNAL_SERVICE_FAILURE, "Order booking failed", ex);
        }
    }

    private SrOrderRequest toSrOrder(OrderSubmission submission) {
        return new SrOrderRequest(
                MegaTradeClientReferencePolicy.format(submission.planReference(), submission.cycleDueDate()),
                submission.secAccount(),
                submission.cashAccount(),
                SUBSCRIPTION_TYPE,
                submission.assetCode(),
                submission.byAmount() ? "true" : "false",
                submission.byAmount() ? submission.amount() : null,
                submission.byAmount() ? null : submission.quantity(),
                ORDER_SOURCE
        );
    }

    private OrderSubmissionResult toSubmissionResult(OrderAckResponse response) {
        boolean accepted = response.returnCode() == 200 && response.mainReference() != null;
        String message = response.errorMessage() == null || response.errorMessage().isBlank()
                ? (accepted ? "accepted" : "rejected")
                : response.errorMessage();
        return new OrderSubmissionResult(accepted, response.mainReference(), message);
    }

    private String outcome(ApplicationException ex) {
        return ex.getErrorCode() == ErrorCode.EXTERNAL_SERVICE_RATE_LIMITED ? "rate_limited" : "failure";
    }

    private Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }
}
