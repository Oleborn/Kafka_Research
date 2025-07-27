package oleborn.kafkaresearch.model.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemDto(
        String productId,
        String productName,
        Integer quantity,
        BigDecimal price
) {}