package ru.practicum.shareit.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.exception.ValidationException;
import ru.practicum.shareit.user.dto.UserDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserDto create(UserDto userDto) {
        checkEmailUnique(userDto.getEmail(), null);
        return UserMapper.toDto(
                userRepository.save(UserMapper.toModel(userDto))
        );
    }

    @Override
    public UserDto update(long userId, UserDto userDto) {
        User user = findUser(userId);

        if (userDto.getName() != null) {
            if (userDto.getName().isBlank()) {
                throw new ValidationException(
                        "User name must not be blank"
                );
            }

            user.setName(userDto.getName());
        }

        if (userDto.getEmail() != null) {
            if (userDto.getEmail().isBlank()
                    || !userDto.getEmail().contains("@")) {
                throw new ValidationException("Invalid email");
            }

            checkEmailUnique(userDto.getEmail(), userId);
            user.setEmail(userDto.getEmail());
        }

        return UserMapper.toDto(userRepository.save(user));
    }

    @Override
    public UserDto getById(long userId) {
        return UserMapper.toDto(findUser(userId));
    }

    @Override
    public List<UserDto> getAll() {
        return userRepository.findAll().stream()
                .map(UserMapper::toDto)
                .toList();
    }

    @Override
    public void delete(long userId) {
        findUser(userId);
        userRepository.deleteById(userId);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "User with id " + userId + " not found"
                        )
                );
    }

    private void checkEmailUnique(String email, Long userId) {
        if (userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new ConflictException(
                    "Email is already in use: " + email
            );
        }
    }
}