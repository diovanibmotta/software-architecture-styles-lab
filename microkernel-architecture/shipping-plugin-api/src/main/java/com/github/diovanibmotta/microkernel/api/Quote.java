package com.github.diovanibmotta.microkernel.api;

import java.math.BigDecimal;

public record Quote(String carrier, String service, BigDecimal price, int deliveryDays) {}
