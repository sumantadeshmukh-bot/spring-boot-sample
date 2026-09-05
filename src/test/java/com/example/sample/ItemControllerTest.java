package com.example.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void helloEndpointReturnsGreeting() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello from Spring Boot!"));
    }

    @Test
    void createItemThenListIncludesIt() throws Exception {
        Item item = new Item("Widget", "A sample widget");

        mockMvc.perform(post("/api/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Widget"));

        mockMvc.perform(get("/api/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Widget')]").exists());
    }

    @Test
    void createItemWithBlankNameIsRejected() throws Exception {
        Item item = new Item("", "missing name");

        mockMvc.perform(post("/api/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownItemReturns404() throws Exception {
        mockMvc.perform(get("/api/items/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateThenDeleteItem() throws Exception {
        Item item = new Item("Gadget", "before update");
        MvcResult created = mockMvc.perform(post("/api/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        Item updated = new Item("Gadget v2", "after update");
        mockMvc.perform(put("/api/items/{id}", id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gadget v2"));

        mockMvc.perform(delete("/api/items/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/items/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchItemsFindsCaseInsensitiveMatch() throws Exception {
        Item item = new Item("Sprocket", "a small metal part");
        mockMvc.perform(post("/api/items")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/items/search").param("name", "sprock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Sprocket')]").exists());
    }

    @Test
    void searchItemsWithNoMatchReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/items/search").param("name", "no-such-item-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void searchItemsWithBlankOrMissingNameReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/items/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(get("/api/items/search").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
