package ru.practicum.shareit.item;

import ru.practicum.shareit.item.model.Item;

import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    Item save(Item item);

    Optional<Item> findById(long itemId);

    List<Item> findByOwnerId(long ownerId);

    List<Item> searchAvailable(String text);
}
