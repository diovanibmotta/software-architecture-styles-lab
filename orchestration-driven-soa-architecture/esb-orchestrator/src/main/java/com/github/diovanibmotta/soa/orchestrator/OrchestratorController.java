package com.github.diovanibmotta.soa.orchestrator;

import com.github.diovanibmotta.soa.contracts.Canonical.OrderRecord;
import com.github.diovanibmotta.soa.contracts.Canonical.OrderRequest;
import com.github.diovanibmotta.soa.contracts.Canonical.WorkflowResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The single entry point of the enterprise for this process: channels talk to the orchestrator, never to services. */
@RestController
class OrchestratorController {

    record PlaceOrderRequest(@NotBlank String customerId, @NotBlank String sku,
                             @Min(1) @Max(10) int quantity, @Min(1) long unitPriceCents) {}

    private final OrderToCashWorkflow workflow;

    OrchestratorController(OrderToCashWorkflow workflow) {
        this.workflow = workflow;
    }

    /** Synchronous: 201 carries the FINAL outcome (CONFIRMED or CANCELLED) plus the trace of the executed steps. */
    @PostMapping("/orders")
    ResponseEntity<WorkflowResult> place(@Valid @RequestBody PlaceOrderRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                workflow.execute(new OrderRequest(r.customerId(), r.sku(), r.quantity(), r.unitPriceCents())));
    }

    @GetMapping("/orders/{id}")
    OrderRecord get(@PathVariable String id) {
        return workflow.find(id);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<Map<String, Object>> unavailable(ServiceUnavailableException e) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("error", e.getMessage());
        body.put("service", e.service());
        body.put("orderId", e.orderId());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }
}
