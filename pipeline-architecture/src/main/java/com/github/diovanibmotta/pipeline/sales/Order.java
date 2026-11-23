package com.github.diovanibmotta.pipeline.sales;

import java.math.BigDecimal;

public record Order(String id, String customer, String country, int quantity, BigDecimal unitPrice, String currency) {
}
