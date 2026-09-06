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
import ru.practicum.shareit.user.User;
import ru.practicum.shareit.user.UserRepository;

import java.util.List;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;

    @Override
    @Transactional
    public ItemDto create(long userId, ItemDto itemDto) {
        User owner = findUser(userId);

        Item item = ItemMapper.toModel(itemDto);
        item.setOwner(owner);

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
    public ItemDto getById(long userId, long itemId) {
        Item item = findItem(itemId);
        return toViewDto(item, item.getOwner().getId().equals(userId));
    }

    @Override
    public List<ItemDto> getByOwner(long userId) {
        findUser(userId);

        return itemRepository.findByOwnerIdOrderById(userId).stream()
                .map(item -> toViewDto(item, true))
                .toList();
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
        if (commentDto == null || commentDto.getText() == null || commentDto.getText().isBlank()) {
            throw new ValidationException("Comment text must not be blank");
        }
        if (!bookingRepository.hasCompletedBooking(itemId, userId, BookingStatus.APPROVED,
                LocalDateTime.now())) {
            throw new ValidationException("Comment is allowed only after a completed booking");
        }
        Comment comment = Comment.builder()
                .text(commentDto.getText())
                .item(item)
                .author(author)
                .created(LocalDateTime.now())
                .build();
        return toCommentDto(commentRepository.save(comment));
    }

    private ItemDto toViewDto(Item item, boolean includeBookings) {
        ItemDto dto = ItemMapper.toDto(item);
        dto.setComments(commentRepository.findByItemIdOrderByCreatedAsc(item.getId()).stream()
                .map(this::toCommentDto)
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
                .build();
    }

    private CommentDto toCommentDto(Comment comment) {
        return CommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .authorName(comment.getAuthor().getName())
                .created(comment.getCreated())
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

    private void requireNotBlank(String value, String field) {
        if (value.isBlank()) {
            throw new ValidationException(
                    "Item " + field + " must not be blank"
            );
        }
    }
}
