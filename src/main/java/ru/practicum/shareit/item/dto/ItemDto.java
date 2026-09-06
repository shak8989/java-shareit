package ru.practicum.shareit.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.shareit.validation.Create;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDto {
    private Long id;

    @NotBlank(groups = Create.class)
    private String name;

    @NotBlank(groups = Create.class)
    private String description;

    @NotNull(groups = Create.class)
    private Boolean available;

    private Long requestId;

    private ItemBookingDto lastBooking;

    private ItemBookingDto nextBooking;

    @Builder.Default
    private List<CommentDto> comments = new ArrayList<>();
}
