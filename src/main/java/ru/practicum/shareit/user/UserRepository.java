package ru.practicum.shareit.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(long userId);

    List<User> findAll();

    void deleteById(long userId);

    boolean existsByEmailAndIdNot(String email, Long userId);
}
