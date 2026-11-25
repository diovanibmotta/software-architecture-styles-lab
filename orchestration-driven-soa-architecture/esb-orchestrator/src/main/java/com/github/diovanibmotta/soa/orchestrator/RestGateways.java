package com.github.diovanibmotta.soa.orchestrator;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderUpdate;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

/** HTTP adapters for the four business services. Any transport/5xx/4xx failure becomes a ServiceUnavailableException. */
@Component
class RestGateways implements Gateways.Customers, Gateways.Inventory, Gateways.Billing, Gateways.Orders {

    private final RestClient customer, inventory, billing, order;

    RestGateways(Endpoints endpoints) {
        this.customer = client(endpoints.customer());
        this.inventory = client(endpoints.inventory());
        this.billing = client(endpoints.billing());
        this.order = client(endpoints.order());
    }

    private static RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(5_000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static <T> T call(String service, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(service, e);
        }
    }

    @Override
    public CreditCheckResult checkCredit(CreditCheckRequest request) {
        return call("customer", () -> customer.post().uri("/credit-checks").body(request).retrieve()
                .body(CreditCheckResult.class));
    }

    @Override
    public Reservation reserve(ReserveRequest request) {
        return call("inventory", () -> inventory.post().uri("/reservations").body(request).retrieve()
                .body(Reservation.class));
    }

    @Override
    public void release(String orderId) {
        call("inventory", () -> inventory.delete().uri("/reservations/{id}", orderId).retrieve().toBodilessEntity());
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        return call("billing", () -> billing.post().uri("/charges").body(request).retrieve().body(ChargeResult.class));
    }

    @Override
    public OrderRecord create(NewOrder newOrder) {
        return call("order", () -> order.post().uri("/orders").body(newOrder).retrieve().body(OrderRecord.class));
    }

    @Override
    public OrderRecord settle(String orderId, OrderStatus status, String reason) {
        return call("order", () -> order.put().uri("/orders/{id}/status", orderId)
                .body(new OrderUpdate(status, reason)).retrieve().body(OrderRecord.class));
    }

    @Override
    public OrderRecord find(String orderId) {
        return call("order", () -> order.get().uri("/orders/{id}", orderId).retrieve().body(OrderRecord.class));
    }
}
