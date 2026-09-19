package ru.practicum.shareit.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;
import ru.practicum.shareit.user.dto.UserDto;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class UserJsonTest {
    @Autowired
    private JacksonTester<UserDto> json;

    @Test
    void serializesAndReadsUserFields() throws Exception {
        UserDto user = new UserDto(7L, "Alice", "alice@example.com");

        var result = json.write(user);

        assertThat(result).extractingJsonPathNumberValue("$.id").isEqualTo(7);
        assertThat(result).extractingJsonPathStringValue("$.name").isEqualTo("Alice");
        assertThat(result).extractingJsonPathStringValue("$.email").isEqualTo("alice@example.com");
        assertThat(json.parseObject(result.getJson())).isEqualTo(user);
    }

    @Test
    void readsNameOnlyUpdateWithoutInventingEmailOrId() throws Exception {
        assertThat(json.parseObject("{\"name\":\"Alice\"}"))
                .isEqualTo(new UserDto(null, "Alice", null));
    }

    @Test
    void readsEmailOnlyUpdateWithoutInventingNameOrId() throws Exception {
        assertThat(json.parseObject("{\"email\":\"alice@example.com\"}"))
                .isEqualTo(new UserDto(null, null, "alice@example.com"));
    }
}
