package com.github.diovanibmotta.servicebased.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void createThenGet() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Notebook\",\"priceCents\":450000,\"stock\":7}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock").value(7));
        mvc.perform(get("/products")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Notebook"));
    }

    @Test
    void validationAndNotFound() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"priceCents\":0,\"stock\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/products/999999")).andExpect(status().isNotFound());
    }
}
