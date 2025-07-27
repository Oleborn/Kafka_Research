package oleborn.kafkaresearch.model.dto;

import oleborn.kafkaresearch.model.dictionary.OrderEventType;
import oleborn.kafkaresearch.model.dictionary.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderEventDto(
        String orderId,
        String userId,
        OrderEventType eventType,
        OrderStatus orderStatus,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemDto> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String paymentMethod,
        String shippingAddress
) {}