package oleborn.kafkaresearch.model.service;

import lombok.RequiredArgsConstructor;
import oleborn.kafkaresearch.model.entity.OrderEvent;
import oleborn.kafkaresearch.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderEvent saveOrder(OrderEvent order) {
        return orderRepository.save(order);
    }

    public List<OrderEvent> getOrdersByOrderId(String orderId) {
        return orderRepository.findByOrderId(orderId);
    }

    public List<OrderEvent> getAllOrders() {
        return orderRepository.findAll();
    }
}