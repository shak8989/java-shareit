package ru.practicum.shareit.item;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.shareit.item.model.Item;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    @EntityGraph(attributePaths = "owner")
    List<Item> findByRequestIdInOrderById(List<Long> requestIds);

    List<Item> findByOwnerIdOrderById(long ownerId);

    @Query("select i from Item i where i.available = true and "
            + "(lower(i.name) like lower(concat('%', :text, '%')) or "
            + "lower(i.description) like lower(concat('%', :text, '%'))) order by i.id")
    List<Item> searchAvailable(@Param("text") String text);
}
