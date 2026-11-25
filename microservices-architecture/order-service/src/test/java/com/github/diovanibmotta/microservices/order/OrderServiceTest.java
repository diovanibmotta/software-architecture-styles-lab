package com.github.diovanibmotta.microservices.order;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "outbox.interval-ms=100")
@AutoConfigureMockMvc
class OrderServiceTest {

    static final StubServer delivery = new StubServer();
    static final StubServer payment = new StubServer();

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        registry.add("delivery.url", delivery::url);
        registry.add("payment.url", payment::url);
    }

    @AfterAll
    static void stopStubs() {
        delivery.close();
        payment.close();
    }

    @Autowired MockMvc mvc;
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;
    @MockitoBean CatalogClient catalog;
    @MockitoBean PaymentClient payments;

    @BeforeEach
    void reset() {
        outbox.deleteAll();
        delivery.requests.clear();
        payment.requests.clear();
        delivery.status.set(200);
        when(catalog.find(1)).thenReturn(Optional.of(new CatalogClient.Item(1, "Burger", 3_500)));
        when(payments.authorize(anyLong(), anyLong())).thenReturn(new PaymentClient.Payment(77, "AUTHORIZED"));
    }

    private long place(int quantity) throws Exception {
        String json = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"ana\",\"itemId\":1,\"quantity\":" + quantity + ",\"address\":\"Rua A, 1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private void callback(long id, String status, String courier) throws Exception {
        mvc.perform(post("/orders/{id}/delivery", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\",\"courier\":" + (courier == null ? "null" : "\"" + courier + "\"") + "}"))
                .andExpect(status().isNoContent());
    }

    private String statusOf(long id) {
        return orders.findById(id).orElseThrow().status().name();
    }

    @Test
    void happyPathPaysDispatchesAsynchronouslyThenConfirmsAndDelivers() throws Exception {
        long id = place(2);

        assertThat(orders.findById(id).orElseThrow().totalCents()).isEqualTo(7_000);
        assertThat(statusOf(id)).isEqualTo("PAID");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(delivery.requests).contains("POST /deliveries"));
        await().atMost(Duration.ofSeconds(5)).until(() -> outbox.findBySentFalseOrderById().isEmpty());

        callback(id, "ASSIGNED", "courier-1");
        mvc.perform(get("/orders/{id}", id)).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.courier").value("courier-1"));
        callback(id, "DELIVERED", "courier-1");
        assertThat(statusOf(id)).isEqualTo("DELIVERED");
    }

    @Test
    void declinedPaymentRejectsOrderAndNeverDispatches() throws Exception {
        when(payments.authorize(anyLong(), anyLong())).thenReturn(new PaymentClient.Payment(78, "DECLINED"));

        long id = place(1);

        assertThat(statusOf(id)).isEqualTo("REJECTED");
        assertThat(outbox.count()).isZero();
    }

    @Test
    void noCourierCompensatesByRefundingThePayment() throws Exception {
        long id = place(1);
        callback(id, "NO_COURIER", null);

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(payment.requests).contains("POST /payments/77/refund"));
    }

    @Test
    void callbacksAreIdempotent() throws Exception {
        long id = place(1);
        callback(id, "NO_COURIER", null);
        callback(id, "NO_COURIER", null);          // duplicate: no second refund message
        callback(id, "ASSIGNED", "courier-9");     // late/contradictory: a cancelled order stays cancelled

        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(outbox.findAll()).filteredOn(m -> m.kind().equals(OutboxMessage.REFUND)).hasSize(1);
    }

    @Test
    void deliveryServiceDownDelaysDispatchButNothingIsLost() throws Exception {
        delivery.status.set(503);
        long id = place(1);

        await().atMost(Duration.ofSeconds(5)).until(() -> delivery.requests.size() >= 2);   // retried
        assertThat(outbox.findBySentFalseOrderById()).hasSize(1);
        assertThat(statusOf(id)).isEqualTo("PAID");

        delivery.status.set(200);                   // service is back
        await().atMost(Duration.ofSeconds(5)).until(() -> outbox.findBySentFalseOrderById().isEmpty());
    }

    @Test
    void unknownItemIs404AndStoresNothing() throws Exception {
        long before = orders.count();
        when(catalog.find(99)).thenReturn(Optional.empty());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"ana\",\"itemId\":99,\"quantity\":1,\"address\":\"x\"}")).andExpect(status().isNotFound());
        assertThat(orders.count()).isEqualTo(before);
    }

    @Test
    void catalogDownIs503AndStoresNothing() throws Exception {
        long before = orders.count();
        when(catalog.find(1)).thenThrow(new DependencyUnavailableException("catalog", null));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"ana\",\"itemId\":1,\"quantity\":1,\"address\":\"x\"}")).andExpect(status().isServiceUnavailable());
        assertThat(orders.count()).isEqualTo(before);
    }

    @Test
    void paymentDownIs503AndOrderIsRejected() throws Exception {
        when(payments.authorize(anyLong(), anyLong())).thenThrow(new DependencyUnavailableException("payment", null));
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"ana\",\"itemId\":1,\"quantity\":1,\"address\":\"x\"}")).andExpect(status().isServiceUnavailable());
        assertThat(orders.findAll()).anyMatch(o -> o.status() == Order.Status.REJECTED && "payment unavailable".equals(o.reason()));
    }

    @Test
    void invalidQuantityIs400() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"ana\",\"itemId\":1,\"quantity\":0,\"address\":\"x\"}")).andExpect(status().isBadRequest());
    }
}
