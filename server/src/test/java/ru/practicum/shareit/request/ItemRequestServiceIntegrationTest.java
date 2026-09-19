package ru.practicum.shareit.request;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.ItemService;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.ItemRequestItemDto;
import ru.practicum.shareit.user.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Transactional
class ItemRequestServiceIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ItemRequestService service;
    @Autowired
    private ItemService itemService;

    private User requestor;
    private User owner;

    @BeforeEach
    void setUp() {
        requestor = user("requestor");
        owner = user("owner");
    }

    @Test
    void createsRequestWithCurrentTimeAndEmptyItems() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ItemRequestDto result = service.create(requestor.getId(), new ItemRequestCreateDto("Need a drill"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getDescription()).isEqualTo("Need a drill");
        assertThat(result.getCreated()).isBetween(before, LocalDateTime.now().plusSeconds(1));
        assertThat(result.getItems()).isEmpty();
        entityManager.flush();
        entityManager.clear();
        ItemRequest saved = entityManager.find(ItemRequest.class, result.getId());
        assertThat(saved.getRequestor().getId()).isEqualTo(requestor.getId());
        assertThat(saved.getDescription()).isEqualTo(result.getDescription());
    }

    @Test
    void ownRequestsAreNewestFirstAndContainOnlyTheirAnswers() {
        ItemRequest older = request(requestor, "Older", LocalDateTime.now().minusDays(2));
        ItemRequest newer = request(requestor, "Newer", LocalDateTime.now().minusDays(1));
        ItemRequest another = request(owner, "Another user", LocalDateTime.now());
        ItemDto answer = createItem(owner.getId(), older.getId());
        createItem(requestor.getId(), another.getId());
        createItem(owner.getId(), null);
        entityManager.flush();
        entityManager.clear();

        List<ItemRequestDto> result = service.getOwnRequests(requestor.getId());

        assertThat(result).extracting(ItemRequestDto::getId).containsExactly(newer.getId(), older.getId());
        assertThat(result.getFirst().getItems()).isEmpty();
        assertThat(result.getLast().getItems()).containsExactly(
                new ItemRequestItemDto(answer.getId(), answer.getName(), owner.getId()));
    }

    @Test
    void otherRequestsExcludeCurrentUserAndAreNewestFirst() {
        request(requestor, "Own", LocalDateTime.now());
        ItemRequest older = request(owner, "Older", LocalDateTime.now().minusDays(2));
        User third = user("third");
        ItemRequest newer = request(third, "Newer", LocalDateTime.now().minusDays(1));
        ItemDto answer = createItem(owner.getId(), newer.getId());
        entityManager.flush();
        entityManager.clear();

        List<ItemRequestDto> result = service.getAllOtherRequests(requestor.getId());

        assertThat(result).extracting(ItemRequestDto::getId).containsExactly(newer.getId(), older.getId());
        assertThat(result.getFirst().getItems()).extracting(ItemRequestItemDto::getId)
                .containsExactly(answer.getId());
        assertThat(result.getLast().getItems()).isEmpty();
    }

    @Test
    void anyExistingUserCanReadRequestWithItsAnswers() {
        ItemRequest request = request(requestor, "Need a drill", LocalDateTime.now());
        ItemDto first = createItem(owner.getId(), request.getId());
        ItemDto second = createItem(owner.getId(), request.getId());
        entityManager.flush();
        entityManager.clear();

        ItemRequestDto result = service.getById(owner.getId(), request.getId());

        assertThat(result.getId()).isEqualTo(request.getId());
        assertThat(result.getItems()).extracting(ItemRequestItemDto::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(entityManager.find(Item.class, first.getId()).getRequest().getId()).isEqualTo(request.getId());
        assertThat(service.getOwnRequests(requestor.getId()).getFirst().getItems()).hasSize(2);
        assertThat(service.getAllOtherRequests(owner.getId()).getFirst().getItems()).hasSize(2);
    }

    @Test
    void ordinaryItemDoesNotRequireRequest() {
        ItemDto item = createItem(owner.getId(), null);
        entityManager.flush();
        entityManager.clear();

        assertThat(item.getRequestId()).isNull();
        assertThat(entityManager.find(Item.class, item.getId()).getRequest()).isNull();
    }

    @Test
    void rejectsMissingRequestWhenCreatingItem() {
        assertThatThrownBy(() -> createItem(owner.getId(), Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnknownUserForEveryRequestOperation() {
        ItemRequest request = request(requestor, "Existing", LocalDateTime.now());
        assertThatThrownBy(() -> service.create(Long.MAX_VALUE, new ItemRequestCreateDto("Need")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getOwnRequests(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getAllOtherRequests(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE, request.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> createItem(Long.MAX_VALUE, request.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsUnknownRequestOnRead() {
        assertThatThrownBy(() -> service.getById(owner.getId(), Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void emptyListsDoNotLoadItems() {
        Statistics statistics = resetStatistics();
        assertThat(service.getOwnRequests(requestor.getId())).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(service.getAllOtherRequests(requestor.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void requestsAndAnswersUseConstantQueryCount(int count) {
        for (int index = 0; index < count; index++) {
            User another = user("another-" + index);
            ItemRequest request = request(another, "Need " + index, LocalDateTime.now().minusDays(index));
            createItem(owner.getId(), request.getId());
        }
        Statistics statistics = resetStatistics();

        List<ItemRequestDto> result = service.getAllOtherRequests(requestor.getId());

        assertThat(result).hasSize(count).allSatisfy(dto -> assertThat(dto.getItems()).hasSize(1));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    private Statistics resetStatistics() {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    private User user(String name) {
        User user = User.builder().name(name).email(name + "@test.ru").build();
        entityManager.persist(user);
        return user;
    }

    private ItemRequest request(User user, String description, LocalDateTime created) {
        ItemRequest request = ItemRequest.builder().requestor(user).description(description).created(created).build();
        entityManager.persist(request);
        return request;
    }

    private ItemDto createItem(long userId, Long requestId) {
        return itemService.create(userId, ItemDto.builder().name("Drill").description("Cordless drill")
                .available(true).requestId(requestId).build());
    }
}
