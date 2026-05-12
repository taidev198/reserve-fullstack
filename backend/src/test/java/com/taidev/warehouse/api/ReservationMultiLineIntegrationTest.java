package com.taidev.warehouse.api;

import com.taidev.warehouse.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API path for orders that reserve more than one SKU in a single request.
 */
class ReservationMultiLineIntegrationTest extends BaseIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void create_reservation_with_two_lines_returns_201_and_both_items() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderId", "ORD-MULTI-1",
                "items", List.of(
                        Map.of("sku", "A100", "quantity", 2),
                        Map.of("sku", "B200", "quantity", 3)
                )
        ));

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[?(@.sku == 'A100' && @.quantity == 2)]").exists())
                .andExpect(jsonPath("$.data.items[?(@.sku == 'B200' && @.quantity == 3)]").exists());
    }

    @Test
    void confirm_multi_line_reservation_succeeds() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderId", "ORD-MULTI-2",
                "items", List.of(
                        Map.of("sku", "C300", "quantity", 1),
                        Map.of("sku", "D400", "quantity", 1)
                )
        ));

        String createResponse = mockMvc().perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(createResponse).path("data").get("id").asLong();

        mockMvc().perform(post("/reservations/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }
}
