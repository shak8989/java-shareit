package ru.practicum.shareit.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateDto {
    @Pattern(regexp = "(?s).*\\S.*", message = "Name must not be blank")
    private String name;

    @Pattern(regexp = "(?s).*\\S.*", message = "Email must not be blank")
    @Email
    private String email;
}
