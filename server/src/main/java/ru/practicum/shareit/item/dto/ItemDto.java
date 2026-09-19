package ru.practicum.shareit.item.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemDto {
    private Long id;
    private String name;
    private String description;
    private Boolean available;

    private Long requestId;

    private ItemBookingDto lastBooking;

    private ItemBookingDto nextBooking;

    @Builder.Default
    private List<CommentDto> comments = new ArrayList<>();
}
