package com.github.diovanibmotta.microservices;

import com.github.diovanibmotta.microservices.catalog.CatalogApplication;
import com.github.diovanibmotta.microservices.delivery.DeliveryApplication;
import com.github.diovanibmotta.microservices.gateway.GatewayApplication;
import com.github.diovanibmotta.microservices.order.OrderApplication;
import com.github.diovanibmotta.microservices.payment.PaymentApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole system, every service in its own Spring context with its own HTTP port and its own database, reached only
 * through the gateway: the same topology as docker-compose, without Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoodDeliveryE2ETest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private int gatewayPort, catalogPort, orderPort, paymentPort, deliveryPort;
    private ConfigurableApplicationContext gateway, catalog, order, payment, delivery;
    private RestClient api;

    /** Reserves all ports at once so two services can never be handed the same one. */
    private static int[] freePorts(int count) throws Exception {
        ServerSocket[] sockets = new ServerSocket[count];
        int[] ports = new int[count];
        for (int i = 0; i < count; i++) {
            sockets[i] = new ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
        }
        for (ServerSocket s : sockets) {
            s.close();
        }
        return ports;
    }

    @BeforeAll
    void start() throws Exception {
        int[] ports = freePorts(5);
        gatewayPort = ports[0];
        catalogPort = ports[1];
        orderPort = ports[2];
        paymentPort = ports[3];
        deliveryPort = ports[4];
        catalog = boot(CatalogApplication.class, "catalog", catalogPort);
        payment = boot(PaymentApplication.class, "payment", paymentPort);
        delivery = bootDelivery();
        order = boot(OrderApplication.class, "order", orderPort,
                "catalog.url=" + url(catalogPort), "payment.url=" + url(paymentPort), "delivery.url=" + url(deliveryPort),
                "resilience.timeout-ms=500", "resilience.open-ms=500", "outbox.interval-ms=100");
        gateway = boot(GatewayApplication.class, "gateway", gatewayPort,
                "gateway.routes.catalog=" + url(catalogPort), "gateway.routes.orders=" + url(orderPort),
                "gateway.routes.payments=" + url(paymentPort), "gateway.routes.deliveries=" + url(deliveryPort));
        api = RestClient.create(url(gatewayPort) + "/api");
    }

    @AfterAll
    void stop() {
        for (ConfigurableApplicationContext c : new ConfigurableApplicationContext[]{gateway, order, delivery, payment, catalog}) {
            if (c != null) c.close();
        }
    }

    private static String url(int port) {
        return "http://127.0.0.1:" + port;
    }

    /**
     * Settings go as command-line arguments: they must beat each service's own {@code @PropertySource} file, which
     * {@code SpringApplicationBuilder.properties(..)} (default properties, lowest precedence) would not.
     */
    private ConfigurableApplicationContext boot(Class<?> app, String name, int port, String... extra) {
        List<String> args = new ArrayList<>(List.of("--server.port=" + port,
                "--spring.datasource.url=jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1",
                "--spring.jmx.enabled=false", "--spring.main.banner-mode=off", "--logging.level.root=WARN"));
        for (String property : extra) {
            args.add("--" + property);
        }
        return new SpringApplicationBuilder(app).run(args.toArray(String[]::new));
    }

    private ConfigurableApplicationContext bootDelivery() {
        return boot(DeliveryApplication.class, "delivery", deliveryPort,
                "delivery.couriers=1", "order.url=" + url(orderPort));
    }

    // ---- helpers (all traffic goes through the gateway) ----

    private ResponseEntity<Map<String, Object>> post(String path, Object body) {
        RestClient.RequestBodySpec spec = api.post().uri(path);
        return (body == null ? spec : spec.body(body)).retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {}).toEntity(MAP);
    }

    private Map<String, Object> get(String path) {
        return api.get().uri(path).retrieve().body(MAP);
    }

    private long newItem(long priceCents) {
        return ((Number) post("/catalog/items", Map.of("name", "Burger", "priceCents", priceCents)).getBody().get("id")).longValue();
    }

    private ResponseEntity<Map<String, Object>> placeOrder(long itemId, int quantity) {
        return post("/orders/orders", Map.of("customerId", "ana", "itemId", itemId, "quantity", quantity, "address", "Rua A, 1"));
    }

    private long id(ResponseEntity<Map<String, Object>> response) {
        return ((Number) response.getBody().get("id")).longValue();
    }

    private String orderStatus(long id) {
        return (String) get("/orders/orders/" + id).get("status");
    }

    private void awaitStatus(long id, String expected) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(orderStatus(id)).isEqualTo(expected));
    }

    // ---- scenarios ----

    @Test
    void happyPathPaysConfirmsAsynchronouslyAndDelivers() {
        long item = newItem(3_500);

        var created = placeOrder(item, 2);

        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).containsEntry("status", "PAID").containsEntry("totalCents", 7_000);
        long orderId = id(created);
        awaitStatus(orderId, "CONFIRMED");                       // delivery was dispatched via outbox + callback
        assertThat(get("/orders/orders/" + orderId)).containsEntry("courier", "courier-1");

        assertThat(post("/deliveries/deliveries/" + orderId + "/complete", null).getStatusCode().value()).isEqualTo(200);
        awaitStatus(orderId, "DELIVERED");
    }

    @Test
    void declinedPaymentRejectsTheOrderAndNothingIsDispatched() {
        long item = newItem(60_000);

        var created = placeOrder(item, 2);                       // 120,000 > payment limit (100,000)

        assertThat(created.getBody()).containsEntry("status", "REJECTED").containsEntry("reason", "payment declined");
        assertThat(get("/orders/orders/" + id(created))).containsEntry("status", "REJECTED").containsEntry("courier", null);
    }

    @Test
    void noCourierCancelsTheOrderAndTheSagaRefundsThePayment() {
        long item = newItem(3_500);
        long first = id(placeOrder(item, 1));
        awaitStatus(first, "CONFIRMED");                         // takes the only courier

        var second = placeOrder(item, 1);
        long secondId = id(second);
        long paymentId = ((Number) second.getBody().get("paymentId")).longValue();
        awaitStatus(secondId, "CANCELLED");                      // compensation step 1: order cancelled
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->   // compensation step 2: payment refunded (outbox)
                assertThat(get("/payments/payments/" + paymentId)).containsEntry("status", "REFUNDED"));

        post("/deliveries/deliveries/" + first + "/complete", null);   // frees the courier for other tests
        awaitStatus(first, "DELIVERED");
    }

    @Test
    void deliveryServiceOutageDelaysConfirmationButLosesNothing() {
        long item = newItem(3_500);
        delivery.close();                                        // delivery-service crashes

        long orderId = id(placeOrder(item, 1));                  // customer is still served: order is PAID
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(orderStatus(orderId)).isEqualTo("PAID"));

        delivery = bootDelivery();                               // comes back: the outbox relay delivers the pending message
        awaitStatus(orderId, "CONFIRMED");
        post("/deliveries/deliveries/" + orderId + "/complete", null);
        awaitStatus(orderId, "DELIVERED");
    }

    @Test
    void paymentOutageFailsFastWith503ThenTheCircuitRecovers() {
        long item = newItem(3_500);
        payment.close();

        for (int i = 0; i < 5; i++) {                            // timeouts / refused connections, then the breaker opens
            assertThat(placeOrder(item, 1).getStatusCode().value()).isEqualTo(503);
        }

        payment = boot(PaymentApplication.class, "payment-2", paymentPort);
        AtomicLong recovered = new AtomicLong();
        await().atMost(Duration.ofSeconds(15)).until(() -> {        // half-open trial succeeds -> closed again
            var response = placeOrder(item, 1);
            if (response.getStatusCode().value() == 201) {
                recovered.set(id(response));                        // keep exactly one order: it takes the only courier
            }
            return recovered.get() != 0;
        });
        awaitStatus(recovered.get(), "CONFIRMED");
        post("/deliveries/deliveries/" + recovered.get() + "/complete", null);
        awaitStatus(recovered.get(), "DELIVERED");
    }

    @Test
    void everyServiceOwnsItsOwnDatabase() {
        Map<String, List<String>> tables = Map.of(
                "catalog", tables(catalog), "payment", tables(payment), "delivery", tables(delivery), "order", tables(order));

        assertThat(tables.get("catalog")).containsExactly("items");
        assertThat(tables.get("payment")).containsExactly("payments");
        assertThat(tables.get("delivery")).containsExactly("deliveries");
        assertThat(tables.get("order")).containsExactlyInAnyOrder("orders", "outbox");
        assertThat(List.of(catalog, payment, delivery, order).stream().map(this::jdbcUrl).distinct()).hasSize(4);
    }

    private List<String> tables(ConfigurableApplicationContext ctx) {
        return ctx.getBean(JdbcTemplate.class)
                .queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC'", String.class)
                .stream().map(String::toLowerCase).toList();
    }

    private String jdbcUrl(ConfigurableApplicationContext ctx) {
        try (var c = ctx.getBean(DataSource.class).getConnection()) {
            return c.getMetaData().getURL();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
