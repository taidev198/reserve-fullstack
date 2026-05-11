package com.cmc.warehouse.api;

import com.cmc.warehouse.BaseIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationControllerTest extends BaseIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private String createReservation(String orderId, String sku, int quantity) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "orderId", orderId,
                "items", List.of(Map.of("sku", sku, "quantity", quantity))
        ));
    }

    @Test
    void create_reservation_returns_201_with_view() throws Exception {
        String body = createReservation("ORD-CTRL-1", "B200", 2);

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.canConfirm").value(true))
                .andExpect(jsonPath("$.data.canCancel").value(true))
                .andExpect(jsonPath("$.data.items[0].sku").value("B200"));
    }

    @Test
    void validation_error_returns_structured_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "orderId", "",
                "items", List.of()
        ));

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details").isArray())
                .andExpect(jsonPath("$.url").value("/reservations"));
    }

    @Test
    void unknown_sku_returns_404() throws Exception {
        String body = createReservation("ORD-CTRL-2", "DOES-NOT-EXIST", 1);

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.data.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.url").value("/reservations"));
    }

    @Test
    void duplicate_active_order_id_returns_409() throws Exception {
        String first = createReservation("ORD-CTRL-IDEM", "B200", 2);
        String second = createReservation("ORD-CTRL-IDEM", "B200", 3);

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("DUPLICATE_ACTIVE_RESERVATION"))
                .andExpect(jsonPath("$.url").value("/reservations"));
    }

    @Test
    void create_replayed_request_returns_200_with_idempotent_header() throws Exception {
        String body = createReservation("ORD-CTRL-REPLAY", "A100", 2);

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Reservation-Idempotent-Replay", "false"));

        mockMvc().perform(post("/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Reservation-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.data.orderId").value("ORD-CTRL-REPLAY"));
    }

    @Test
    void confirm_and_get_endpoints_return_confirmed_reservation() throws Exception {
        String body = createReservation("ORD-CTRL-CONFIRM", "B200", 1);
        String createResponse = mockMvc().perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(createResponse).path("data").get("id").asLong();

        mockMvc().perform(post("/reservations/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.canConfirm").value(false))
                .andExpect(jsonPath("$.data.canCancel").value(false));

        mockMvc().perform(get("/reservations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void cancel_endpoint_returns_cancelled_reservation() throws Exception {
        String body = createReservation("ORD-CTRL-CANCEL", "C300", 2);
        String createResponse = mockMvc().perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(createResponse).path("data").get("id").asLong();

        mockMvc().perform(post("/reservations/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.canConfirm").value(false))
                .andExpect(jsonPath("$.data.canCancel").value(false));
    }

    @Test
    void get_unknown_reservation_returns_404() throws Exception {
        mockMvc().perform(get("/reservations/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("RESERVATION_NOT_FOUND"))
                .andExpect(jsonPath("$.url").value("/reservations/999999"));
    }

    @Test
    void list_reservations_returns_paged_response() throws Exception {
        mockMvc().perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReservation("ORD-CTRL-LIST-1", "A100", 1)))
                .andExpect(status().isCreated());
        mockMvc().perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createReservation("ORD-CTRL-LIST-2", "B200", 1)))
                .andExpect(status().isCreated());

        mockMvc().perform(get("/reservations?page=0&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void inventory_endpoint_lists_seeded_skus_with_pagination_metadata() throws Exception {
        mockMvc().perform(get("/inventory?page=0&size=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.content[?(@.sku == 'A100')]").exists());
    }
}
