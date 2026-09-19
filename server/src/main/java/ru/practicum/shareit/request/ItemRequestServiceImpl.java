package ru.practicum.shareit.request;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.ItemRepository;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemRequestServiceImpl implements ItemRequestService {
    private final ItemRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public ItemRequestDto create(long userId, ItemRequestCreateDto dto) {
        User requestor = findUser(userId);
        ItemRequest request = ItemRequestMapper.toModel(dto, requestor, LocalDateTime.now());
        return ItemRequestMapper.toDto(requestRepository.save(request), List.of());
    }

    @Override
    public List<ItemRequestDto> getOwnRequests(long userId) {
        findUser(userId);
        return toDtos(requestRepository.findByRequestorIdOrderByCreatedDesc(userId));
    }

    @Override
    public List<ItemRequestDto> getAllOtherRequests(long userId) {
        findUser(userId);
        return toDtos(requestRepository.findByRequestorIdNotOrderByCreatedDesc(userId));
    }

    @Override
    public ItemRequestDto getById(long userId, long requestId) {
        findUser(userId);
        ItemRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request with id " + requestId + " not found"));
        return toDtos(List.of(request)).getFirst();
    }

    private List<ItemRequestDto> toDtos(List<ItemRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<Long> requestIds = requests.stream().map(ItemRequest::getId).toList();
        Map<Long, List<Item>> itemsByRequest = itemRepository.findByRequestIdInOrderById(requestIds).stream()
                .collect(Collectors.groupingBy(item -> item.getRequest().getId()));
        return requests.stream()
                .map(request -> ItemRequestMapper.toDto(request,
                        itemsByRequest.getOrDefault(request.getId(), List.of())))
                .toList();
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User with id " + userId + " not found"));
    }
}
