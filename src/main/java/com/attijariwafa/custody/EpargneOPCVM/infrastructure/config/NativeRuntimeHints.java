package com.attijariwafa.custody.EpargneOPCVM.infrastructure.config;

import com.attijariwafa.custody.EpargneOPCVM.api.dto.ManualExecutionResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.PlanStatsResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.SchedulerRunResponse;
import com.attijariwafa.custody.EpargneOPCVM.api.dto.SchedulerStatusResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.ApiTitresHttpApi;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceRequest;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.CashBalanceResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.OrderAckResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrFundAssetResponse;
import com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http.SrOrderRequest;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.framework.Advised;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.DecoratingProxy;

/**
 * The {@link ApiTitresHttpApi} proxy is built by hand with HttpServiceProxyFactory in
 * {@link RestClientConfiguration}, so Spring AOT cannot see either the JDK proxy or the
 * Jackson payload types behind it. The scheduler-run response records are registered too:
 * they are only reached through nested records and {@code ResponseEntity} bodies, which
 * is exactly the shape that has silently serialised to empty objects in native images.
 */
class NativeRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.proxies().registerJdkProxy(
                ApiTitresHttpApi.class, SpringProxy.class, Advised.class, DecoratingProxy.class);

        new BindingReflectionHintsRegistrar().registerReflectionHints(
                hints.reflection(),
                SrOrderRequest.class,
                SrFundAssetResponse.class,
                OrderAckResponse.class,
                CashBalanceRequest.class,
                CashBalanceResponse.class,
                SchedulerRunResponse.class,
                SchedulerStatusResponse.class,
                ManualExecutionResponse.class,
                PlanStatsResponse.class);
    }
}
