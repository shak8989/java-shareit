package ru.practicum.shareit.booking;

import ru.practicum.shareit.booking.dto.BookingCreateDto;
import java.time.LocalDateTime;
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

@WebMvcTest(BookingController.class)
class BookingControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private BookingClient client;

    @Test
    void createsBooking() throws Exception {
        when(client.create(eq(2L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 5, "status", "WAITING")));
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        LocalDateTime end = start.plusDays(1);
        mvc.perform(post("/bookings").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":3,\"start\":\"" + start + "\",\"end\":\"" + end + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING"));
        verify(client).create(2L, new BookingCreateDto(3L, start, end));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"start\":\"2099-01-01T10:00:00\",\"end\":\"2099-01-02T10:00:00\"}",
            "{\"itemId\":3,\"end\":\"2099-01-02T10:00:00\"}",
            "{\"itemId\":3,\"start\":\"2099-01-01T10:00:00\"}",
            "{\"itemId\":3,\"start\":\"2000-01-01T10:00:00\",\"end\":\"2099-01-02T10:00:00\"}",
            "{\"itemId\":3,\"start\":\"2099-01-02T10:00:00\",\"end\":\"2099-01-01T10:00:00\"}",
            "{\"itemId\":3,\"start\":\"2099-01-01T10:00:00\",\"end\":\"2099-01-01T10:00:00\"}",
            "{\"itemId\":3,\"start\":\"wrong\",\"end\":\"2099-01-02T10:00:00\"}"})
    void rejectsInvalidBookingWithoutCallingServer(String body) throws Exception {
        mvc.perform(post("/bookings").header("X-Sharer-User-Id", 2).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void approvesBooking() throws Exception {
        when(client.approve(1L, 5L, true)).thenReturn(ResponseEntity.ok(Map.of("status", "APPROVED")));
        mvc.perform(patch("/bookings/5").header("X-Sharer-User-Id", 1).param("approved", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        verify(client).approve(1L, 5L, true);
    }

    @Test
    void getsBooking() throws Exception {
        when(client.getById(2L, 5L)).thenReturn(ResponseEntity.ok(Map.of("id", 5)));
        mvc.perform(get("/bookings/5").header("X-Sharer-User-Id", 2))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void bookerListDefaultsToAll() throws Exception {
        when(client.getByBooker(2L, BookingState.ALL)).thenReturn(ResponseEntity.ok(List.of()));
        mvc.perform(get("/bookings").header("X-Sharer-User-Id", 2)).andExpect(status().isOk());
        verify(client).getByBooker(2L, BookingState.ALL);
    }

    @Test
    void ownerListUsesState() throws Exception {
        when(client.getByOwner(1L, BookingState.FUTURE)).thenReturn(ResponseEntity.ok(List.of()));
        mvc.perform(get("/bookings/owner").header("X-Sharer-User-Id", 1).param("state", "FUTURE"))
                .andExpect(status().isOk());
        verify(client).getByOwner(1L, BookingState.FUTURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", "/bookings/owner"})
    void rejectsUnknownState(String path) throws Exception {
        mvc.perform(get(path).header("X-Sharer-User-Id", 2).param("state", "UNKNOWN"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Unknown state: UNKNOWN"));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMissingApprovalParameter() throws Exception {
        mvc.perform(patch("/bookings/5").header("X-Sharer-User-Id", 1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

}
