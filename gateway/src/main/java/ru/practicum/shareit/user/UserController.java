package ru.practicum.shareit.user;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.dto.UserUpdateDto;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserClient client;

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody UserDto dto) {
        return client.create(dto);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Object> update(@PathVariable long userId, @Valid @RequestBody UserUpdateDto dto) {
        return client.update(userId, dto);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Object> getById(@PathVariable long userId) {
        return client.getById(userId);
    }

    @GetMapping
    public ResponseEntity<Object> getAll() {
        return client.getAll();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Object> delete(@PathVariable long userId) {
        return client.deleteById(userId);
    }
}
