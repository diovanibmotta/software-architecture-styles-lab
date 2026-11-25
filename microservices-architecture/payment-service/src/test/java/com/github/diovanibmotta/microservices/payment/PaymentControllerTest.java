package com.github.diovanibmotta.microservices.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "payment.limit-cents=100000")
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired PaymentRepository payments;

    private void authorize(long orderId, long amount, int expectedHttp, String expectedStatus) throws Exception {
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"amountCents\":" + amount + "}"))
                .andExpect(status().is(expectedHttp))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void authorizesUpToTheLimitAndDeclinesAbove() throws Exception {
        authorize(1001, 100_000, 201, "AUTHORIZED");
        authorize(1002, 100_001, 201, "DECLINED");
    }

    @Test
    void authorizeIsIdempotentPerOrder() throws Exception {
        authorize(2001, 5_000, 201, "AUTHORIZED");
        authorize(2001, 5_000, 200, "AUTHORIZED");   // retry: same payment, no second charge
        assertThat(payments.findAll()).filteredOn(p -> p.orderId() == 2001).hasSize(1);
    }

    @Test
    void refundCompensatesAndIsIdempotent() throws Exception {
        authorize(3001, 5_000, 201, "AUTHORIZED");
        long id = payments.findByOrderId(3001).orElseThrow().id();

        mvc.perform(post("/payments/{id}/refund", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(post("/payments/{id}/refund", id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        mvc.perform(get("/payments/{id}", id)).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void declinedPaymentCannotBeRefunded() throws Exception {
        authorize(4001, 999_999, 201, "DECLINED");
        long id = payments.findByOrderId(4001).orElseThrow().id();
        mvc.perform(post("/payments/{id}/refund", id)).andExpect(status().isConflict());
    }

    @Test
    void refundOfUnknownPaymentIs404() throws Exception {
        mvc.perform(post("/payments/{id}/refund", 987654)).andExpect(status().isNotFound());
    }
}
