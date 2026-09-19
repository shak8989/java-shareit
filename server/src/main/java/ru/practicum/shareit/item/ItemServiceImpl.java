package ru.practicum.shareit.item;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingRepository;
import ru.practicum.shareit.booking.BookingStatus;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemBookingDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.request.ItemRequestRepository;
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;
    private final ItemRequestRepository requestRepository;

    @Override
    @Transactional
    public ItemDto create(long userId, ItemDto itemDto) {
        User owner = findUser(userId);

        ItemRequest request = null;
        if (itemDto.getRequestId() != null) {
            request = requestRepository.findById(itemDto.getRequestId())
                    .orElseThrow(() -> new NotFoundException("Request with id " + itemDto.getRequestId() + " not found"));
        }
        Item item = ItemMapper.toModel(itemDto, owner, request);

        return ItemMapper.toDto(itemRepository.save(item));
    }

    @Override
    @Transactional
    public ItemDto update(long userId, long itemId, ItemDto itemDto) {
        Item item = findItem(itemId);

        if (!item.getOwner().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Only the owner can update item " + itemId
            );
        }

        if (itemDto.getName() != null) {
            item.setName(itemDto.getName());
        }

        if (itemDto.getDescription() != null) {
            item.setDescription(itemDto.getDescription());
        }

        if (itemDto.getAvailable() != null) {
            item.setAvailable(itemDto.getAvailable());
        }

        return ItemMapper.toDto(itemRepository.save(item));
    }

    @Override
    public ItemDto getById(long userId, long itemId) {
        Item item = findItem(itemId);
        return toViewDto(item, item.getOwner().getId().equals(userId));
    }

    @Override
    public List<ItemDto> getByOwner(long userId) {
        findUser(userId);

        List<Item> items = itemRepository.findByOwnerIdOrderById(userId);
        if (items.isEmpty()) {
            return List.of();
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, List<Booking>> bookingsByItem = bookingRepository
                .findByItemIdInAndStatusOrderByStartAsc(itemIds, BookingStatus.APPROVED).stream()
                .collect(Collectors.groupingBy(booking -> booking.getItem().getId()));
        Map<Long, List<Comment>> commentsByItem = commentRepository
                .findByItemIdInOrderByCreatedAsc(itemIds).stream()
                .collect(Collectors.groupingBy(comment -> comment.getItem().getId()));
        LocalDateTime now = LocalDateTime.now();

        return items.stream().map(item -> {
            ItemDto dto = ItemMapper.toDto(item);
            dto.setComments(commentsByItem.getOrDefault(item.getId(), List.of()).stream()
                    .map(CommentMapper::toDto)
                    .toList());
            for (Booking booking : bookingsByItem.getOrDefault(item.getId(), List.of())) {
                if (!booking.getStart().isAfter(now)) {
                    dto.setLastBooking(toItemBookingDto(booking));
                } else {
                    dto.setNextBooking(toItemBookingDto(booking));
                    break;
                }
            }
            return dto;
        }).toList();
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return itemRepository.searchAvailable(text).stream()
                .map(item -> toViewDto(item, false))
                .toList();
    }

    @Override
    @Transactional
    public CommentDto addComment(long userId, long itemId, CommentCreateDto commentDto) {
        User author = findUser(userId);
        Item item = findItem(itemId);
        LocalDateTime now = LocalDateTime.now();
        if (!bookingRepository.hasCompletedBooking(itemId, userId, BookingStatus.APPROVED,
                now)) {
            throw new ValidationException("Comment is allowed only after a completed booking");
        }
        Comment comment = CommentMapper.toModel(commentDto, item, author, now);
        return CommentMapper.toDto(commentRepository.save(comment));
    }

    private ItemDto toViewDto(Item item, boolean includeBookings) {
        ItemDto dto = ItemMapper.toDto(item);
        dto.setComments(commentRepository.findByItemIdOrderByCreatedAsc(item.getId()).stream()
                .map(CommentMapper::toDto)
                .toList());
        if (includeBookings) {
            LocalDateTime now = LocalDateTime.now();
            bookingRepository.findFirstByItemIdAndStatusAndStartLessThanEqualOrderByStartDesc(
                            item.getId(), BookingStatus.APPROVED, now)
                    .ifPresent(booking -> dto.setLastBooking(toItemBookingDto(booking)));
            bookingRepository.findFirstByItemIdAndStatusAndStartAfterOrderByStartAsc(
                            item.getId(), BookingStatus.APPROVED, now)
                    .ifPresent(booking -> dto.setNextBooking(toItemBookingDto(booking)));
        }
        return dto;
    }

    private ItemBookingDto toItemBookingDto(Booking booking) {
        return ItemBookingDto.builder()
                .id(booking.getId())
                .bookerId(booking.getBooker().getId())
                .start(booking.getStart())
                .build();
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "User with id " + userId + " not found"
                        )
                );
    }

    private Item findItem(long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Item with id " + itemId + " not found"
                        )
                );
    }

}
