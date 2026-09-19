package ru.practicum.shareit.item;

import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemUpdateDto;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
class ItemControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private ItemClient client;

    @Test
    void createsItemOnRequest() throws Exception {
        when(client.create(eq(2L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 3, "requestId", 10)));
        mvc.perform(post("/items").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drill\",\"description\":\"Cordless\",\"available\":false,\"requestId\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(10));
        verify(client).create(2L, new ItemDto("Drill", "Cordless", false, 10L));
    }

    @Test
    void createsItemWithoutRequest() throws Exception {
        when(client.create(eq(2L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 3)));
        mvc.perform(post("/items").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drill\",\"description\":\"Cordless\",\"available\":true}"))
                .andExpect(status().isOk());
        verify(client).create(2L, new ItemDto("Drill", "Cordless", true, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"description\":\"Cordless\",\"available\":true,\"requestId\":10}",
            "{\"name\":\"Drill\",\"available\":true,\"requestId\":10}",
            "{\"name\":\"Drill\",\"description\":\"Cordless\",\"requestId\":10}",
            "{\"name\":\" \",\"description\":\"Cordless\",\"available\":true}",
            "{\"name\":\"Drill\",\"description\":\" \",\"available\":true}", "{}"})
    void rejectsInvalidItemEvenWithRequest(String body) throws Exception {
        mvc.perform(post("/items").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void patchIsPartial() throws Exception {
        when(client.update(eq(2L), eq(3L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 3)));
        mvc.perform(patch("/items/3").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}")).andExpect(status().isOk());
        verify(client).update(2L, 3L, new ItemUpdateDto(null, null, false));
    }

    @Test
    void patchRejectsBlankName() throws Exception {
        mvc.perform(patch("/items/3").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }

    @Test
    void getsItemWithCommentsAndBookings() throws Exception {
        when(client.getById(2L, 3L)).thenReturn(ResponseEntity.ok(Map.of("id", 3,
                "lastBooking", Map.of("start", "2026-09-18T10:00:00"), "comments", List.of(Map.of("text", "Good")))));
        mvc.perform(get("/items/3").header("X-Sharer-User-Id", 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastBooking.start").value("2026-09-18T10:00:00"))
                .andExpect(jsonPath("$.comments[0].text").value("Good"));
    }

    @Test
    void listsOwnerItems() throws Exception {
        when(client.getByOwner(2L)).thenReturn(ResponseEntity.ok(List.of(Map.of("id", 3))));
        mvc.perform(get("/items").header("X-Sharer-User-Id", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void searchesItems() throws Exception {
        when(client.search(2L, "Drill")).thenReturn(ResponseEntity.ok(List.of(Map.of("id", 3))));
        mvc.perform(get("/items/search").header("X-Sharer-User-Id", 2).param("text", "Drill"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3));
        verify(client).search(2L, "Drill");
    }

    @Test
    void addsComment() throws Exception {
        when(client.addComment(eq(2L), eq(3L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 8, "text", "Good")));
        mvc.perform(post("/items/3/comment").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Good\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Good"));
        verify(client).addComment(2L, 3L, new CommentCreateDto("Good"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":\" \"}", "{\"text\":\"\"}"})
    void rejectsBlankComment(String body) throws Exception {
        mvc.perform(post("/items/3/comment").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void preservesForbidden() throws Exception {
        when(client.update(eq(2L), eq(3L), any())).thenReturn(ResponseEntity.status(403).body(Map.of("error", "Only owner")));
        mvc.perform(patch("/items/3").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only owner"));
    }

}
