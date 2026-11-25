package com.github.diovanibmotta.soa.contracts;

import java.util.List;

/**
 * Canonical message layer: the one enterprise-wide vocabulary. Every business service and the orchestrator speak
 * these records, so services never translate between each other's models. It is also the main coupling point of the
 * style: changing a canonical message touches every consumer (see docs/04).
 */
public final class Canonical {

    private Canonical() {}

    public enum OrderStatus { CREATED, CONFIRMED, CANCELLED }

    // --- entry message of the order-to-cash workflow (what the channel sends to the orchestrator)
    public record OrderRequest(String customerId, String sku, int quantity, long unitPriceCents) {
        public long amountCents() {
            return unitPriceCents * quantity;
        }
    }

    // --- Customer service
    public record CreditCheckRequest(String customerId, long amountCents) {}

    public record CreditCheckResult(boolean approved, String reason) {}

    // --- Inventory service
    public record ReserveRequest(String orderId, String sku, int quantity) {}

    public record Reservation(String orderId, boolean reserved, String reason) {}

    // --- Billing service
    public record ChargeRequest(String orderId, String customerId, long amountCents) {}

    public record ChargeResult(String orderId, boolean approved, String reason) {}

    // --- Order service
    public record NewOrder(String customerId, String sku, int quantity, long amountCents) {}

    public record OrderUpdate(OrderStatus status, String reason) {}

    public record OrderRecord(String id, String customerId, String sku, int quantity, long amountCents,
                              OrderStatus status, String reason) {}

    /** One executed step of a workflow, returned to the caller as an audit trail of the orchestration. */
    public record Step(String service, String operation, String outcome) {}

    public record WorkflowResult(OrderRecord order, List<Step> steps) {}
}
