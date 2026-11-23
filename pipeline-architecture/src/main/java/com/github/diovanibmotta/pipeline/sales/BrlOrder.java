package com.github.diovanibmotta.pipeline.sales;

import java.math.BigDecimal;

public record BrlOrder(String id, String customer, String country, BigDecimal totalBrl) {
}
