package oleborn.kafkaresearch.model.service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.kafkaresearch.mapper.OrderMapper;
import oleborn.kafkaresearch.model.dto.OrderEventDto;
import oleborn.kafkaresearch.model.service.OrderService;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * Основной класс для обработки сообщений Kafka, содержащий различные стратегии потребления
 * в зависимости от требований к обработке сообщений.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;

    /**
     * Стандартный потребитель для обработки сообщений в формате JSON строки.
     * <p>
     * Применяется, когда:
     * - Сообщения приходят в виде сырой JSON строки
     * - Требуется ручной контроль процесса десериализации
     * - Необходима дополнительная обработка ошибок парсинга JSON
     * <p>
     * Особенности:
     * - Использует ObjectMapper для десериализации
     * - Логирует ошибки парсинга JSON
     * - Подходит для обработки сообщений с нестандартными форматами
     *
     * @param message сырое JSON сообщение в виде строки
     */
    @KafkaListener(
            topics = "${app.kafka.topics.order-events}"
            //,properties = {"value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"}
            //,properties = {"value.deserializer=org.springframework.kafka.support.serializer.JsonDeserializer"}
            //при замене меняется способ чтения с listenStandard(OrderEventDto orderEventDto) на listenStandard(String message) то есть приходящее сообщение десериализуется как строка
    )
    @KafkaHandler
    public void listenStandard(String message) {
        try {
            OrderEventDto order = objectMapper.readValue(message, OrderEventDto.class);
            orderService.saveOrder(orderMapper.toEntity(order));
            log.info("Standard consumer with message processed and saved order: {}", order.orderId());
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            try {
                throw e;
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Оптимизированный стандартный потребитель для автоматической десериализации DTO.
     * <p>
     * Применяется, когда:
     * - Сообщения приходят в стандартном JSON формате
     * - Spring Kafka может автоматически десериализовать в OrderEventDto
     * - Требуется минимальная конфигурация
     * <p>
     * Особенности:
     * - Автоматическая десериализация Spring Kafka
     * - Более чистый и лаконичный код
     * - Меньший контроль над процессом десериализации
     *
     * @param orderEventDto автоматически десериализованный объект OrderEventDto
     */
    @KafkaListener(topics = "${app.kafka.topics.order-events}")
    @KafkaHandler
    public void listenStandard(OrderEventDto orderEventDto) {
        try {
            orderService.saveOrder(orderMapper.toEntity(orderEventDto));
            log.info("Standard consumer with orderEventDto processed and saved order: {}", orderEventDto.orderId());
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
        }
    }

    /**
     * Пакетный потребитель для обработки групп сообщений.
     * <p>
     * Применяется, когда:
     * - Требуется обработка большого количества сообщений
     * - Важна эффективность, а не минимальная задержка
     * - Можно обрабатывать сообщения группами
     * <p>
     * Особенности:
     * - Получает список сообщений за один вызов
     * - Использует специальную фабрику контейнеров для пакетной обработки
     * - Уменьшает нагрузку за счет группировки операций
     *
     * @param orderEventDtos список объектов OrderEventDto для пакетной обработки
     */
//    @KafkaListener(
//            topics = "${app.kafka.topics.order-events}",
//            containerFactory = "batchKafkaListenerContainerFactory"
//    )
//    @KafkaHandler
//    public void listenBatch(List<OrderEventDto> orderEventDtos) {
//        log.info("Batch consumer processing {} messages", orderEventDtos.size());
//        orderEventDtos.forEach(dto -> {
//            orderService.saveOrder(orderMapper.toEntity(dto));
//            log.info("Batch consumer saved order: {}", dto.orderId());
//        });
//    }

    /**
     * Потребитель с минимальной задержкой для критически важных сообщений.
     * <p>
     * Применяется, когда:
     * - Требуется минимальное время между получением и обработкой сообщения
     * - Обрабатываются уведомления или события реального времени
     * - Важна скорость реакции на события
     * <p>
     * Особенности:
     * - Оптимизирован для минимальной задержки
     * - Использует специальную low-latency фабрику контейнеров
     * - Обрабатывает сообщения по одному с максимальным приоритетом
     *
     * @param orderEventDto событие для немедленной обработки
     */
//    @KafkaListener(
//            topics = "${app.kafka.topics.notification-events}",
//            containerFactory = "lowLatencyKafkaListenerContainerFactory"
//    )
//    @KafkaHandler
//    public void listenLowLatency(OrderEventDto orderEventDto) {
//        orderService.saveOrder(orderMapper.toEntity(orderEventDto));
//        log.info("Low latency consumer processed and saved order: {}", orderEventDto.orderId());
//    }

    /**
     * Высокопроизводительный потребитель для обработки большого потока сообщений.
     * <p>
     * Применяется, когда:
     * - Требуется обрабатывать высокий трафик сообщений
     * - Задержка менее критична, чем пропускная способность
     * - Обрабатываются пользовательские события или логгирующие данные
     * <p>
     * Особенности:
     * - Оптимизирован для максимальной пропускной способности
     * - Использует специальную high-throughput фабрику контейнеров
     * - Обычно работает с несколькими потоками обработки
     *
     * @param orderEventDto событие для обработки в высокопроизводительном режиме
     */
//    @KafkaListener(
//            topics = "${app.kafka.topics.user-events}",
//            containerFactory = "highThroughputKafkaListenerContainerFactory"
//    )
//    @KafkaHandler
//    public void listenHighThroughput(OrderEventDto orderEventDto) {
//        orderService.saveOrder(orderMapper.toEntity(orderEventDto));
//        log.info("High throughput consumer processed and saved order: {}", orderEventDto.orderId());
//    }
}