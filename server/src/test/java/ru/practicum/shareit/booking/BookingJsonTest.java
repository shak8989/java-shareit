package ru.practicum.shareit.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.user.dto.UserDto;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class BookingJsonTest {
    @Autowired
    private JacksonTester<BookingDto> json;

    @Test
    void preservesDatesStatusAndNestedIdentifiers() throws Exception {
        BookingDto dto = BookingDto.builder().id(5L).start(LocalDateTime.of(2026, 9, 20, 12, 0))
                .end(LocalDateTime.of(2026, 9, 21, 12, 0)).status(BookingStatus.APPROVED)
                .booker(UserDto.builder().id(2L).name("Booker").email("b@test.ru").build())
                .item(ItemDto.builder().id(3L).name("Drill").available(true).build()).build();

        var result = json.write(dto);

        assertThat(result).extractingJsonPathStringValue("$.start").isEqualTo("2026-09-20T12:00:00");
        assertThat(result).extractingJsonPathStringValue("$.end").isEqualTo("2026-09-21T12:00:00");
        assertThat(result).extractingJsonPathStringValue("$.status").isEqualTo("APPROVED");
        assertThat(result).extractingJsonPathNumberValue("$.booker.id").isEqualTo(2);
        assertThat(result).extractingJsonPathNumberValue("$.item.id").isEqualTo(3);
        assertThat(json.parseObject(result.getJson())).isEqualTo(dto);
    }
}
