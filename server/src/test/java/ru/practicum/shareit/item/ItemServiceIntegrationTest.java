package ru.practicum.shareit.item;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.Booking;
import ru.practicum.shareit.booking.BookingStatus;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemBookingDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Transactional
class ItemServiceIntegrationTest {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ItemService itemService;

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
