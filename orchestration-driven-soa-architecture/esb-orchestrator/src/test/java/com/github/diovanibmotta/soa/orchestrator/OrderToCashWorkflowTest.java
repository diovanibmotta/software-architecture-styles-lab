package com.github.diovanibmotta.soa.orchestrator;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import com.github.diovanibmotta.soa.contracts.Canonical.Step;
import com.github.diovanibmotta.soa.contracts.Canonical.WorkflowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderToCashWorkflowTest {

    private final Gateways.Customers customers = mock(Gateways.Customers.class);
    private final Gateways.Inventory inventory = mock(Gateways.Inventory.class);
    private final Gateways.Billing billing = mock(Gateways.Billing.class);
    private final Gateways.Orders orders = mock(Gateways.Orders.class);
    private final OrderToCashWorkflow workflow = new OrderToCashWorkflow(customers, inventory, billing, orders);
    private final OrderRequest request = new OrderRequest("ana", "console", 2, 100);

    @BeforeEach
    void happyDefaults() {
        when(orders.create(any())).thenReturn(order(OrderStatus.CREATED, null));
        when(orders.settle(eq("o1"), any(), any())).thenAnswer(i -> order(i.getArgument(1), i.getArgument(2)));
        when(customers.checkCredit(any())).thenReturn(new CreditCheckResult(true, null));
        when(inventory.reserve(any())).thenReturn(new Reservation("o1", true, null));
        when(billing.charge(any())).thenReturn(new ChargeResult("o1", true, null));
    }

    private static OrderRecord order(OrderStatus status, String reason) {
        return new OrderRecord("o1", "ana", "console", 2, 200, status, reason);
    }

    private static java.util.List<String> trace(WorkflowResult r) {
        return r.steps().stream().map(Step::service).toList();
    }

    @Test
    void happyPathRunsEveryServiceInOrderAndConfirms() {
        WorkflowResult result = workflow.execute(request);

        assertThat(result.order().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(trace(result)).containsExactly("order", "customer", "inventory", "billing", "order");
    }

    @Test
    void creditDeniedStopsBeforeInventoryAndBilling() {
        when(customers.checkCredit(any())).thenReturn(new CreditCheckResult(false, "credit limit exceeded"));

        WorkflowResult result = workflow.execute(request);

        assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().reason()).isEqualTo("credit denied: credit limit exceeded");
        verify(inventory, never()).reserve(any());
        verify(billing, never()).charge(any());
    }

    @Test
    void stockRejectedStopsBeforeBilling() {
        when(inventory.reserve(any())).thenReturn(new Reservation("o1", false, "insufficient stock"));

        WorkflowResult result = workflow.execute(request);

        assertThat(result.order().reason()).isEqualTo("stock rejected: insufficient stock");
        verify(billing, never()).charge(any());
        verify(inventory, never()).release(anyString());
    }

    @Test
    void declinedPaymentCompensatesByReleasingStock() {
        when(billing.charge(any())).thenReturn(new ChargeResult("o1", false, "limit"));

        WorkflowResult result = workflow.execute(request);

        assertThat(result.order().status()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventory).release("o1");
        assertThat(result.steps()).contains(new Step("inventory", "release", "compensated"));
    }

    @Test
    void serviceDownAfterReservationReleasesStockCancelsOrderAndSurfacesTheFailure() {
        when(billing.charge(any())).thenThrow(new ServiceUnavailableException("billing", new RuntimeException("boom")));

        assertThatThrownBy(() -> workflow.execute(request))
                .isInstanceOfSatisfying(ServiceUnavailableException.class, e -> {
                    assertThat(e.service()).isEqualTo("billing");
                    assertThat(e.orderId()).isEqualTo("o1");
                });
        verify(inventory).release("o1");
        verify(orders).settle("o1", OrderStatus.CANCELLED, "billing-service unavailable");
    }

    @Test
    void failureAfterChargeDoesNotReleaseStockAndFlagsReconciliation() {
        when(orders.settle("o1", OrderStatus.CONFIRMED, null))
                .thenThrow(new ServiceUnavailableException("order", new RuntimeException("boom")));

        assertThatThrownBy(() -> workflow.execute(request)).isInstanceOf(ServiceUnavailableException.class);
        verify(inventory, never()).release(anyString());
    }
}
