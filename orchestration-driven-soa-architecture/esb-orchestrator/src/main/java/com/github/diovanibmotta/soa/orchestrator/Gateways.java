package com.github.diovanibmotta.soa.orchestrator;

import com.github.diovanibmotta.soa.contracts.Canonical.ChargeRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.ChargeResult;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.CreditCheckResult;
import com.github.diovanibmotta.soa.contracts.Canonical.NewOrder;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderStatus;
import com.github.diovanibmotta.soa.contracts.Canonical.Reservation;
import com.github.diovanibmotta.soa.contracts.Canonical.ReserveRequest;

/** The orchestrator's view of each business service: canonical messages in, canonical messages out. */
public final class Gateways {

    private Gateways() {}

    public interface Customers {
        CreditCheckResult checkCredit(CreditCheckRequest request);
    }

    public interface Inventory {
        Reservation reserve(ReserveRequest request);

        void release(String orderId);
    }

    public interface Billing {
        ChargeResult charge(ChargeRequest request);
    }

    public interface Orders {
        OrderRecord create(NewOrder order);

        OrderRecord settle(String orderId, OrderStatus status, String reason);

        OrderRecord find(String orderId);
    }
}
