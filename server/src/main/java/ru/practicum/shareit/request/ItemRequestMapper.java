package ru.practicum.shareit.request;

import lombok.experimental.UtilityClass;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.ItemRequestItemDto;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;
import java.util.List;

@UtilityClass
public class ItemRequestMapper {
    public ItemRequest toModel(ItemRequestCreateDto dto, User requestor, LocalDateTime created) {
        return ItemRequest.builder()
                .description(dto.getDescription())
                .requestor(requestor)
                .created(created)
                .build();
    }

    public ItemRequestDto toDto(ItemRequest request, List<Item> items) {
        return new ItemRequestDto(request.getId(), request.getDescription(), request.getCreated(),
                items.stream().map(ItemRequestMapper::toItemDto).toList());
    }

    private ItemRequestItemDto toItemDto(Item item) {
        return new ItemRequestItemDto(item.getId(), item.getName(), item.getOwner().getId());
    }
}
