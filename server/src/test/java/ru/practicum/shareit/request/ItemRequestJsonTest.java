package ru.practicum.shareit.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.ItemRequestItemDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ItemRequestJsonTest {
    @Autowired
    private JacksonTester<ItemRequestDto> json;

    @Test
    void serializesDateAndNestedItemWithoutEntities() throws Exception {
        ItemRequestDto dto = new ItemRequestDto(1L, "Need a drill", LocalDateTime.of(2026, 9, 18, 12, 30),
                List.of(new ItemRequestItemDto(2L, "Drill", 3L)));
        var result = json.write(dto);

        assertThat(result).extractingJsonPathStringValue("$.created").isEqualTo("2026-09-18T12:30:00");
        assertThat(result).extractingJsonPathNumberValue("$.items[0].id").isEqualTo(2);
        assertThat(result).extractingJsonPathNumberValue("$.items[0].ownerId").isEqualTo(3);
        assertThat(result).doesNotHaveJsonPath("$.requestor");
        assertThat(json.parseObject(result.getJson())).isEqualTo(dto);
    }

    @Test
    void serializesEmptyItemsAsArray() throws Exception {
        var result = json.write(new ItemRequestDto(1L, "Need", LocalDateTime.now(), List.of()));
        assertThat(result).extractingJsonPathArrayValue("$.items").isEmpty();
    }
}
