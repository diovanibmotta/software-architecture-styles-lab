package com.github.diovanibmotta.servicebased.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.diovanibmotta.servicebased.shared.ConflictException;
import com.github.diovanibmotta.servicebased.shared.OrderRepository;
import com.github.diovanibmotta.servicebased.shared.Product;
import com.github.diovanibmotta.servicebased.shared.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
class OrderServiceTest {

    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired MockMvc mvc;

    @Test
    void placingOrderDecrementsStockAndComputesTotal() throws Exception {
        Long id = products.save(new Product("Fone", 25_000, 3)).getId();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(50_000))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void insufficientStockIs409AndLeavesNoOrder() throws Exception {
        Long id = products.save(new Product("Mouse", 9_000, 1)).getId();
        long before = orders.count();
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":2}"))
                .andExpect(status().isConflict());
        assertThat(orders.count()).isEqualTo(before);
        assertThat(products.findById(id).orElseThrow().getStock()).isEqualTo(1);
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":999999,\"quantity\":1}")).andExpect(status().isNotFound());
    }

    @Test
    void neverOversellsUnderConcurrentBuyers() throws Exception {
        Long id = products.save(new Product("Console", 300_000, 5)).getId();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Callable<Boolean> buyer = () -> {
                try {
                    service.place(id, 1);
                    return true;
                } catch (ConflictException e) {
                    return false;
                }
            };
            results.add(pool.submit(buyer));
        }
        long sold = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) sold++;
        }
        pool.shutdown();
        assertThat(sold).isEqualTo(5);
        assertThat(products.findById(id).orElseThrow().getStock()).isZero();
        assertThatThrownBy(() -> service.place(id, 1)).isInstanceOf(ConflictException.class);
    }
}
