package com.github.diovanibmotta.microservices.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("gateway")
record GatewayProperties(Map<String, String> routes, long timeoutMs) {
}
