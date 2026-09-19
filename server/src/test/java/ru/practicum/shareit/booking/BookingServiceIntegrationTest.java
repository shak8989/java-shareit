package ru.practicum.shareit.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.dto.BookingCreateDto;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BookingServiceIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private BookingService service;

    private User owner;
    private User booker;
    private Item item;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner");
        booker = persistUser("booker");
        item = persistItem(owner, "Drill");
        now = LocalDateTime.now().withNano(0);
    }

    @Test
    void createsWaitingBookingAndPersistsItemBookerAndDates() {
        BookingCreateDto input = new BookingCreateDto(item.getId(), now.plusDays(1), now.plusDays(2));

        BookingDto result = service.create(booker.getId(), input);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getStart()).isEqualTo(input.getStart());
        assertThat(result.getEnd()).isEqualTo(input.getEnd());
        assertThat(result.getStatus()).isEqualTo(BookingStatus.WAITING);
        assertThat(result.getBooker().getId()).isEqualTo(booker.getId());
        assertThat(result.getBooker().getName()).isEqualTo(booker.getName());
        assertThat(result.getBooker().getEmail()).isEqualTo(booker.getEmail());
        assertThat(result.getItem().getId()).isEqualTo(item.getId());
        assertThat(result.getItem().getName()).isEqualTo(item.getName());
        assertThat(result.getItem().getDescription()).isEqualTo(item.getDescription());
        assertThat(result.getItem().getAvailable()).isTrue();
        assertThat(result.getItem().getRequestId()).isNull();
        flushAndClear();
        Booking saved = entityManager.find(Booking.class, result.getId());
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.WAITING);
        assertThat(saved.getStart()).isEqualTo(input.getStart());
        assertThat(saved.getEnd()).isEqualTo(input.getEnd());
        assertThat(saved.getItem().getId()).isEqualTo(item.getId());
        assertThat(saved.getBooker().getId()).isEqualTo(booker.getId());
    }

    @Test
    void cannotCreateBookingForMissingUser() {
        assertThatThrownBy(() -> service.create(Long.MAX_VALUE, validInput()))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
    }

    @Test
    void cannotCreateBookingForMissingItem() {
        BookingCreateDto input = new BookingCreateDto(Long.MAX_VALUE, now.plusDays(1), now.plusDays(2));
        assertThatThrownBy(() -> service.create(booker.getId(), input))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Item");
    }

    @Test
    void ownerCannotBookOwnItem() {
        assertThatThrownBy(() -> service.create(owner.getId(), validInput()))
                .isInstanceOf(ForbiddenException.class).hasMessage("Owner cannot book own item");
    }

    @Test
    void cannotBookUnavailableItem() {
        item.setAvailable(false);
        flushAndClear();

        assertThatThrownBy(() -> service.create(booker.getId(), validInput()))
                .isInstanceOf(ValidationException.class).hasMessage("Item is unavailable");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void databaseRejectsEndEqualToOrBeforeStart(int endOffsetHours) {
        LocalDateTime start = now.plusDays(1);
        BookingCreateDto input = new BookingCreateDto(item.getId(), start, start.plusHours(endOffsetHours));

        // The gateway validates dates; the server also enforces the existing schema constraint.
        assertThatThrownBy(() -> service.create(booker.getId(), input))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("booking_dates_valid");
    }

    @ParameterizedTest
    @CsvSource({"true, APPROVED", "false, REJECTED"})
    void ownerDecidesWaitingBooking(boolean approved, BookingStatus expectedStatus) {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.WAITING);
        flushAndClear();

        BookingDto result = service.approve(owner.getId(), booking.getId(), approved);

        assertThat(result.getId()).isEqualTo(booking.getId());
        assertThat(result.getStatus()).isEqualTo(expectedStatus);
        assertThat(result.getStart()).isEqualTo(booking.getStart());
        assertThat(result.getEnd()).isEqualTo(booking.getEnd());
        flushAndClear();
        assertThat(entityManager.find(Booking.class, booking.getId()).getStatus()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bookerCannotDecideBooking(boolean approved) {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.WAITING);
        flushAndClear();

        assertThatThrownBy(() -> service.approve(booker.getId(), booking.getId(), approved))
                .isInstanceOf(ForbiddenException.class).hasMessage("Only item owner can change booking status");
        assertThat(entityManager.find(Booking.class, booking.getId()).getStatus()).isEqualTo(BookingStatus.WAITING);
    }

    @Test
    void unknownUserCannotApproveBooking() {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.WAITING);

        assertThatThrownBy(() -> service.approve(Long.MAX_VALUE, booking.getId(), true))
                .isInstanceOf(ForbiddenException.class).hasMessage("Only item owner can change booking status");
    }

    @ParameterizedTest
    @CsvSource({"APPROVED, true", "APPROVED, false", "REJECTED, true", "REJECTED, false"})
    void cannotChangeAlreadyDecidedBooking(BookingStatus status, boolean approved) {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), status);
        flushAndClear();

        assertThatThrownBy(() -> service.approve(owner.getId(), booking.getId(), approved))
                .isInstanceOf(ValidationException.class).hasMessage("Booking status is already decided");
        assertThat(entityManager.find(Booking.class, booking.getId()).getStatus()).isEqualTo(status);
    }

    @Test
    void cannotApproveMissingBooking() {
        assertThatThrownBy(() -> service.approve(owner.getId(), Long.MAX_VALUE, true))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Booking");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bookerAndOwnerCanReadBooking(boolean asOwner) {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.APPROVED);
        flushAndClear();

        BookingDto result = service.getById(asOwner ? owner.getId() : booker.getId(), booking.getId());

        assertThat(result.getId()).isEqualTo(booking.getId());
        assertThat(result.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(result.getStart()).isEqualTo(booking.getStart());
        assertThat(result.getEnd()).isEqualTo(booking.getEnd());
        assertThat(result.getBooker().getId()).isEqualTo(booker.getId());
        assertThat(result.getItem().getId()).isEqualTo(item.getId());
    }

    @Test
    void unrelatedUserCannotReadBooking() {
        User stranger = persistUser("stranger");
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.WAITING);

        assertThatThrownBy(() -> service.getById(stranger.getId(), booking.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotReadMissingBooking() {
        assertThatThrownBy(() -> service.getById(booker.getId(), Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Booking");
    }

    @Test
    void unknownUserCannotReadOrListBookings() {
        Booking booking = persistBooking(item, booker, now.plusDays(1), now.plusDays(2), BookingStatus.WAITING);

        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE, booking.getId()))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
        assertThatThrownBy(() -> service.getByBooker(Long.MAX_VALUE, BookingState.ALL))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
        assertThatThrownBy(() -> service.getByOwner(Long.MAX_VALUE, BookingState.ALL))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("User");
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void bookerListsOnlyOwnBookingsForEveryStateInStartDescendingOrder(BookingState state) {
        Map<BookingState, List<Booking>> expected = persistStateBookings(item, booker, now);
        persistStateBookings(item, persistUser("another-booker"), now);
        flushAndClear();

        List<BookingDto> result = service.getByBooker(booker.getId(), state);

        assertThat(result).extracting(BookingDto::getId)
                .containsExactlyElementsOf(expected.get(state).stream().map(Booking::getId).toList());
        assertThat(result).extracting(BookingDto::getStart).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(result).allSatisfy(dto -> assertThat(dto.getBooker().getId()).isEqualTo(booker.getId()));
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void ownerListsBookingsAcrossOwnItemsAndBookersButExcludesOtherOwners(BookingState state) {
        Map<BookingState, List<Booking>> first = persistStateBookings(item, booker, now);
        Map<BookingState, List<Booking>> second = persistStateBookings(persistItem(owner, "Saw"),
                persistUser("another-booker"), now.minusMinutes(10));
        persistStateBookings(persistItem(persistUser("another-owner"), "Other drill"), booker, now);
        List<Long> expectedIds = Stream.concat(first.get(state).stream(), second.get(state).stream())
                .sorted(Comparator.comparing(Booking::getStart).reversed()).map(Booking::getId).toList();
        flushAndClear();

        List<BookingDto> result = service.getByOwner(owner.getId(), state);

        assertThat(result).extracting(BookingDto::getId).containsExactlyElementsOf(expectedIds);
        assertThat(result).extracting(BookingDto::getStart).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void existingUserWithoutBookingsOrItemsGetsEmptyLists(BookingState state) {
        User emptyUser = persistUser("empty-user");
        persistStateBookings(item, booker, now);
        flushAndClear();

        assertThat(service.getByBooker(emptyUser.getId(), state)).isEmpty();
        assertThat(service.getByOwner(emptyUser.getId(), state)).isEmpty();
    }

    private Map<BookingState, List<Booking>> persistStateBookings(Item bookedItem, User bookedBy,
                                                                LocalDateTime reference) {
        Booking pastApproved = persistBooking(bookedItem, bookedBy, reference.minusDays(6), reference.minusDays(5),
                BookingStatus.APPROVED);
        Booking pastRejected = persistBooking(bookedItem, bookedBy, reference.minusDays(4), reference.minusDays(3),
                BookingStatus.REJECTED);
        Booking currentWaiting = persistBooking(bookedItem, bookedBy, reference.minusDays(2), reference.plusDays(2),
                BookingStatus.WAITING);
        Booking currentApproved = persistBooking(bookedItem, bookedBy, reference.minusDays(1), reference.plusDays(1),
                BookingStatus.APPROVED);
        Booking futureApproved = persistBooking(bookedItem, bookedBy, reference.plusDays(3), reference.plusDays(4),
                BookingStatus.APPROVED);
        Booking futureWaiting = persistBooking(bookedItem, bookedBy, reference.plusDays(5), reference.plusDays(6),
                BookingStatus.WAITING);
        Booking futureRejected = persistBooking(bookedItem, bookedBy, reference.plusDays(7), reference.plusDays(8),
                BookingStatus.REJECTED);
        Map<BookingState, List<Booking>> expected = new EnumMap<>(BookingState.class);
        expected.put(BookingState.ALL, List.of(futureRejected, futureWaiting, futureApproved, currentApproved,
                currentWaiting, pastRejected, pastApproved));
        expected.put(BookingState.CURRENT, List.of(currentApproved, currentWaiting));
        expected.put(BookingState.PAST, List.of(pastRejected, pastApproved));
        expected.put(BookingState.FUTURE, List.of(futureRejected, futureWaiting, futureApproved));
        expected.put(BookingState.WAITING, List.of(futureWaiting, currentWaiting));
        expected.put(BookingState.REJECTED, List.of(futureRejected, pastRejected));
        return expected;
    }

    private BookingCreateDto validInput() {
        return new BookingCreateDto(item.getId(), now.plusDays(1), now.plusDays(2));
    }

    private User persistUser(String name) {
        User user = User.builder().name(name).email(name + "@example.com").build();
        entityManager.persist(user);
        return user;
    }

    private Item persistItem(User itemOwner, String name) {
        Item saved = Item.builder().name(name).description("Cordless tool").available(true).owner(itemOwner).build();
        entityManager.persist(saved);
        return saved;
    }

    private Booking persistBooking(Item bookedItem, User bookedBy, LocalDateTime start, LocalDateTime end,
                                   BookingStatus status) {
        Booking booking = Booking.builder().item(bookedItem).booker(bookedBy).start(start).end(end).status(status).build();
        entityManager.persist(booking);
        return booking;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
