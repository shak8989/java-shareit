package ru.practicum.shareit.item;

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
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemUpdateDto;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {
    private static final String USER_ID_HEADER = "X-Sharer-User-Id";
    private final ItemClient client;

    @PostMapping
    public ResponseEntity<Object> create(@RequestHeader(USER_ID_HEADER) long userId,
                                         @Valid @RequestBody ItemDto dto) {
        return client.create(userId, dto);
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<Object> update(@RequestHeader(USER_ID_HEADER) long userId, @PathVariable long itemId,
                                         @Valid @RequestBody ItemUpdateDto dto) {
        return client.update(userId, itemId, dto);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<Object> getById(@RequestHeader(value = USER_ID_HEADER, defaultValue = "-1") long userId,
                                          @PathVariable long itemId) {
        return client.getById(userId, itemId);
    }

    @GetMapping
    public ResponseEntity<Object> getByOwner(@RequestHeader(USER_ID_HEADER) long userId) {
        return client.getByOwner(userId);
    }

    @GetMapping("/search")
    public ResponseEntity<Object> search(@RequestHeader(value = USER_ID_HEADER, required = false) Long userId,
                                         @RequestParam(defaultValue = "") String text) {
        return client.search(userId, text);
    }

    @PostMapping("/{itemId}/comment")
    public ResponseEntity<Object> addComment(@RequestHeader(USER_ID_HEADER) long userId, @PathVariable long itemId,
                                             @Valid @RequestBody CommentCreateDto dto) {
        return client.addComment(userId, itemId, dto);
    }
}
