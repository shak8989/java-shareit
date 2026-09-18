package ru.practicum.shareit.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.shareit.booking.dto.BookingCreateDto;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.exception.ValidationException;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping(path = "/bookings")
@RequiredArgsConstructor
public class BookingController {
    private static final String USER_ID_HEADER = "X-Sharer-User-Id";
    private final BookingService bookingService;

    @PostMapping
    public BookingDto create(@RequestHeader(USER_ID_HEADER) long userId,
                             @RequestBody BookingCreateDto bookingDto) {
        log.info("Creating booking by user {}", userId);
        return bookingService.create(userId, bookingDto);
    }

    @PatchMapping("/{bookingId}")
    public BookingDto approve(@RequestHeader(USER_ID_HEADER) long userId,
                              @PathVariable long bookingId,
                              @RequestParam boolean approved) {
        log.info("Changing booking {} status by user {}", bookingId, userId);
        return bookingService.approve(userId, bookingId, approved);
    }

    @GetMapping("/{bookingId}")
    public BookingDto getById(@RequestHeader(USER_ID_HEADER) long userId,
                              @PathVariable long bookingId) {
        log.info("Getting booking {} by user {}", bookingId, userId);
        return bookingService.getById(userId, bookingId);
    }

    @GetMapping
    public List<BookingDto> getByBooker(@RequestHeader(USER_ID_HEADER) long userId,
                                        @RequestParam(defaultValue = "ALL") String state) {
        log.info("Getting bookings by user {}", userId);
        return bookingService.getByBooker(userId, parseState(state));
    }

    @GetMapping("/owner")
    public List<BookingDto> getByOwner(@RequestHeader(USER_ID_HEADER) long userId,
                                       @RequestParam(defaultValue = "ALL") String state) {
        log.info("Getting owner bookings by user {}", userId);
        return bookingService.getByOwner(userId, parseState(state));
    }

    private BookingState parseState(String state) {
        try {
            return BookingState.valueOf(state.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ValidationException("Unknown state: " + state);
        }
    }
}
