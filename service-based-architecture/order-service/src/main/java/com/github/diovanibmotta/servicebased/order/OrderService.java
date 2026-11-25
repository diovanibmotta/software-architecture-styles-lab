package com.github.diovanibmotta.servicebased.order;

import com.github.diovanibmotta.servicebased.shared.ConflictException;
import com.github.diovanibmotta.servicebased.shared.CustomerOrder;
import com.github.diovanibmotta.servicebased.shared.NotFoundException;
import com.github.diovanibmotta.servicebased.shared.OrderRepository;
import com.github.diovanibmotta.servicebased.shared.Product;
import com.github.diovanibmotta.servicebased.shared.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {

    private final ProductRepository products;
    private final OrderRepository orders;

    OrderService(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    /**
     * Stock decrement and order insert are ONE local ACID transaction: the benefit of a shared database.
     * In microservices this would need a saga; here a rollback undoes everything.
     */
    @Transactional
    CustomerOrder place(Long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product " + productId + " not found"));
        if (products.decrementStock(productId, quantity) == 0) {
            throw new ConflictException("Insufficient stock for product " + productId);
        }
        return orders.save(new CustomerOrder(productId, quantity, product.getPriceCents() * quantity));
    }

    @Transactional(readOnly = true)
    CustomerOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }
}
