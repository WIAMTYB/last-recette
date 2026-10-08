package com.attijariwafa.custody.EpargneOPCVM.application.port.out;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.FundDetails;

import java.util.Map;

public interface OpcvmCatalogPort {

    FundDetails getFund(String assetCode);
    Map<String,FundDetails> listFundsByIsin();

}