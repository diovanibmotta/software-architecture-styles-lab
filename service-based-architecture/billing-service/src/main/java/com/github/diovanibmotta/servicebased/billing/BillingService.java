package com.github.diovanibmotta.servicebased.billing;

import com.github.diovanibmotta.servicebased.shared.ConflictException;
import com.github.diovanibmotta.servicebased.shared.CustomerOrder;
import com.github.diovanibmotta.servicebased.shared.Invoice;
import com.github.diovanibmotta.servicebased.shared.InvoiceRepository;
import com.github.diovanibmotta.servicebased.shared.NotFoundException;
import com.github.diovanibmotta.servicebased.shared.OrderRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BillingService {

    private final OrderRepository orders;
    private final InvoiceRepository invoices;

    BillingService(OrderRepository orders, InvoiceRepository invoices) {
        this.orders = orders;
        this.invoices = invoices;
    }

    /**
     * Reads the order straight from the shared schema (no call to order-service) and settles it atomically.
     * The compare-and-set makes a double payment impossible even with concurrent requests.
     */
    @Transactional
    Invoice pay(Long orderId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order " + orderId + " not found"));
        if (orders.markPaid(orderId) == 0) {
            throw new ConflictException("Order " + orderId + " is already paid");
        }
        return invoices.save(new Invoice(orderId, order.getTotalCents(), Instant.now()));
    }

    @Transactional(readOnly = true)
    Invoice invoiceOf(Long orderId) {
        return invoices.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("No invoice for order " + orderId));
    }
}
