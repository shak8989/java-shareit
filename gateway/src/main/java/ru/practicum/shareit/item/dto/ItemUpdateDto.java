package ru.practicum.shareit.item.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemUpdateDto {
    @Pattern(regexp = "(?s).*\\S.*", message = "Name must not be blank")
    private String name;

    @Pattern(regexp = "(?s).*\\S.*", message = "Description must not be blank")
    private String description;

    private Boolean available;
}
