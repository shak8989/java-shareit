package ru.practicum.shareit.request;

import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;

import java.util.List;

public interface ItemRequestService {
    ItemRequestDto create(long userId, ItemRequestCreateDto dto);

    List<ItemRequestDto> getOwnRequests(long userId);

    List<ItemRequestDto> getAllOtherRequests(long userId);

    ItemRequestDto getById(long userId, long requestId);
}
