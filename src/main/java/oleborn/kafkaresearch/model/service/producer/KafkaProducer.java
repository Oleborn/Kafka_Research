package oleborn.kafkaresearch.model.service.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.kafkaresearch.model.dto.OrderEventDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Producer для отправки сообщений в различные топики.
 * Использует разные KafkaTemplate для демонстрации стандартного, транзакционного,
 * высокопроизводительного, низколатентного и надёжного продюсеров.
 * Ошибочные сообщения отправляются в Dead Letter Queue (DLQ).
 *
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaProducer {

    // Стандартный продюсер
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Транзакционный продюсер
    private final KafkaTemplate<String, Object> transactionalKafkaTemplate;

    // Высокопроизводительный продюсер
    private final KafkaTemplate<String, Object> highThroughputKafkaTemplate;

    // Низколатентный продюсер
    private final KafkaTemplate<String, Object> lowLatencyKafkaTemplate;

    // Надёжный продюсер
    private final KafkaTemplate<String, Object> reliableKafkaTemplate;


    @Value("${app.kafka.topics.order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topics.dead-letter-queue}")
    private String dlqTopic;

    @Value("${app.kafka.topics.user-events}")
    private String userEventsTopic;

    /**
     * Отправка сообщения через стандартный продюсер.
     * Используется для общего назначения (например, заказы).
     * @param key Ключ сообщения
     * @param orderEventDto Сообщение
     */
    public void sendStandard(String key, OrderEventDto orderEventDto) {
        try {
            kafkaTemplate.send(orderEventsTopic, key, orderEventDto);
            log.info("Standard producer sent to {}: key={}, value={}", orderEventsTopic, key, orderEventDto);
        } catch (Exception e) {
            log.error("Error sending to {}, sending to DLQ: {}", orderEventsTopic, e.getMessage());
        }
    }

    /**
     * Отправка сообщения через транзакционный продюсер.
     * Атомарно отправляет в два топика (order-events и user-events).
     * @param key Ключ сообщения
     * @param orderEventDto Сообщение
     */
    @Transactional
    public void sendTransactional(String key, OrderEventDto orderEventDto) {
        try {
            transactionalKafkaTemplate.send(orderEventsTopic, key, orderEventDto);
            transactionalKafkaTemplate.send(userEventsTopic, key, orderEventDto);
            log.info("Transactional producer sent to {} and {}: key={}, value={}",
                    orderEventsTopic, userEventsTopic, key, orderEventDto);
        } catch (Exception e) {
            log.error("Error in transaction, sending to DLQ: {}", e.getMessage());
            throw e; // Откат транзакции
        }
    }

    /**
     * Отправка сообщения через высокопроизводительный продюсер.
     * Подходит для больших объёмов данных (например, события пользователей).
     * @param key Ключ сообщения
     * @param orderEventDto Сообщение
     */
    public void sendHighThroughput(String key, OrderEventDto orderEventDto) {
        try {
            highThroughputKafkaTemplate.send(userEventsTopic, key, orderEventDto);
            log.info("High throughput producer sent to {}: key={}, value={}", userEventsTopic, key, orderEventDto);
        } catch (Exception e) {
            log.error("Error sending to {}, sending to DLQ: {}", userEventsTopic, e.getMessage());
            sendToDlq(key, orderEventDto, e.getMessage());
        }
    }

    /**
     * Отправка сообщения через низколатентный продюсер.
     * Подходит для уведомлений с минимальной задержкой.
     * @param key Ключ сообщения
     * @param orderEventDto Сообщение
     */
    public void sendLowLatency(String key, OrderEventDto orderEventDto) {
        try {
            lowLatencyKafkaTemplate.send(orderEventsTopic, key, orderEventDto);
            log.info("Low latency producer sent to {}: key={}, value={}", orderEventsTopic, key, orderEventDto);
        } catch (Exception e) {
            log.error("Error sending to {}, sending to DLQ: {}", orderEventsTopic, e.getMessage());
            sendToDlq(key, orderEventDto, e.getMessage());
        }
    }

    /**
     * Отправка сообщения через надёжный продюсер.
     * Гарантирует доставку и порядок для критических данных.
     * @param key Ключ сообщения
     * @param message Сообщение
     */
    public void sendReliable(String key, OrderEventDto message) {
        try {
            reliableKafkaTemplate.send(orderEventsTopic, key, message);
            log.info("Reliable producer sent to {}: key={}, value={}", orderEventsTopic, key, message);
        } catch (Exception e) {
            log.error("Error sending to {}, sending to DLQ: {}", orderEventsTopic, e.getMessage());
            sendToDlq(key, message, e.getMessage());
        }
    }

    /**
     * Отправка сообщения в Dead Letter Queue при ошибке.
     * @param key Ключ сообщения
     * @param orderEventDto Сообщение
     * @param error Описание ошибки
     */
    private void sendToDlq(String key, OrderEventDto orderEventDto, String error) {
        try {
            String dlqMessage = String.format("Original message: %s, Error: %s", orderEventDto.toString(), error);
            kafkaTemplate.send(dlqTopic, key, dlqMessage);
            log.info("Sent to DLQ {}: key={}, value={}", dlqTopic, key, orderEventDto);
        } catch (Exception e) {
            log.error("Failed to send to DLQ {}: {}", dlqTopic, e.getMessage());
        }
    }
}