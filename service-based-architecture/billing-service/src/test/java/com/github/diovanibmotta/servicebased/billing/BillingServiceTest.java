package com.github.diovanibmotta.servicebased.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.diovanibmotta.servicebased.shared.ConflictException;
import com.github.diovanibmotta.servicebased.shared.CustomerOrder;
import com.github.diovanibmotta.servicebased.shared.InvoiceRepository;
import com.github.diovanibmotta.servicebased.shared.OrderRepository;
import com.github.diovanibmotta.servicebased.shared.Product;
import com.github.diovanibmotta.servicebased.shared.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BillingServiceTest {

    @Autowired BillingService service;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired InvoiceRepository invoices;
    @Autowired MockMvc mvc;

    private Long newOrder() {
        Long productId = products.save(new Product("Teclado", 40_000, 10)).getId();
        return orders.save(new CustomerOrder(productId, 2, 80_000)).getId();
    }

    @Test
    void payingSettlesOrderAndIssuesInvoice() throws Exception {
        Long orderId = newOrder();
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountCents").value(80_000));
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(CustomerOrder.Status.PAID);
        mvc.perform(get("/invoices/" + orderId)).andExpect(status().isOk());
    }

    @Test
    void secondPaymentIs409() throws Exception {
        Long orderId = newOrder();
        service.pay(orderId);
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isConflict());
        assertThat(invoices.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void unknownOrderIs404() throws Exception {
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":999999}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/invoices/999999")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentPaymentsIssueExactlyOneInvoice() throws Exception {
        Long orderId = newOrder();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                try {
                    service.pay(orderId);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            }));
        }
        long paid = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) paid++;
        }
        pool.shutdown();
        assertThat(paid).isEqualTo(1);
        assertThat(invoices.findByOrderId(orderId)).isPresent();
    }
}
