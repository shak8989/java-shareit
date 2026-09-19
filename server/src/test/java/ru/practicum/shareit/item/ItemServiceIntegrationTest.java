package ru.practicum.shareit.item;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingStatus;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemBookingDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@ActiveProfiles("test")
@Transactional
class ItemServiceIntegrationTest {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ItemService itemService;

    @Test
    void createsOrdinaryItemWithoutRequest() {
        User owner = persistUser("owner");

        ItemDto result = itemService.create(owner.getId(), ItemDto.builder().name("Drill")
                .description("Cordless drill").available(true).build());

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Drill");
        assertThat(result.getDescription()).isEqualTo("Cordless drill");
        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getRequestId()).isNull();
        assertThat(result.getComments()).isEmpty();
        flushAndClear();
        Item saved = entityManager.find(Item.class, result.getId());
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(saved.getName()).isEqualTo(result.getName());
        assertThat(saved.getDescription()).isEqualTo(result.getDescription());
        assertThat(saved.getAvailable()).isTrue();
        assertThat(saved.getRequest()).isNull();
    }

    @Test
    void createsItemLinkedToExistingRequest() {
        User owner = persistUser("owner");
        ItemRequest request = persistRequest(persistUser("requestor"));
        flushAndClear();

        ItemDto result = itemService.create(owner.getId(), ItemDto.builder().name("Drill")
                .description("Cordless drill").available(true).requestId(request.getId()).build());

        assertThat(result.getRequestId()).isEqualTo(request.getId());
        flushAndClear();
        Item saved = entityManager.find(Item.class, result.getId());
        assertThat(saved.getRequest().getId()).isEqualTo(request.getId());
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void cannotCreateItemForMissingRequest() {
        User owner = persistUser("owner");
        ItemDto input = ItemDto.builder().name("Drill").description("Cordless drill")
                .available(true).requestId(Long.MAX_VALUE).build();

        assertThatThrownBy(() -> itemService.create(owner.getId(), input))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Request");
    }

    @Test
    void cannotCreateItemForMissingOwner() {
        ItemDto input = ItemDto.builder().name("Drill").description("Cordless drill").available(true).build();

        assertThatThrownBy(() -> itemService.create(Long.MAX_VALUE, input))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
    }

    @ParameterizedTest
    @CsvSource({
            "Renamed,,,Renamed,Description,true",
            ",New description,,Drill,New description,true",
            ",,false,Drill,Description,false",
            "Renamed,New description,false,Renamed,New description,false",
            ",,,Drill,Description,true"
    })
    void ownerUpdatesSuppliedFieldsAndPreservesOthers(String name, String description, Boolean available,
                                                     String expectedName, String expectedDescription,
                                                     boolean expectedAvailable) {
        User owner = persistUser("owner");
        ItemRequest request = persistRequest(persistUser("requestor"));
        Item item = persistItem(owner, "Drill");
        item.setRequest(request);
        flushAndClear();

        ItemDto result = itemService.update(owner.getId(), item.getId(), ItemDto.builder()
                .name(name).description(description).available(available).build());

        assertThat(result.getId()).isEqualTo(item.getId());
        assertThat(result.getName()).isEqualTo(expectedName);
        assertThat(result.getDescription()).isEqualTo(expectedDescription);
        assertThat(result.getAvailable()).isEqualTo(expectedAvailable);
        assertThat(result.getRequestId()).isEqualTo(request.getId());
        flushAndClear();
        Item saved = entityManager.find(Item.class, item.getId());
        assertThat(saved.getName()).isEqualTo(expectedName);
        assertThat(saved.getDescription()).isEqualTo(expectedDescription);
        assertThat(saved.getAvailable()).isEqualTo(expectedAvailable);
        assertThat(saved.getRequest().getId()).isEqualTo(request.getId());
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void cannotUpdateMissingItem() {
        User owner = persistUser("owner");

        assertThatThrownBy(() -> itemService.update(owner.getId(), Long.MAX_VALUE,
                ItemDto.builder().name("Changed").build()))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Item");
    }

    @Test
    void nonOwnerCannotUpdateItem() {
        User owner = persistUser("owner");
        User stranger = persistUser("stranger");
        Item item = persistItem(owner, "Drill");
        flushAndClear();

        assertThatThrownBy(() -> itemService.update(stranger.getId(), item.getId(),
                ItemDto.builder().name("Changed").available(false).build()))
                .isInstanceOf(ForbiddenException.class);
        Item saved = entityManager.find(Item.class, item.getId());
        assertThat(saved.getName()).isEqualTo("Drill");
        assertThat(saved.getAvailable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void getByOwnerLoadsBookingsAndCommentsWithConstantQueryCount(int itemCount) {
        User owner = persistUser("owner");
        User otherOwner = persistUser("other-owner");
        persistItem(otherOwner, "Other owner's item");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Item> items = new ArrayList<>();
        List<Booking> lastBookings = new ArrayList<>();
        List<Booking> nextBookings = new ArrayList<>();
        List<User> bookers = new ArrayList<>();
        for (int index = 0; index < itemCount; index++) {
            Item item = persistItem(owner, "Item " + index);
            User booker = persistUser("booker-" + index);
            User secondBooker = persistUser("second-booker-" + index);
            items.add(item);
            bookers.add(booker);
            persistBooking(item, booker, now.minusDays(3), now.minusDays(2), BookingStatus.APPROVED);
            lastBookings.add(persistBooking(item, booker, now.minusHours(1), now.plusHours(1),
                    BookingStatus.APPROVED));
            persistBooking(item, secondBooker, now.minusMinutes(30), now.plusHours(2), BookingStatus.WAITING);
            persistBooking(item, secondBooker, now.minusMinutes(15), now.plusHours(2), BookingStatus.REJECTED);
            persistBooking(item, secondBooker, now.plusHours(2), now.plusHours(3), BookingStatus.WAITING);
            persistBooking(item, secondBooker, now.plusHours(3), now.plusHours(4), BookingStatus.REJECTED);
            nextBookings.add(persistBooking(item, secondBooker, now.plusDays(1), now.plusDays(2),
                    BookingStatus.APPROVED));
            persistBooking(item, booker, now.plusDays(3), now.plusDays(4), BookingStatus.APPROVED);
            persistComment(item, secondBooker, "Later", now.minusHours(1));
            persistComment(item, booker, "Earlier", now.minusDays(1));
        }
        Item emptyItem = persistItem(owner, "Without bookings and comments");
        Statistics statistics = resetStatistics();

        List<ItemDto> result = itemService.getByOwner(owner.getId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        assertThat(result).hasSize(itemCount + 1);
        for (int index = 0; index < itemCount; index++) {
            ItemDto dto = result.get(index);
            assertThat(dto.getId()).isEqualTo(items.get(index).getId());
            assertBooking(dto.getLastBooking(), lastBookings.get(index));
            assertBooking(dto.getNextBooking(), nextBookings.get(index));
            assertThat(dto.getComments()).extracting(CommentDto::getText).containsExactly("Earlier", "Later");
            CommentDto firstComment = dto.getComments().getFirst();
            assertThat(firstComment.getId()).isNotNull();
            assertThat(firstComment.getAuthorName()).isEqualTo(bookers.get(index).getName());
            assertThat(firstComment.getCreated()).isEqualTo(now.minusDays(1));
        }
        ItemDto emptyDto = result.getLast();
        assertThat(emptyDto.getId()).isEqualTo(emptyItem.getId());
        assertThat(emptyDto.getLastBooking()).isNull();
        assertThat(emptyDto.getNextBooking()).isNull();
        assertThat(emptyDto.getComments()).isEmpty();
    }

    @Test
    void getByOwnerWithoutItemsSkipsBookingAndCommentQueries() {
        User owner = persistUser("empty-owner");
        Statistics statistics = resetStatistics();

        assertThat(itemService.getByOwner(owner.getId())).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void getByIdIncludesCommentsAndShowsClosestApprovedBookingsOnlyToOwner(boolean asOwner) {
        User owner = persistUser("owner");
        User booker = persistUser("booker");
        Item item = persistItem(owner, "Drill");
        Item otherItem = persistItem(owner, "Other item");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        persistBooking(item, booker, now.minusDays(3), now.minusDays(2), BookingStatus.APPROVED);
        Booking last = persistBooking(item, booker, now.minusHours(1), now.plusHours(1), BookingStatus.APPROVED);
        persistBooking(item, booker, now.minusMinutes(30), now.plusHours(2), BookingStatus.WAITING);
        persistBooking(item, booker, now.minusMinutes(15), now.plusHours(2), BookingStatus.REJECTED);
        persistBooking(item, booker, now.plusHours(2), now.plusHours(3), BookingStatus.WAITING);
        persistBooking(item, booker, now.plusHours(3), now.plusHours(4), BookingStatus.REJECTED);
        Booking next = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.APPROVED);
        persistBooking(item, booker, now.plusDays(3), now.plusDays(4), BookingStatus.APPROVED);
        persistBooking(otherItem, booker, now.minusMinutes(5), now.plusMinutes(5), BookingStatus.APPROVED);
        persistBooking(otherItem, booker, now.plusMinutes(10), now.plusMinutes(20), BookingStatus.APPROVED);
        persistComment(item, booker, "Later", now.minusHours(1));
        persistComment(item, booker, "Earlier", now.minusDays(1));
        persistComment(otherItem, booker, "Other item comment", now.minusDays(2));
        flushAndClear();

        ItemDto result = itemService.getById(asOwner ? owner.getId() : booker.getId(), item.getId());

        assertThat(result.getId()).isEqualTo(item.getId());
        assertThat(result.getName()).isEqualTo("Drill");
        assertThat(result.getDescription()).isEqualTo("Description");
        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getComments()).extracting(CommentDto::getText).containsExactly("Earlier", "Later");
        assertThat(result.getComments()).allSatisfy(comment -> {
            assertThat(comment.getId()).isNotNull();
            assertThat(comment.getAuthorName()).isEqualTo(booker.getName());
        });
        assertThat(result.getComments().getFirst().getCreated()).isEqualTo(now.minusDays(1));
        if (asOwner) {
            assertBooking(result.getLastBooking(), last);
            assertBooking(result.getNextBooking(), next);
        } else {
            assertThat(result.getLastBooking()).isNull();
            assertThat(result.getNextBooking()).isNull();
        }
    }

    @Test
    void ownerCanReadItemWithoutBookingsOrComments() {
        User owner = persistUser("owner");
        Item item = persistItem(owner, "Drill");
        flushAndClear();

        ItemDto result = itemService.getById(owner.getId(), item.getId());

        assertThat(result.getId()).isEqualTo(item.getId());
        assertThat(result.getLastBooking()).isNull();
        assertThat(result.getNextBooking()).isNull();
        assertThat(result.getComments()).isEmpty();
    }

    @Test
    void cannotReadMissingItem() {
        User user = persistUser("viewer");

        assertThatThrownBy(() -> itemService.getById(user.getId(), Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Item");
    }

    @Test
    void cannotListItemsForMissingOwner() {
        assertThatThrownBy(() -> itemService.getByOwner(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
    }

    @ParameterizedTest
    @ValueSource(strings = {"drill", "DRILL", "rIl"})
    void searchMatchesNameAndDescriptionIgnoringCaseAndExcludesUnavailableItems(String query) {
        User owner = persistUser("owner");
        Item byName = persistItem(owner, "Cordless drill");
        Item byDescription = persistItem(owner, "Tool");
        byDescription.setDescription("Small drill for wood");
        Item unavailableByName = persistItem(owner, "Drill unavailable");
        unavailableByName.setAvailable(false);
        Item unavailableByDescription = persistItem(owner, "Unavailable tool");
        unavailableByDescription.setDescription("Another drill");
        unavailableByDescription.setAvailable(false);
        persistItem(owner, "Saw");
        flushAndClear();

        List<ItemDto> result = itemService.search(query);

        assertThat(result).extracting(ItemDto::getId).containsExactly(byName.getId(), byDescription.getId());
        assertThat(result).allSatisfy(dto -> {
            assertThat(dto.getAvailable()).isTrue();
            assertThat(dto.getLastBooking()).isNull();
            assertThat(dto.getNextBooking()).isNull();
        });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t\n"})
    void blankSearchReturnsNoItems(String query) {
        persistItem(persistUser("owner"), "Drill");
        flushAndClear();

        assertThat(itemService.search(query)).isEmpty();
    }

    @Test
    void searchReturnsEmptyListWhenNothingMatches() {
        persistItem(persistUser("owner"), "Drill");
        flushAndClear();

        assertThat(itemService.search("bicycle")).isEmpty();
    }

    @Test
    void completedApprovedBookingAllowsPersistingCommentWithAuthorAndCreationTime() {
        User owner = persistUser("owner");
        User booker = persistUser("booker");
        Item item = persistItem(owner, "Drill");
        LocalDateTime now = LocalDateTime.now();
        persistBooking(item, booker, now.minusDays(2), now.minusDays(1), BookingStatus.APPROVED);
        flushAndClear();
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        CommentDto result = itemService.addComment(booker.getId(), item.getId(), new CommentCreateDto("Works well"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getText()).isEqualTo("Works well");
        assertThat(result.getAuthorName()).isEqualTo(booker.getName());
        assertThat(result.getCreated()).isBetween(before, LocalDateTime.now());
        flushAndClear();
        Comment saved = entityManager.find(Comment.class, result.getId());
        assertThat(saved.getText()).isEqualTo(result.getText());
        assertThat(saved.getAuthor().getId()).isEqualTo(booker.getId());
        assertThat(saved.getItem().getId()).isEqualTo(item.getId());
        assertThat(saved.getCreated()).isBetween(before, LocalDateTime.now());
        assertThat(itemService.getById(owner.getId(), item.getId()).getComments())
                .extracting(CommentDto::getId).containsExactly(result.getId());
    }

    @Test
    void cannotCommentWithoutBooking() {
        User owner = persistUser("owner");
        User user = persistUser("viewer");
        Item item = persistItem(owner, "Drill");
        flushAndClear();

        assertCommentForbidden(user, item);
    }

    @ParameterizedTest
    @CsvSource({"WAITING,-2,-1", "REJECTED,-2,-1", "APPROVED,-1,1", "APPROVED,1,2"})
    void cannotCommentWithUnapprovedOrUnfinishedBooking(BookingStatus status, int startDays, int endDays) {
        User owner = persistUser("owner");
        User booker = persistUser("booker");
        Item item = persistItem(owner, "Drill");
        LocalDateTime now = LocalDateTime.now();
        persistBooking(item, booker, now.plusDays(startDays), now.plusDays(endDays), status);
        flushAndClear();

        assertCommentForbidden(booker, item);
    }

    @Test
    void completedBookingForAnotherUserOrItemDoesNotAllowComment() {
        User owner = persistUser("owner");
        User booker = persistUser("booker");
        User otherBooker = persistUser("other-booker");
        Item item = persistItem(owner, "Drill");
        Item otherItem = persistItem(owner, "Saw");
        LocalDateTime now = LocalDateTime.now();
        persistBooking(item, otherBooker, now.minusDays(2), now.minusDays(1), BookingStatus.APPROVED);
        persistBooking(otherItem, booker, now.minusDays(2), now.minusDays(1), BookingStatus.APPROVED);
        flushAndClear();

        assertCommentForbidden(booker, item);
    }

    @Test
    void cannotCommentAsMissingUser() {
        Item item = persistItem(persistUser("owner"), "Drill");

        assertThatThrownBy(() -> itemService.addComment(Long.MAX_VALUE, item.getId(), new CommentCreateDto("Good")))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
    }

    @Test
    void cannotCommentOnMissingItem() {
        User user = persistUser("booker");

        assertThatThrownBy(() -> itemService.addComment(user.getId(), Long.MAX_VALUE, new CommentCreateDto("Good")))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Item");
    }

    private void assertCommentForbidden(User user, Item item) {
        assertThatThrownBy(() -> itemService.addComment(user.getId(), item.getId(), new CommentCreateDto("Good")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Comment is allowed only after a completed booking");
        assertThat(entityManager.createQuery("select count(c) from Comment c", Long.class).getSingleResult()).isZero();
    }

    private ItemRequest persistRequest(User requestor) {
        ItemRequest request = ItemRequest.builder().requestor(requestor).description("Need a drill")
                .created(LocalDateTime.now()).build();
        entityManager.persist(request);
        return request;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private User persistUser(String name) {
        User user = User.builder().name(name).email(name + "@example.com").build();
        entityManager.persist(user);
        return user;
    }

    private Item persistItem(User owner, String name) {
        Item item = Item.builder().name(name).description("Description").available(true).owner(owner).build();
        entityManager.persist(item);
        return item;
    }

    private Booking persistBooking(Item item, User booker, LocalDateTime start, LocalDateTime end,
                                   BookingStatus status) {
        Booking booking = Booking.builder().item(item).booker(booker).start(start).end(end).status(status).build();
        entityManager.persist(booking);
        return booking;
    }

    private void persistComment(Item item, User author, String text, LocalDateTime created) {
        entityManager.persist(Comment.builder().item(item).author(author).text(text).created(created).build());
    }

    private void assertBooking(ItemBookingDto actual, Booking expected) {
        assertThat(actual).isNotNull();
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getBookerId()).isEqualTo(expected.getBooker().getId());
        assertThat(actual.getStart()).isEqualTo(expected.getStart());
    }
}
