package com.attijariwafa.custody.EpargneOPCVM.infrastructure.client.http;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange(accept = "application/json")
public interface ApiTitresHttpApi {

    @GetExchange("/secaccount/getbycashaccounts")
    List<Map<String, Object>> getSecAccountsByCashAccounts(@RequestParam("cashaccounts") List<String> cashaccounts);

    @GetExchange("/tradableasset/v2/listforsrtrade")
    List<SrFundAssetResponse> listOpcForSrTrade();

    @PostExchange(value = "/srtrade/book", contentType = "application/json")
    OrderAckResponse bookSrOrder(@RequestBody SrOrderRequest request);

    @PostExchange(value = "/cash/balances", contentType = "application/json")
    CashBalanceResponse getCashBalance(@RequestBody CashBalanceRequest request);
}
