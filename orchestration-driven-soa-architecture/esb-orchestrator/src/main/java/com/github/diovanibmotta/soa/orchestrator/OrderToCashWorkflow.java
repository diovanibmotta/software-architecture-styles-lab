package com.github.diovanibmotta.soa.orchestrator;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.Step;
import com.github.diovanibmotta.soa.contracts.Canonical.WorkflowResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The orchestration engine of the lab (the ESB's "process"): the ONLY place that knows the order-to-cash sequence
 * (credit → stock → charge → settle), its branching and its compensation. Business services stay passive and
 * reusable; they never call each other. The price is that this class (and the process hosting it) is the center of
 * everything: bottleneck, single point of failure and the place every change lands.
 */
@Service
public class OrderToCashWorkflow {

    private final Gateways.Customers customers;
    private final Gateways.Inventory inventory;
    private final Gateways.Billing billing;
    private final Gateways.Orders orders;

    OrderToCashWorkflow(Gateways.Customers customers, Gateways.Inventory inventory, Gateways.Billing billing,
                        Gateways.Orders orders) {
        this.customers = customers;
        this.inventory = inventory;
        this.billing = billing;
        this.orders = orders;
    }

    public WorkflowResult execute(OrderRequest request) {
        List<Step> steps = new ArrayList<>();
        OrderRecord order = orders.create(new NewOrder(request.customerId(), request.sku(), request.quantity(),
                request.amountCents()));
        steps.add(new Step("order", "create", order.id()));
        boolean reserved = false;
        boolean charged = false;
        try {
            CreditCheckResult credit = customers.checkCredit(new CreditCheckRequest(request.customerId(), request.amountCents()));
            steps.add(new Step("customer", "checkCredit", credit.approved() ? "approved" : credit.reason()));
            if (!credit.approved()) return cancel(order, steps, "credit denied: " + credit.reason());

            Reservation reservation = inventory.reserve(new ReserveRequest(order.id(), request.sku(), request.quantity()));
            steps.add(new Step("inventory", "reserve", reservation.reserved() ? "reserved" : reservation.reason()));
            if (!reservation.reserved()) return cancel(order, steps, "stock rejected: " + reservation.reason());
            reserved = true;

            ChargeResult charge = billing.charge(new ChargeRequest(order.id(), request.customerId(), request.amountCents()));
            steps.add(new Step("billing", "charge", charge.approved() ? "approved" : charge.reason()));
            if (!charge.approved()) {
                inventory.release(order.id());                     // compensation: undo the reservation
                steps.add(new Step("inventory", "release", "compensated"));
                return cancel(order, steps, "payment declined: " + charge.reason());
            }
            charged = true;

            OrderRecord confirmed = orders.settle(order.id(), OrderStatus.CONFIRMED, null);
            steps.add(new Step("order", "settle", "CONFIRMED"));
            return new WorkflowResult(confirmed, steps);
        } catch (ServiceUnavailableException e) {
            steps.add(new Step(e.service(), "call", "unavailable"));
            if (charged) {
                // money was taken and we cannot say so to the order system: needs reconciliation (no refund operation here)
                steps.add(new Step("orchestrator", "reconcile", "order " + order.id() + " charged but not settled"));
            } else {
                compensate(order.id(), reserved, e.service(), steps);
            }
            throw e.forOrder(order.id());
        }
    }

    public OrderRecord find(String orderId) {
        return orders.find(orderId);
    }

    private WorkflowResult cancel(OrderRecord order, List<Step> steps, String reason) {
        OrderRecord cancelled = orders.settle(order.id(), OrderStatus.CANCELLED, reason);
        steps.add(new Step("order", "settle", "CANCELLED"));
        return new WorkflowResult(cancelled, steps);
    }

    /** Best effort: each undo is attempted on its own; a failing undo is recorded, not thrown over the original error. */
    private void compensate(String orderId, boolean reserved, String failedService, List<Step> steps) {
        if (reserved) {
            try {
                inventory.release(orderId);
                steps.add(new Step("inventory", "release", "compensated"));
            } catch (ServiceUnavailableException e) {
                steps.add(new Step("inventory", "release", "failed"));
            }
        }
        try {
            orders.settle(orderId, OrderStatus.CANCELLED, failedService + "-service unavailable");
            steps.add(new Step("order", "settle", "CANCELLED"));
        } catch (ServiceUnavailableException e) {
            steps.add(new Step("order", "settle", "failed"));
        }
    }
}
