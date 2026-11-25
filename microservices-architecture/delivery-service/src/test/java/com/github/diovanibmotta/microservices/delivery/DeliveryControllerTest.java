package com.github.diovanibmotta.microservices.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "delivery.couriers=1")
@AutoConfigureMockMvc
class DeliveryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired DeliveryRepository deliveries;
    @MockitoBean OrderCallback orders;

    @BeforeEach
    void cleanDatabase() {
        deliveries.deleteAll();   // couriers are a shared resource: each test starts with all of them free
    }

    private void dispatch(long orderId, int http, String expectedStatus) throws Exception {
        mvc.perform(post("/deliveries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"address\":\"Rua A, 1\"}"))
                .andExpect(status().is(http))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    @Test
    void assignsCourierUntilTheyRunOutThenReportsNoCourierAndCompletionFreesOne() throws Exception {
        dispatch(1, 202, "ASSIGNED");
        dispatch(2, 202, "NO_COURIER");              // only one courier on shift
        verify(orders, atLeastOnce()).report(any());

        mvc.perform(post("/deliveries/{id}/complete", 1)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        dispatch(3, 202, "ASSIGNED");                // courier is free again
    }

    @Test
    void dispatchIsIdempotentAndResendsTheCallback() throws Exception {
        dispatch(10, 202, "ASSIGNED");
        dispatch(10, 202, "ASSIGNED");               // relay retry: same decision, no second courier consumed
        verify(orders, times(2)).report(any());
        mvc.perform(post("/deliveries/{id}/complete", 10)).andExpect(status().isOk());
    }

    @Test
    void failedCallbackAnswers503SoTheSenderRetries() throws Exception {
        doThrow(new RestClientException("order-service down")).when(orders).report(any());
        mvc.perform(post("/deliveries/{id}/complete", 99)).andExpect(status().isNotFound());
        mvc.perform(post("/deliveries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":20,\"address\":\"Rua B, 2\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void cannotCompleteAnUnassignedDelivery() throws Exception {
        dispatch(30, 202, "ASSIGNED");               // takes the only courier (others in this class may be free by now)
        dispatch(31, 202, "NO_COURIER");
        mvc.perform(post("/deliveries/{id}/complete", 31)).andExpect(status().isConflict());
    }
}
