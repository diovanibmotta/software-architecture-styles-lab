package com.github.diovanibmotta.servicebased.catalog;

import com.github.diovanibmotta.servicebased.shared.NotFoundException;
import com.github.diovanibmotta.servicebased.shared.Product;
import com.github.diovanibmotta.servicebased.shared.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Catalog domain service: owns product data (name, price, initial stock). */
@RestController
@RequestMapping("/products")
class ProductController {

    record NewProduct(@NotBlank String name, @Min(1) long priceCents, @Min(0) int stock) {
    }

    private final ProductRepository products;

    ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    List<Product> list() {
        return products.findAll();
    }

    @GetMapping("/{id}")
    Product get(@PathVariable Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Product create(@Valid @RequestBody NewProduct request) {
        return products.save(new Product(request.name(), request.priceCents(), request.stock()));
    }
}
