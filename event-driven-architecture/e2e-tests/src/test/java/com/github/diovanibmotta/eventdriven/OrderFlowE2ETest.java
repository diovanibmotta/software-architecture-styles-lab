package com.github.diovanibmotta.eventdriven;

import com.github.diovanibmotta.eventdriven.inventory.InventoryApplication;
import com.github.diovanibmotta.eventdriven.notification.NotificationApplication;
import com.github.diovanibmotta.eventdriven.order.OrderApplication;
import com.github.diovanibmotta.eventdriven.payment.PaymentApplication;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real broker (embedded Artemis over TCP) + the four processors, each its own Spring context talking only through
 * the broker and its own HTTP port, exactly like the docker-compose topology but without Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFlowE2ETest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    private EmbeddedActiveMQ broker;
    private String brokerUrl;
    private ConfigurableApplicationContext order, inventory, payment, notification;
    private RestClient orderApi, inventoryApi, notificationApi;

    @BeforeAll
    void start() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        brokerUrl = "tcp://127.0.0.1:" + port;
        Configuration config = new ConfigurationImpl()
                .setPersistenceEnabled(false).setSecurityEnabled(false).setJMXManagementEnabled(false)
                .addAcceptorConfiguration("tcp", brokerUrl);
        broker = new EmbeddedActiveMQ().setConfiguration(config);
        broker.start();

        order = boot(OrderApplication.class);
        inventory = boot(InventoryApplication.class);
        payment = boot(PaymentApplication.class);
        notification = boot(NotificationApplication.class);
        orderApi = api(order);
        inventoryApi = api(inventory);
        notificationApi = api(notification);
    }

    @AfterAll
    void stop() throws Exception {
        for (ConfigurableApplicationContext c : new ConfigurableApplicationContext[]{order, inventory, payment, notification}) {
            if (c != null) c.close();
        }
        broker.stop();
    }

    private ConfigurableApplicationContext boot(Class<?> app) {
        return new SpringApplicationBuilder(app)
                .properties("server.port=0", "spring.artemis.mode=native", "spring.artemis.broker-url=" + brokerUrl,
                        "spring.jmx.enabled=false", "spring.main.banner-mode=off", "logging.level.root=WARN")
                .run();
    }

    private static RestClient api(ConfigurableApplicationContext ctx) {
        return RestClient.create("http://localhost:" + ctx.getEnvironment().getProperty("local.server.port"));
    }

    private void setStock(String sku, int quantity) {
        inventoryApi.put().uri("/stock/{sku}", sku).body(Map.of("quantity", quantity)).retrieve().toBodilessEntity();
    }

    private int stock(String sku) {
        return (Integer) inventoryApi.get().uri("/stock/{sku}", sku).retrieve().body(MAP).get("quantity");
    }

    private String placeOrder(String customer, String sku, int quantity, long unitPriceCents) {
        var response = orderApi.post().uri("/orders")
                .body(Map.of("customerId", customer, "sku", sku, "quantity", quantity, "unitPriceCents", unitPriceCents))
                .retrieve().toEntity(MAP);
        assertThat(response.getStatusCode().value()).as("accepted, not completed").isEqualTo(202);
        assertThat(response.getBody()).containsEntry("status", "PENDING");
        return (String) response.getBody().get("id");
    }

    private Map<String, Object> order(String id) {
        return orderApi.get().uri("/orders/{id}", id).retrieve().body(MAP);
    }

    private Map<String, Object> awaitSettled(String id) {
        await().atMost(Duration.ofSeconds(20)).until(() -> !"PENDING".equals(order(id).get("status")));
        return order(id);
    }

    @Test
    void happyPathConfirmsOrderReservesStockAndNotifiesCustomer() {
        setStock("happy-console", 3);

        String id = placeOrder("ana", "happy-console", 1, 300_000);

        assertThat(awaitSettled(id)).containsEntry("status", "CONFIRMED");
        assertThat(stock("happy-console")).isEqualTo(2);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationApi.get().uri("/notifications?customerId=ana").retrieve().body(LIST))
                        .anyMatch(n -> n.get("message").toString().contains(id + " confirmed")));
    }

    @Test
    void outOfStockCancelsOrderAndNotifiesCustomer() {
        setStock("nostock-phone", 1);

        String id = placeOrder("bruno", "nostock-phone", 2, 100_000);

        Map<String, Object> settled = awaitSettled(id);
        assertThat(settled).containsEntry("status", "CANCELLED");
        assertThat(settled.get("reason").toString()).startsWith("stock rejected");
        assertThat(stock("nostock-phone")).isEqualTo(1);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(notificationApi.get().uri("/notifications?customerId=bruno").retrieve().body(LIST))
                        .anyMatch(n -> n.get("message").toString().contains(id + " cancelled")));
    }

    @Test
    void declinedPaymentCancelsOrderAndCompensatesByReleasingStock() {
        setStock("declined-tv", 3);

        String id = placeOrder("carla", "declined-tv", 1, 2_000_000);   // above the 1,000,000 limit

        assertThat(awaitSettled(id)).containsEntry("status", "CANCELLED");
        // the reservation made by inventory is undone by reacting to PaymentDeclined: no distributed transaction
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(stock("declined-tv")).isEqualTo(3));
    }

    @Test
    void neverOversellsWithConcurrentOrders() {
        setStock("race-ticket", 5);

        List<String> ids = IntStream.range(0, 12).parallel()
                .mapToObj(i -> placeOrder("buyer" + i, "race-ticket", 1, 1_000)).toList();

        List<Object> statuses = ids.stream().map(id -> awaitSettled(id).get("status")).toList();
        assertThat(statuses.stream().filter("CONFIRMED"::equals)).hasSize(5);
        assertThat(statuses.stream().filter("CANCELLED"::equals)).hasSize(7);
        assertThat(stock("race-ticket")).isZero();
    }

    @Test
    void brokerHoldsEventsWhileAProcessorIsDownAndDeliversWhenItReturns() throws Exception {
        setStock("resilient-laptop", 2);
        payment.close();                                   // payment-service crashes

        String id = placeOrder("dora", "resilient-laptop", 1, 500_000);

        Thread.sleep(1_500);                               // order and inventory keep working; payment is missing
        assertThat(order(id)).containsEntry("status", "PENDING");
        assertThat(stock("resilient-laptop")).isEqualTo(1);

        payment = boot(PaymentApplication.class);          // comes back: durable subscription still has the event
        assertThat(awaitSettled(id)).containsEntry("status", "CONFIRMED");
    }
}
