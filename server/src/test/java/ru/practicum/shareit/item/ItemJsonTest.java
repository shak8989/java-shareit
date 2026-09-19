package ru.practicum.shareit.item;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemBookingDto;
import ru.practicum.shareit.item.dto.ItemDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ItemJsonTest {
    @Autowired
    private JacksonTester<ItemDto> json;
    @Autowired
    private JacksonTester<CommentCreateDto> commentJson;

    @Test
    void preservesRequestBookingsAndCommentsWithoutExposingEntities() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 1, 2, 10, 30);
        ItemDto item = ItemDto.builder().id(3L).name("Drill").description("Cordless").available(true)
                .requestId(4L).lastBooking(new ItemBookingDto(5L, 6L, start))
                .nextBooking(new ItemBookingDto(7L, 8L, start.plusDays(1)))
                .comments(List.of(new CommentDto(9L, "Works well", "Alice", start.plusHours(1)))).build();

        var result = json.write(item);

        assertThat(result).extractingJsonPathNumberValue("$.id").isEqualTo(3);
        assertThat(result).extractingJsonPathStringValue("$.name").isEqualTo("Drill");
        assertThat(result).extractingJsonPathStringValue("$.description").isEqualTo("Cordless");
        assertThat(result).extractingJsonPathBooleanValue("$.available").isTrue();
        assertThat(result).extractingJsonPathNumberValue("$.requestId").isEqualTo(4);
        assertThat(result).extractingJsonPathNumberValue("$.lastBooking.id").isEqualTo(5);
        assertThat(result).extractingJsonPathNumberValue("$.lastBooking.bookerId").isEqualTo(6);
        assertThat(result).extractingJsonPathStringValue("$.lastBooking.start").isEqualTo("2030-01-02T10:30:00");
        assertThat(result).extractingJsonPathNumberValue("$.nextBooking.id").isEqualTo(7);
        assertThat(result).extractingJsonPathNumberValue("$.nextBooking.bookerId").isEqualTo(8);
        assertThat(result).extractingJsonPathStringValue("$.nextBooking.start").isEqualTo("2030-01-03T10:30:00");
        assertThat(result).extractingJsonPathNumberValue("$.comments[0].id").isEqualTo(9);
        assertThat(result).extractingJsonPathStringValue("$.comments[0].text").isEqualTo("Works well");
        assertThat(result).extractingJsonPathStringValue("$.comments[0].authorName").isEqualTo("Alice");
        assertThat(result).extractingJsonPathStringValue("$.comments[0].created").isEqualTo("2030-01-02T11:30:00");
        assertThat(result).doesNotHaveJsonPath("$.owner");
        assertThat(result).doesNotHaveJsonPath("$.request");
        assertThat(result).doesNotHaveJsonPath("$.comments[0].author");
        assertThat(result).doesNotHaveJsonPath("$.comments[0].item");
        assertThat(json.parseObject(result.getJson())).isEqualTo(item);
    }

    @Test
    void readsItemCreationWithRequestIdAndDefaultsCommentsToEmptyArray() throws Exception {
        ItemDto item = json.parseObject(
                "{\"name\":\"Drill\",\"description\":\"Cordless\",\"available\":true,\"requestId\":4}");

        assertThat(item.getId()).isNull();
        assertThat(item.getName()).isEqualTo("Drill");
        assertThat(item.getDescription()).isEqualTo("Cordless");
        assertThat(item.getAvailable()).isTrue();
        assertThat(item.getRequestId()).isEqualTo(4L);
        assertThat(item.getComments()).isEmpty();
        assertThat(json.write(item)).extractingJsonPathArrayValue("$.comments").isEmpty();
    }

    @Test
    void readsFalseAvailabilityForPartialUpdateAndLeavesOmittedFieldsNull() throws Exception {
        ItemDto item = json.parseObject("{\"available\":false}");

        assertThat(item.getAvailable()).isFalse();
        assertThat(item.getName()).isNull();
        assertThat(item.getDescription()).isNull();
        assertThat(item.getRequestId()).isNull();
    }

    @Test
    void readsAndWritesCommentTextWithEscapedCharacters() throws Exception {
        CommentCreateDto comment = new CommentCreateDto("Works \"well\"\nThank you");

        var result = commentJson.write(comment);

        assertThat(result).extractingJsonPathStringValue("$.text").isEqualTo(comment.getText());
        assertThat(commentJson.parseObject(result.getJson())).isEqualTo(comment);
    }
}
