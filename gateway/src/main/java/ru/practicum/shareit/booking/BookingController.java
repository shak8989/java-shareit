package ru.practicum.shareit.booking;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.shareit.booking.dto.BookingCreateDto;
import java.util.Locale;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {
    private static final String USER_ID_HEADER = "X-Sharer-User-Id";
    private final BookingClient client;

    @PostMapping
    public ResponseEntity<Object> create(@RequestHeader(USER_ID_HEADER) long userId,
                                         @Valid @RequestBody BookingCreateDto dto) {
        return client.create(userId, dto);
    }

    @PatchMapping("/{bookingId}")
    public ResponseEntity<Object> approve(@RequestHeader(USER_ID_HEADER) long userId, @PathVariable long bookingId,
                                          @RequestParam boolean approved) {
        return client.approve(userId, bookingId, approved);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Object> getById(@RequestHeader(USER_ID_HEADER) long userId, @PathVariable long bookingId) {
        return client.getById(userId, bookingId);
    }

    @GetMapping
    public ResponseEntity<Object> getByBooker(@RequestHeader(USER_ID_HEADER) long userId,
                                             @RequestParam(defaultValue = "ALL") String state) {
        return client.getByBooker(userId, parseState(state));
    }

    @GetMapping("/owner")
    public ResponseEntity<Object> getByOwner(@RequestHeader(USER_ID_HEADER) long userId,
                                            @RequestParam(defaultValue = "ALL") String state) {
        return client.getByOwner(userId, parseState(state));
    }

    private BookingState parseState(String state) {
        try {
            return BookingState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown state: " + state);
        }
    }
}
