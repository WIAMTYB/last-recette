package com.attijariwafa.custody.EpargneOPCVM.application.port.out;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.CashBalance;

public interface CashBalancePort {

    CashBalance getBalance(String cashAccount);
}
