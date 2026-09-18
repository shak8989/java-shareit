package ru.practicum.shareit.user;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.shareit.user.dto.UserDto;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public UserDto create(@RequestBody UserDto userDto) {
        log.info("Creating user");
        return userService.create(userDto);
    }

    @PatchMapping("/{userId}")
    public UserDto update(@PathVariable long userId, @RequestBody UserDto userDto) {
        log.info("Updating user {}", userId);
        return userService.update(userId, userDto);
    }

    @GetMapping("/{userId}")
    public UserDto getById(@PathVariable long userId) {
        log.info("Getting user {}", userId);
        return userService.getById(userId);
    }

    @GetMapping
    public List<UserDto> getAll() {
        log.info("Getting all users");
        return userService.getAll();
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable long userId) {
        log.info("Deleting user {}", userId);
        userService.delete(userId);
    }
}
