package ru.practicum.shareit.item;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;

    @Override
    public ItemDto create(long userId, ItemDto itemDto) {
        User owner = findUser(userId);
        Item item = ItemMapper.toModel(itemDto);
        item.setOwner(owner);
        return ItemMapper.toDto(itemRepository.save(item));
    }

    @Override
    public ItemDto update(long userId, long itemId, ItemDto itemDto) {
        Item item = findItem(itemId);
        if (!item.getOwner().getId().equals(userId)) {
            throw new ForbiddenException("Only the owner can update item " + itemId);
        }
        if (itemDto.getName() != null) {
            requireNotBlank(itemDto.getName(), "name");
            item.setName(itemDto.getName());
        }
        if (itemDto.getDescription() != null) {
            requireNotBlank(itemDto.getDescription(), "description");
            item.setDescription(itemDto.getDescription());
        }
        if (itemDto.getAvailable() != null) {
            item.setAvailable(itemDto.getAvailable());
        }
        return ItemMapper.toDto(itemRepository.save(item));
    }

    @Override
    public ItemDto getById(long itemId) {
        return ItemMapper.toDto(findItem(itemId));
    }

    @Override
    public List<ItemDto> getByOwner(long userId) {
        findUser(userId);
        return itemRepository.findByOwnerId(userId).stream().map(ItemMapper::toDto).toList();
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return itemRepository.searchAvailable(text).stream().map(ItemMapper::toDto).toList();
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
    }

    private Item findItem(long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item with id " + itemId + " not found"));
    }

    private void requireNotBlank(String value, String field) {
        if (value.isBlank()) {
            throw new ValidationException("Item " + field + " must not be blank");
        }
    }
}
