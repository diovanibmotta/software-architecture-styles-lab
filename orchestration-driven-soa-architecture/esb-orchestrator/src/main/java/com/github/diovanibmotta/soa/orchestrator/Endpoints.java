package com.github.diovanibmotta.soa.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The service registry of this lab: where each business service lives. Only the orchestrator knows these URLs. */
@ConfigurationProperties("soa.endpoints")
public record Endpoints(String customer, String inventory, String billing, String order) {}
