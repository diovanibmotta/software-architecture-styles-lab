package com.github.diovanibmotta.servicebased.order;

import com.github.diovanibmotta.servicebased.shared.CustomerOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
class OrderController {

    record PlaceOrder(@NotNull Long productId, @Min(1) @Max(10) int quantity) {
    }

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerOrder place(@Valid @RequestBody PlaceOrder request) {
        return service.place(request.productId(), request.quantity());
    }

    @GetMapping("/{id}")
    CustomerOrder get(@PathVariable Long id) {
        return service.get(id);
    }
}
