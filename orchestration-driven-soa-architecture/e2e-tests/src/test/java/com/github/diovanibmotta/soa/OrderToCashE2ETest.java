package com.github.diovanibmotta.soa;

import com.github.diovanibmotta.soa.billing.BillingApplication;
import com.github.diovanibmotta.soa.customer.CustomerApplication;
import com.github.diovanibmotta.soa.inventory.InventoryApplication;
import com.github.diovanibmotta.soa.order.OrderApplication;
import com.github.diovanibmotta.soa.orchestrator.OrchestratorApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The orchestrator plus the four business services, each in its own Spring context on its own HTTP port, wired only
 * through URLs: the same topology as docker-compose, without Docker.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderToCashE2ETest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private ConfigurableApplicationContext customer, inventory, billing, order, orchestrator, brokenOrchestrator;
    private RestClient orchestratorApi, brokenApi, inventoryApi, customerApi;

    @BeforeAll
    void start() throws Exception {
        customer = boot(CustomerApplication.class, "customer.initial=ana=5000000");
        inventory = boot(InventoryApplication.class);
        billing = boot(BillingApplication.class);
        order = boot(OrderApplication.class);
        orchestrator = orchestrator(url(billing));
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();           // nothing listens here once closed: a billing service that is down
        }
        brokenOrchestrator = orchestrator("http://localhost:" + deadPort);
        orchestratorApi = RestClient.create(url(orchestrator));
        brokenApi = RestClient.create(url(brokenOrchestrator));
        inventoryApi = RestClient.create(url(inventory));
        customerApi = RestClient.create(url(customer));
    }

    @AfterAll
    void stop() {
        for (ConfigurableApplicationContext c : List.of(brokenOrchestrator, orchestrator, order, billing, inventory, customer)) {
            if (c != null) c.close();
        }
    }

    private ConfigurableApplicationContext orchestrator(String billingUrl) {
        return boot(OrchestratorApplication.class,
                "soa.endpoints.customer=" + url(customer), "soa.endpoints.inventory=" + url(inventory),
                "soa.endpoints.billing=" + billingUrl, "soa.endpoints.order=" + url(order));
    }

    /** Properties go as command-line args (highest precedence): every module ships its own application.yml and only one wins on the shared test classpath. */
    private static ConfigurableApplicationContext boot(Class<?> app, String... properties) {
        List<String> args = new java.util.ArrayList<>(List.of("--server.port=0", "--spring.main.banner-mode=off", "--logging.level.root=WARN"));
        for (String p : properties) args.add("--" + p);
        return new SpringApplicationBuilder(app).run(args.toArray(String[]::new));
    }

    private static String url(ConfigurableApplicationContext ctx) {
        return "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port");
    }

    private void setStock(String sku, int quantity) {
        inventoryApi.put().uri("/stock/{sku}", sku).body(Map.of("quantity", quantity)).retrieve().toBodilessEntity();
    }

    private int stock(String sku) {
        return (Integer) inventoryApi.get().uri("/stock/{sku}", sku).retrieve().body(MAP).get("quantity");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> orderOf(Map<String, Object> result) {
        return (Map<String, Object>) result.get("order");
    }

    @SuppressWarnings("unchecked")
    private static List<String> trace(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("steps")).stream()
                .map(s -> s.get("service") + "." + s.get("operation") + ":" + s.get("outcome")).toList();
    }

    private Map<String, Object> place(RestClient api, String customerId, String sku, int quantity, long unitPriceCents) {
        var response = api.post().uri("/orders")
                .body(Map.of("customerId", customerId, "sku", sku, "quantity", quantity, "unitPriceCents", unitPriceCents))
                .retrieve().toEntity(MAP);
        assertThat(response.getStatusCode().value()).as("final outcome returned synchronously").isEqualTo(201);
        return response.getBody();
    }

    @Test
    void happyPathConfirmsOrderReservesStockAndExposesTheOrchestrationTrace() {
        setStock("happy-console", 3);

        Map<String, Object> result = place(orchestratorApi, "ana", "happy-console", 1, 300_000);

        assertThat(orderOf(result)).containsEntry("status", "CONFIRMED");
        assertThat(trace(result)).extracting(s -> s.split(":")[0])
                .containsExactly("order.create", "customer.checkCredit", "inventory.reserve", "billing.charge", "order.settle");
        assertThat(stock("happy-console")).isEqualTo(2);
        String id = (String) orderOf(result).get("id");
        assertThat(orchestratorApi.get().uri("/orders/{id}", id).retrieve().body(MAP)).containsEntry("status", "CONFIRMED");
    }

    @Test
    void unknownCustomerIsRejectedBeforeTouchingStock() {
        setStock("unk-phone", 3);

        Map<String, Object> result = place(orchestratorApi, "nobody", "unk-phone", 1, 1_000);

        assertThat(orderOf(result)).containsEntry("status", "CANCELLED").containsEntry("reason", "credit denied: unknown customer");
        assertThat(stock("unk-phone")).isEqualTo(3);
    }

    @Test
    void inactiveCustomerIsRejected() {
        customerApi.put().uri("/customers/{id}", "zeca").body(Map.of("active", false, "creditLimitCents", 9_000_000))
                .retrieve().toBodilessEntity();

        Map<String, Object> result = place(orchestratorApi, "zeca", "any", 1, 1_000);

        assertThat(orderOf(result)).containsEntry("reason", "credit denied: customer inactive");
    }

    @Test
    void outOfStockCancelsOrderWithoutCharging() {
        setStock("nostock-phone", 1);

        Map<String, Object> result = place(orchestratorApi, "ana", "nostock-phone", 2, 100_000);

        assertThat(orderOf(result)).containsEntry("status", "CANCELLED");
        assertThat(orderOf(result).get("reason").toString()).startsWith("stock rejected");
        assertThat(trace(result)).noneMatch(s -> s.startsWith("billing"));
        assertThat(stock("nostock-phone")).isEqualTo(1);
    }

    @Test
    void declinedPaymentCancelsOrderAndCompensatesByReleasingStock() {
        setStock("declined-tv", 3);

        Map<String, Object> result = place(orchestratorApi, "ana", "declined-tv", 1, 2_000_000);   // above the 1,000,000 limit

        assertThat(orderOf(result)).containsEntry("status", "CANCELLED");
        assertThat(orderOf(result).get("reason").toString()).startsWith("payment declined");
        assertThat(trace(result)).contains("inventory.release:compensated");
        assertThat(stock("declined-tv")).isEqualTo(3);
    }

    @Test
    void neverOversellsWithConcurrentOrders() {
        setStock("race-ticket", 5);

        List<Object> statuses = IntStream.range(0, 12).parallel()
                .mapToObj(i -> orderOf(place(orchestratorApi, "ana", "race-ticket", 1, 1_000)).get("status")).toList();

        assertThat(statuses.stream().filter("CONFIRMED"::equals)).hasSize(5);
        assertThat(statuses.stream().filter("CANCELLED"::equals)).hasSize(7);
        assertThat(stock("race-ticket")).isZero();
    }

    @Test
    void billingDownIsSurfacedAs502AfterCompensatingStockAndCancellingTheOrder() {
        setStock("down-laptop", 2);

        var response = brokenApi.post().uri("/orders")
                .body(Map.of("customerId", "ana", "sku", "down-laptop", "quantity", 1, "unitPriceCents", 500_000))
                .exchange((req, res) -> Map.entry(res.getStatusCode().value(), res.bodyTo(MAP)));

        assertThat(response.getKey()).isEqualTo(502);
        assertThat(response.getValue()).containsEntry("service", "billing");
        assertThat(stock("down-laptop")).as("reservation released by compensation").isEqualTo(2);
        String id = (String) response.getValue().get("orderId");
        assertThat(orchestratorApi.get().uri("/orders/{id}", id).retrieve().body(MAP))
                .containsEntry("status", "CANCELLED").containsEntry("reason", "billing-service unavailable");
    }
}
