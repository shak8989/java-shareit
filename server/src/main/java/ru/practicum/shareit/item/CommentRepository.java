package ru.practicum.shareit.item;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByItemIdOrderByCreatedAsc(long itemId);

    @EntityGraph(attributePaths = {"item", "author"})
    List<Comment> findByItemIdInOrderByCreatedAsc(List<Long> itemIds);
}
