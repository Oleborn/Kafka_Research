package oleborn.kafkaresearch.repository;

import oleborn.kafkaresearch.model.entity.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEvent, UUID> {
    List<OrderEvent> findByOrderId(String orderId);
}