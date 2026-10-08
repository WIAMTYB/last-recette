package com.attijariwafa.custody.EpargneOPCVM.application.port.out;

import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmission;
import com.attijariwafa.custody.EpargneOPCVM.application.port.out.model.OrderSubmissionResult;

public interface OrderCreationPort {

    OrderSubmissionResult createOrder(OrderSubmission submission);
}
