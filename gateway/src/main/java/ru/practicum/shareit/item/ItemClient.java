package ru.practicum.shareit.item;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.DefaultUriBuilderFactory;
import ru.practicum.shareit.client.BaseClient;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.ItemUpdateDto;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import java.util.Map;

@Service
public class ItemClient extends BaseClient {
    private static final String API_PREFIX = "/items";

    public ItemClient(@Value("${shareit-server.url}") String serverUrl, RestTemplateBuilder builder) {
        super(builder
                .uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl + API_PREFIX))
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory())
                .build());
    }

    public ResponseEntity<Object> create(long userId, ItemDto dto) {
        return post("", userId, dto);
    }

    public ResponseEntity<Object> update(long userId, long itemId, ItemUpdateDto dto) {
        return patch("/" + itemId, userId, dto);
    }

    public ResponseEntity<Object> getById(long userId, long itemId) {
        return get("/" + itemId, userId);
    }

    public ResponseEntity<Object> getByOwner(long userId) {
        return get("", userId);
    }

    public ResponseEntity<Object> search(Long userId, String text) {
        return get("/search?text={text}", userId, Map.of("text", text));
    }

    public ResponseEntity<Object> addComment(long userId, long itemId, CommentCreateDto dto) {
        return post("/" + itemId + "/comment", userId, dto);
    }
}
