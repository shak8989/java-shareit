package ru.practicum.shareit.booking;

import ru.practicum.shareit.booking.dto.BookingCreateDto;
import ru.practicum.shareit.booking.dto.BookingDto;

import java.util.List;

public interface BookingService {
    BookingDto create(long userId, BookingCreateDto bookingDto);

    BookingDto approve(long userId, long bookingId, boolean approved);

    BookingDto getById(long userId, long bookingId);

    List<BookingDto> getByBooker(long userId, BookingState state);

    List<BookingDto> getByOwner(long userId, BookingState state);
}
