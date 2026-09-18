package ru.practicum.shareit.booking;

import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookingController.class)
class BookingControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private BookingService service;

    private final BookingDto dto = BookingDto.builder().id(5L).status(BookingStatus.WAITING).build();

    @Test
    void create() throws Exception {
        when(service.create(eq(2L), any())).thenReturn(dto);
        mvc.perform(post("/bookings").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":3,\"start\":\"2030-01-01T10:00:00\",\"end\":\"2030-01-02T10:00:00\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void approve() throws Exception {
        dto.setStatus(BookingStatus.APPROVED);
        when(service.approve(1L, 5L, true)).thenReturn(dto);
        mvc.perform(patch("/bookings/5").header("X-Sharer-User-Id", 1).param("approved", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        verify(service).approve(1L, 5L, true);
    }

    @Test
    void getById() throws Exception {
        when(service.getById(2L, 5L)).thenReturn(dto);
        mvc.perform(get("/bookings/5").header("X-Sharer-User-Id", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void bookerListDefaultsToAll() throws Exception {
        when(service.getByBooker(2L, BookingState.ALL)).thenReturn(List.of(dto));
        mvc.perform(get("/bookings").header("X-Sharer-User-Id", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(5));
        verify(service).getByBooker(2L, BookingState.ALL);
    }

    @Test
    void ownerListUsesState() throws Exception {
        when(service.getByOwner(1L, BookingState.WAITING)).thenReturn(List.of(dto));
        mvc.perform(get("/bookings/owner").header("X-Sharer-User-Id", 1).param("state", "WAITING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(5));
        verify(service).getByOwner(1L, BookingState.WAITING);
    }

    @Test
    void missingBooking() throws Exception {
        when(service.getById(2L, 99L)).thenThrow(new NotFoundException("Booking not found"));
        mvc.perform(get("/bookings/99").header("X-Sharer-User-Id", 2))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }

    @Test
    void forbiddenApproval() throws Exception {
        when(service.approve(2L, 5L, true)).thenThrow(new ForbiddenException("Only owner"));
        mvc.perform(patch("/bookings/5").header("X-Sharer-User-Id", 2).param("approved", "true"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Only owner"));
    }

    @Test
    void invalidState() throws Exception {
        mvc.perform(get("/bookings").header("X-Sharer-User-Id", 2).param("state", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Unknown state: UNKNOWN"));
        verifyNoInteractions(service);
    }

}
