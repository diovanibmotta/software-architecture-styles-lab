package com.github.diovanibmotta.microservices.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * Minimal reverse proxy: {@code /api/{route}/rest} -> {@code routes[route]/rest}. Passes method, query string, body,
 * content type and the upstream status through. Upstream down -> 502, upstream slow -> 504. Production gateways add
 * authentication, rate limiting, per-route circuit breakers; this one only shows the routing responsibility.
 */
@RestController
class GatewayController {

    private static final String PREFIX = "/api/";

    private final Map<String, String> routes;
    private final RestClient client;

    GatewayController(GatewayProperties properties) {
        this.routes = properties.routes();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs())).build());
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @RequestMapping("/api/**")
    ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().substring(PREFIX.length());
        int slash = path.indexOf('/');
        String route = slash < 0 ? path : path.substring(0, slash);
        String rest = slash < 0 ? "" : path.substring(slash);
        String base = routes.get(route);
        if (base == null || rest.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "no route for /api/" + path);
        }
        String query = request.getQueryString();
        String target = base + rest + (query == null ? "" : "?" + query);
        byte[] body = request.getInputStream().readAllBytes();

        try {
            RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(request.getMethod())).uri(java.net.URI.create(target));
            if (request.getContentType() != null) {
                spec.header(HttpHeaders.CONTENT_TYPE, request.getContentType());
            }
            if (body.length > 0) {
                spec.body(body);
            }
            return spec.exchange((req, res) -> {
                ResponseEntity.BodyBuilder out = ResponseEntity.status(res.getStatusCode());
                MediaType type = res.getHeaders().getContentType();
                if (type != null) {
                    out.contentType(type);
                }
                return out.body(res.getBody().readAllBytes());
            });
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException || e.getCause() instanceof SocketTimeoutException;
            return error(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY, route + " service did not answer");
        }
    }

    private static ResponseEntity<byte[]> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(("{\"error\":\"" + message + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
