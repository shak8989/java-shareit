package ru.practicum.shareit.request;

import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemRequestController.class)
class ItemRequestControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private ItemRequestClient client;

    @Test
    void createsValidRequest() throws Exception {
        when(client.create(eq(1L), any())).thenReturn(ResponseEntity.ok(
                Map.of("id", 10, "description", "Need a drill", "created", "2026-09-18T10:00:00", "items", List.of())));
        mvc.perform(post("/requests").header("X-Sharer-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Need a drill\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10)).andExpect(jsonPath("$.items").isEmpty());
        verify(client).create(1L, new ItemRequestCreateDto("Need a drill"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"description\":null}", "{\"description\":\"\"}", "{\"description\":\"   \"}"})
    void invalidDescriptionDoesNotReachServer(String body) throws Exception {
        mvc.perform(post("/requests").header("X-Sharer-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        verifyNoInteractions(client);
    }

    @Test
    void ownRequests() throws Exception {
        when(client.getOwnRequests(1L)).thenReturn(ResponseEntity.ok(List.of(Map.of("id", 10))));
        mvc.perform(get("/requests").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        verify(client).getOwnRequests(1L);
    }

    @Test
    void otherRequests() throws Exception {
        when(client.getAllOtherRequests(1L)).thenReturn(ResponseEntity.ok(List.of(Map.of("id", 10))));
        mvc.perform(get("/requests/all").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        verify(client).getAllOtherRequests(1L);
    }

    @Test
    void requestById() throws Exception {
        when(client.getById(1L, 10L)).thenReturn(ResponseEntity.ok(Map.of("id", 10)));
        mvc.perform(get("/requests/10").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10));
        verify(client).getById(1L, 10L);
    }

    @Test
    void serverNotFoundPassesThrough() throws Exception {
        when(client.getById(1L, 99L)).thenReturn(ResponseEntity.status(404).body(Map.of("error", "Request not found")));
        mvc.perform(get("/requests/99").header("X-Sharer-User-Id", 1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Request not found"));
    }

    @Test
    void missingHeaderIsBadRequest() throws Exception {
        mvc.perform(get("/requests")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void serverConnectionFailureReturnsBadGateway() throws Exception {
        when(client.getOwnRequests(1L)).thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(get("/requests").header("X-Sharer-User-Id", 1))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ShareIt server is unavailable"));
        verify(client).getOwnRequests(1L);
    }

}
