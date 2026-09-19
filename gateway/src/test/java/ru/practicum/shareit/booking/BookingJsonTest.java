package ru.practicum.shareit.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.booking.dto.BookingCreateDto;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class BookingJsonTest {
    @Autowired
    private JacksonTester<BookingCreateDto> json;

    @Test
    void parsesIsoDatesAndKeepsValidationPropertyOutOfRequest() throws Exception {
        BookingCreateDto dto = json.parseObject(
                "{\"itemId\":3,\"start\":\"2030-01-01T10:30:00\",\"end\":\"2030-01-02T10:30:00\"}");

        assertThat(dto.getStart()).isEqualTo(LocalDateTime.of(2030, 1, 1, 10, 30));
        assertThat(dto.getEnd()).isEqualTo(LocalDateTime.of(2030, 1, 2, 10, 30));
        var result = json.write(dto);
        assertThat(result).doesNotHaveJsonPath("$.endAfterStart");
        assertThat(result).extractingJsonPathStringValue("$.start").isEqualTo("2030-01-01T10:30:00");
        assertThat(json.parseObject(result.getJson())).isEqualTo(dto);
    }
}
