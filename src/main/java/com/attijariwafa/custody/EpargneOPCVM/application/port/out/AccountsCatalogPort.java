package com.attijariwafa.custody.EpargneOPCVM.application.port.out;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.AccountRelationship;

public interface AccountsCatalogPort {

    AccountRelationship findRelationship(String secAccount, String cashAccount);
}
