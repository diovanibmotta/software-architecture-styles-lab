package com.github.diovanibmotta.microservices.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ItemControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;

    @Test
    void createdItemCanBeReadBack() throws Exception {
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Burger\",\"priceCents\":3500}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());
        long id = items.findAll().iterator().next().id();

        mvc.perform(get("/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Burger"))
                .andExpect(jsonPath("$.priceCents").value(3500));
    }

    @Test
    void unknownItemIs404() throws Exception {
        mvc.perform(get("/items/{id}", 987654)).andExpect(status().isNotFound());
    }

    @Test
    void invalidItemIs400() throws Exception {
        mvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"priceCents\":0}"))
                .andExpect(status().isBadRequest());
    }
}
