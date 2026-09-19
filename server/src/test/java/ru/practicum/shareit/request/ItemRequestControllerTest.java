package ru.practicum.shareit.request;

import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.ItemRequestItemDto;
import ru.practicum.shareit.exception.NotFoundException;
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

@WebMvcTest(ItemRequestController.class)
class ItemRequestControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private ItemRequestService service;

    private final ItemRequestDto dto = new ItemRequestDto(10L, "Need a drill",
            LocalDateTime.of(2026, 9, 18, 10, 0), List.of(new ItemRequestItemDto(5L, "Drill", 2L)));

    @Test
    void create() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(dto);
        mvc.perform(post("/requests").header("X-Sharer-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Need a drill\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.created").value("2026-09-18T10:00:00"));
        verify(service).create(1L, new ItemRequestCreateDto("Need a drill"));
    }

    @Test
    void ownRequests() throws Exception {
        when(service.getOwnRequests(1L)).thenReturn(List.of(dto));
        mvc.perform(get("/requests").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].ownerId").value(2));
        verify(service).getOwnRequests(1L);
    }

    @Test
    void otherRequests() throws Exception {
        when(service.getAllOtherRequests(1L)).thenReturn(List.of(dto));
        mvc.perform(get("/requests/all").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        verify(service).getAllOtherRequests(1L);
    }

    @Test
    void getById() throws Exception {
        when(service.getById(1L, 10L)).thenReturn(dto);
        mvc.perform(get("/requests/10").header("X-Sharer-User-Id", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(5));
        verify(service).getById(1L, 10L);
    }

    @Test
    void missingRequest() throws Exception {
        when(service.getById(1L, 99L)).thenThrow(new NotFoundException("Request not found"));
        mvc.perform(get("/requests/99").header("X-Sharer-User-Id", 1))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Request not found"));
    }

    @Test
    void missingHeader() throws Exception {
        mvc.perform(get("/requests")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(service);
    }

}
