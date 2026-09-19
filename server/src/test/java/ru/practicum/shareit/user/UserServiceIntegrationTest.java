package ru.practicum.shareit.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private UserService service;

    @Test
    void createsUserWithGeneratedIdAndPersistsNameAndEmail() {
        UserDto result = service.create(new UserDto(null, "Alice", "alice@example.com"));

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Alice");
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertStoredUser(result.getId(), "Alice", "alice@example.com");
    }

    @Test
    void cannotCreateUserWithDuplicateEmail() {
        User existing = persistUser("Alice", "alice@example.com");
        flushAndClear();

        assertThatThrownBy(() -> service.create(new UserDto(null, "Bob", existing.getEmail())))
                .isInstanceOf(ConflictException.class).hasMessageContaining(existing.getEmail());
        assertThat(service.getAll()).extracting(UserDto::getId).containsExactly(existing.getId());
    }

    @Test
    void updatesNameAndEmail() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        UserDto result = service.update(user.getId(), new UserDto(null, "Alicia", "alicia@example.com"));

        assertThat(result).isEqualTo(new UserDto(user.getId(), "Alicia", "alicia@example.com"));
        assertStoredUser(user.getId(), "Alicia", "alicia@example.com");
    }

    @Test
    void updatesOnlyNameAndPreservesEmail() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        UserDto result = service.update(user.getId(), new UserDto(null, "Alicia", null));

        assertThat(result).isEqualTo(new UserDto(user.getId(), "Alicia", "alice@example.com"));
        assertStoredUser(user.getId(), "Alicia", "alice@example.com");
    }

    @Test
    void updatesOnlyEmailAndPreservesName() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        UserDto result = service.update(user.getId(), new UserDto(null, null, "alicia@example.com"));

        assertThat(result).isEqualTo(new UserDto(user.getId(), "Alice", "alicia@example.com"));
        assertStoredUser(user.getId(), "Alice", "alicia@example.com");
    }

    @Test
    void emptyUpdatePreservesNameAndEmail() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        UserDto result = service.update(user.getId(), new UserDto(null, null, null));

        assertThat(result).isEqualTo(new UserDto(user.getId(), "Alice", "alice@example.com"));
        assertStoredUser(user.getId(), "Alice", "alice@example.com");
    }

    @Test
    void updatingToOwnEmailIsAllowed() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        UserDto result = service.update(user.getId(), new UserDto(null, null, user.getEmail()));

        assertThat(result).isEqualTo(new UserDto(user.getId(), "Alice", "alice@example.com"));
        assertStoredUser(user.getId(), "Alice", "alice@example.com");
    }

    @Test
    void cannotUpdateToAnotherUsersEmail() {
        User alice = persistUser("Alice", "alice@example.com");
        User bob = persistUser("Bob", "bob@example.com");
        flushAndClear();

        assertThatThrownBy(() -> service.update(bob.getId(), new UserDto(null, null, alice.getEmail())))
                .isInstanceOf(ConflictException.class).hasMessageContaining(alice.getEmail());
        assertStoredUser(alice.getId(), "Alice", "alice@example.com");
        assertStoredUser(bob.getId(), "Bob", "bob@example.com");
    }

    @Test
    void cannotUpdateMissingUser() {
        assertThatThrownBy(() -> service.update(Long.MAX_VALUE, new UserDto(null, "Alice", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getsUserByIdWithStoredFields() {
        User user = persistUser("Alice", "alice@example.com");
        flushAndClear();

        assertThat(service.getById(user.getId()))
                .isEqualTo(new UserDto(user.getId(), "Alice", "alice@example.com"));
    }

    @Test
    void cannotGetMissingUser() {
        assertThatThrownBy(() -> service.getById(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getsAllUsers() {
        User alice = persistUser("Alice", "alice@example.com");
        User bob = persistUser("Bob", "bob@example.com");
        flushAndClear();

        assertThat(service.getAll()).containsExactlyInAnyOrder(
                new UserDto(alice.getId(), "Alice", "alice@example.com"),
                new UserDto(bob.getId(), "Bob", "bob@example.com"));
    }

    @Test
    void getsEmptyListWhenNoUsersExist() {
        assertThat(service.getAll()).isEmpty();
    }

    @Test
    void deletesUserFromDatabaseAndPreservesOtherUsers() {
        User alice = persistUser("Alice", "alice@example.com");
        User bob = persistUser("Bob", "bob@example.com");
        flushAndClear();

        service.delete(alice.getId());
        flushAndClear();

        assertThat(entityManager.find(User.class, alice.getId())).isNull();
        assertThatThrownBy(() -> service.getById(alice.getId())).isInstanceOf(NotFoundException.class);
        assertThat(service.getAll()).containsExactly(new UserDto(bob.getId(), "Bob", "bob@example.com"));
    }

    @Test
    void cannotDeleteMissingUser() {
        assertThatThrownBy(() -> service.delete(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    private User persistUser(String name, String email) {
        User user = User.builder().name(name).email(email).build();
        entityManager.persist(user);
        return user;
    }

    private void assertStoredUser(long userId, String name, String email) {
        flushAndClear();
        User saved = entityManager.find(User.class, userId);
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getEmail()).isEqualTo(email);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
