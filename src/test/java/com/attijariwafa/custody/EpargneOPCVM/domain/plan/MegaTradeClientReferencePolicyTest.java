package com.attijariwafa.custody.EpargneOPCVM.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MegaTradeClientReferencePolicyTest {

    @Test
    void shouldFormatCompactReferenceWithinMegaTradeLimit() {
        String clientReference = MegaTradeClientReferencePolicy.format(
                "EP000022",
                LocalDate.of(2026, 9, 2));

        assertThat(clientReference).isEqualTo("EP00002220260902");
        assertThat(clientReference).hasSize(MegaTradeClientReferencePolicy.MAX_LENGTH);
    }
}
