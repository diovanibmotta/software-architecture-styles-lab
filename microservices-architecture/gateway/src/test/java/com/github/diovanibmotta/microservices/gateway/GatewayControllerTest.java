package com.github.diovanibmotta.microservices.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gateway.timeout-ms=500")
@AutoConfigureMockMvc
class GatewayControllerTest {

    static HttpServer upstream;
    static int deadPort;

    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.createContext("/echo", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] out = (ex.getRequestMethod() + "|" + ex.getRequestURI() + "|" + body).getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/plain");
                ex.sendResponseHeaders(201, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            upstream.createContext("/slow", ex -> {
                try {
                    Thread.sleep(1_500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                ex.sendResponseHeaders(200, -1);
                ex.close();
            });
            upstream.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());   // the slow handler must not block the others
            upstream.start();
            try (ServerSocket s = new ServerSocket(0)) {
                deadPort = s.getLocalPort();    // closed again: nobody listens here
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.catalog", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("gateway.routes.orders", () -> "http://127.0.0.1:" + deadPort);
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
    }

    @Autowired MockMvc mvc;

    @Test
    void forwardsMethodPathQueryBodyAndUpstreamStatus() throws Exception {
        mvc.perform(post("/api/catalog/echo?x=1").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("POST|/echo?x=1|{\"a\":1}"));
    }

    @Test
    void forwardsGet() throws Exception {
        mvc.perform(get("/api/catalog/echo")).andExpect(status().isCreated()).andExpect(content().string("GET|/echo|"));
    }

    @Test
    void unknownRouteIs404() throws Exception {
        mvc.perform(get("/api/unknown/x")).andExpect(status().isNotFound());
    }

    @Test
    void deadUpstreamIs502() throws Exception {
        mvc.perform(get("/api/orders/1")).andExpect(status().isBadGateway());
    }

    @Test
    void slowUpstreamIs504() throws Exception {
        mvc.perform(get("/api/catalog/slow")).andExpect(status().isGatewayTimeout());
    }
}
