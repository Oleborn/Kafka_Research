package oleborn.kafkaresearch.mapper;

import oleborn.kafkaresearch.model.dto.OrderEventDto;
import oleborn.kafkaresearch.model.dto.OrderItemDto;
import oleborn.kafkaresearch.model.entity.OrderEvent;
import oleborn.kafkaresearch.model.entity.OrderItem;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderEventDto toDto(OrderEvent orderEvent);

    @Mapping(target = "items", source = "items", qualifiedByName = "mapItemsToEntity")
    OrderEvent toEntity(OrderEventDto orderEventDto);

    OrderItemDto toDto(OrderItem orderItem);

    @Named("mapItemsToEntity")
    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemDto orderItemDto);

    // Если нужен отдельный метод без игнорирования order (редкий случай)
    default OrderItem toEntityWithOrder(OrderItemDto dto, @Context OrderEvent order) {
        OrderItem item = toEntity(dto); // использует метод с @Mapping(ignore)
        item.setOrder(order); // устанавливаем order вручную
        return item;
    }
}