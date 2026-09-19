package ru.practicum.shareit.item;

import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import ru.practicum.shareit.item.dto.ItemBookingDto;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
class ItemControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private ItemService service;

    private final ItemDto dto = ItemDto.builder().id(3L).name("Drill").description("Cordless")
            .available(true).requestId(10L).build();

    @Test
    void createOnRequest() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(dto);
        mvc.perform(post("/items").header("X-Sharer-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Drill\",\"description\":\"Cordless\",\"available\":true,\"requestId\":10}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(10));
        verify(service).create(1L, ItemDto.builder().name("Drill").description("Cordless")
                .available(true).requestId(10L).build());
    }

    @Test
    void updateIsPartial() throws Exception {
        when(service.update(eq(1L), eq(3L), any())).thenReturn(dto);
        mvc.perform(patch("/items/3").header("X-Sharer-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}")).andExpect(status().isOk());
        verify(service).update(1L, 3L, ItemDto.builder().available(false).build());
    }

    @Test
    void getByIdIncludesBookingStartAndComments() throws Exception {
        dto.setLastBooking(new ItemBookingDto(7L, 2L, LocalDateTime.of(2026, 9, 17, 10, 0)));
        dto.setComments(List.of(new CommentDto(8L, "Good", "Booker", LocalDateTime.of(2026, 9, 18, 10, 0))));
        when(service.getById(1L, 3L)).thenReturn(dto);
        mvc.perform(get("/items/3").header("X-Sharer-User-Id", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lastBooking.start").value("2026-09-17T10:00:00"))
                .andExpect(jsonPath("$.comments[0].text").value("Good"));
    }

    @Test
    void getByOwner() throws Exception {
        when(service.getByOwner(1L)).thenReturn(List.of(dto));
        mvc.perform(get("/items").header("X-Sharer-User-Id", 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void search() throws Exception {
        when(service.search("Drill")).thenReturn(List.of(dto));
        mvc.perform(get("/items/search").param("text", "Drill")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
        verify(service).search("Drill");
    }

    @Test
    void comment() throws Exception {
        when(service.addComment(eq(2L), eq(3L), any())).thenReturn(
                new CommentDto(8L, "Good", "Booker", LocalDateTime.now()));
        mvc.perform(post("/items/3/comment").header("X-Sharer-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Good\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorName").value("Booker"));
        verify(service).addComment(2L, 3L, new CommentCreateDto("Good"));
    }

    @Test
    void updateByNonOwner() throws Exception {
        when(service.update(eq(2L), eq(3L), any())).thenThrow(new ForbiddenException("Only owner"));
        mvc.perform(patch("/items/3").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changed\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only owner"));
    }

    @Test
    void missingItem() throws Exception {
        when(service.getById(1L, 99L)).thenThrow(new NotFoundException("Item not found"));
        mvc.perform(get("/items/99").header("X-Sharer-User-Id", 1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void commentWithoutCompletedBooking() throws Exception {
        when(service.addComment(eq(2L), eq(3L), any())).thenThrow(new ValidationException("Booking required"));
        mvc.perform(post("/items/3/comment").header("X-Sharer-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Good\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Booking required"));
    }

}
