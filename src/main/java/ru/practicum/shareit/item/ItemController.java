package ru.practicum.shareit.item;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.validation.Create;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {
    private static final String USER_ID_HEADER = "X-Sharer-User-Id";
    private final ItemService itemService;

    @PostMapping
    public ItemDto create(@RequestHeader(USER_ID_HEADER) long userId,
                          @Validated(Create.class) @RequestBody ItemDto itemDto) {
        log.info("Creating item by user {}", userId);
        return itemService.create(userId, itemDto);
    }

    @PatchMapping("/{itemId}")
    public ItemDto update(@RequestHeader(USER_ID_HEADER) long userId,
                          @PathVariable long itemId,
                          @RequestBody ItemDto itemDto) {
        log.info("Updating item {} by user {}", itemId, userId);
        return itemService.update(userId, itemId, itemDto);
    }

    @GetMapping("/{itemId}")
    public ItemDto getById(@PathVariable long itemId) {
        log.info("Getting item {}", itemId);
        return itemService.getById(itemId);
    }

    @GetMapping
    public List<ItemDto> getByOwner(@RequestHeader(USER_ID_HEADER) long userId) {
        log.info("Getting items of user {}", userId);
        return itemService.getByOwner(userId);
    }

    @GetMapping("/search")
    public List<ItemDto> search(@RequestParam(defaultValue = "") String text) {
        log.info("Searching items by text '{}'", text);
        return itemService.search(text);
    }
}
