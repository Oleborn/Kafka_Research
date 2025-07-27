package oleborn.kafkaresearch.model.config;

import jakarta.persistence.EntityManagerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Kafka Producer для Spring Kafka.
 * Настраивает продюсеры для разных сценариев: стандартный, транзакционный, высокопроизводительный, низколатентный, надёжный.
 * Используется с application.yml (bootstrap-servers, retries, acks).
 *
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers; // Адреса брокеров (localhost:9092,localhost:9094,localhost:9096 для хоста)

    @Value("${app.kafka.retry.max-attempts}")
    private int retryMaxAttempts; // Максимум ретраев (3)

    /**
     * Базовая конфигурация для всех продюсеров.
     * Использует параметры из application.yml и добавляет общие настройки.
     * @return Map с настройками продюсера.
     */
    private Map<String, Object> getBaseProducerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        // Адреса брокеров Kafka
        // Влияние: Определяет, к каким брокерам подключается продюсер
        // Рекомендация: localhost:9094,localhost:9096,localhost:9098 (хост); kafka1:9092,kafka2:9092,kafka3:9092 (Docker)
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Сериализаторы для ключей и значений
        // Влияние: Преобразуют строки в байты для отправки сообщений
        // Рекомендация: StringSerializer для строк; JsonSerializer/AvroSerializer для сложных объектов
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Режим подтверждения доставки
        // Влияние: all — ждёт подтверждения от всех ISR (надёжность); 1 — только от лидера (производительность); 0 — без подтверждения (максимальная скорость)
        // Рекомендация: all для демо и продакшена (совместимо с min.insync.replicas=2); 1 для высокопроизводительного
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // Количество попыток повтора при ошибке доставки
        // Влияние: Повторяет отправку при временных сбоях
        // Рекомендация: 3 для демо (из application.yml); 5-10 для продакшена; Integer.MAX_VALUE только с enable-idempotence=true
        configProps.put(ProducerConfig.RETRIES_CONFIG, retryMaxAttempts);

        // Идемпотентность в Kafka гарантирует, что при повторной отправке сообщения (из-за ошибок сети и т.д.)
        // брокер будет обрабатывать его только один раз, предотвращая дублирование.
        // Влияние: Гарантирует exactly-once доставку (требует acks=all, retries>0)
        // Рекомендация: true для демо и продакшена (надёжность); false для высокопроизводительного
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Уникальный ID транзакции
        // Влияние: Обеспечивает идентификацию транзакций (требуется для exactly-once)
//        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, null);

        // Размер пакета сообщений (байт)
        /*
            Продюсер буферизует сообщения в памяти
            Когда размер накопленных сообщений достигает batch.size:
                Весь пакет отправляется на брокер
                Создается новый пустой батч
            Если батч не заполнен до истечения linger.ms, он все равно будет отправлен
         */
        // Влияние: Больший batch повышает пропускную способность, но увеличивает задержку
        // Рекомендация: 16384 (16 КБ) для демо; 32768-65536 для продакшена
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

        // Задержка перед отправкой пакета (мс)
        // Влияние: Ждёт накопления сообщений для экономии запросов
        // Рекомендация: 5 мс для демо; 0 для низколатентного; 10-20 для высокопроизводительного
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        // buffer.memory — это размер (в байтах) общего буфера памяти, который
        // Kafka Producer использует для временного хранения сообщений перед их отправкой брокерам.
        // Влияние: Хранит сообщения перед отправкой
        // Рекомендация: 33554432 (32 МБ) для демо; 67108864 (64 МБ) для продакшена
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // Тип компрессии
        // Влияние: snappy — баланс скорости и компрессии; lz4 — быстрее; gzip — выше компрессия; none — без компрессии
        // Рекомендация: snappy для демо; lz4 для высокопроизводительного; none для низколатентного
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Таймаут запроса (мс)
        // Влияние: Максимальное время ожидания ответа от брокера
        // Рекомендация: 30000 (30 сек) для демо; 60000 для надёжного; 5000 для низколатентного
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        // Таймаут доставки (мс)
        // Влияние: Общее время на отправку, включая ретраи
        // Рекомендация: 120000 (2 мин) для демо; 300000 для надёжного; 10000 для низколатентного
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

        // Один запрос в полёте для сохранения порядка
        // Влияние: Гарантирует порядок сообщений, но снижает производительность
        // Рекомендация: 1 для демо и продакшена; 5 для стандартного
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        return configProps;
    }

    /**
     * ProducerFactory для стандартного продюсера.
     * Баланс надёжности и производительности.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(getBaseProducerConfigs());
    }

    /**
     * KafkaTemplate для стандартного продюсера.
     * Используется для отправки сообщений в топики (например, order-events).
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**==================================Транзакционный продюсер==============================================**/

    /**
     * ProducerFactory для транзакционного продюсера.
     * <p>
     * Создает продюсеров с поддержкой транзакций для гарантированной доставки сообщений.
     * Основные особенности:
     * <ul>
     *   <li>Поддерживает атомарную отправку в несколько топиков</li>
     *   <li>Гарантирует exactly-once семантику</li>
     *   <li>Интегрируется с Spring Transaction Management</li>
     * </ul>
     *
     * @return ProducerFactory с настроенными транзакционными параметрами
     *
     * @see ProducerConfig#TRANSACTIONAL_ID_CONFIG
     * @see ProducerConfig#ENABLE_IDEMPOTENCE_CONFIG
     */
    @Bean
    public ProducerFactory<String, Object> transactionalProducerFactory() {
        Map<String, Object> configProps = getBaseProducerConfigs();

        /*
         * Уникальный ID транзакции:
         * - Обязателен для транзакционных продюсеров
         * - Должен быть уникальным для каждого экземпляра приложения
         * - Используется Kafka для отслеживания состояния транзакций
         */
        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "kafka-demo-tx-producer");

        /*
         * Включение идемпотентности:
         * - Гарантирует отсутствие дубликатов сообщений
         * - Обязательное требование для транзакций
         * - Автоматически включает acks=all и retries>0
         */
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        DefaultKafkaProducerFactory<String, Object> factory = new DefaultKafkaProducerFactory<>(configProps);
        /*
         * Префикс для транзакционных ID:
         * - Используется при создании новых транзакционных ID
         * - Позволяет масштабировать приложение
         * - Добавляет идентификацию приложения в логи Kafka
         */
        factory.setTransactionIdPrefix("kafka-tx-");
        return factory;
    }

    /**
     * Специализированный KafkaTemplate для транзакционных операций.
     * <p>
     * Особенности:
     * <ul>
     *   <li>Работает только с транзакционным ProducerFactory</li>
     *   <li>Поддерживает аннотацию @Transactional</li>
     *   <li>Автоматически управляет жизненным циклом транзакций</li>
     * </ul>
     *
     * Пример использования:
     * <pre>{@code
     * @Transactional
     * public void processOrder(Order order) {
     *     transactionalKafkaTemplate.send("orders", order.getId(), order);
     *     transactionalKafkaTemplate.send("notifications", order.getUserId(), order);
     * }
     * }</pre>
     *
     * @return KafkaTemplate с поддержкой транзакций
     */
    @Bean
    public KafkaTemplate<String, Object> transactionalKafkaTemplate() {
        return new KafkaTemplate<>(transactionalProducerFactory());
    }

    /**
     * Основной менеджер транзакций для JPA.
     * <p>
     * Координирует транзакции базы данных при использовании в сочетании
     * с KafkaTransactionManager через ChainedTransactionManager.
     *
     * @param emf EntityManagerFactory для JPA
     * @return PlatformTransactionManager для работы с БД
     *
     * @see JpaTransactionManager
     */
    @Primary
    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /**
     * Менеджер транзакций для Kafka.
     * <p>
     * Управляет жизненным циклом транзакций Kafka и обеспечивает:
     * <ul>
     *   <li>Синхронизацию с Spring транзакциями</li>
     *   <li>Откат транзакций при ошибках</li>
     *   <li>Координацию распределенных транзакций</li>
     * </ul>
     *
     * Конфигурация синхронизации:
     * {@code SYNCHRONIZATION_ON_ACTUAL_TRANSACTION} означает, что транзакция Kafka
     * будет зарегистрирована как ресурс в существующей JTA/Spring транзакции.
     *
     * @return KafkaTransactionManager с настроенной синхронизацией
     *
     * @see AbstractPlatformTransactionManager#SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
     */
    @Bean
    public KafkaTransactionManager<String, Object> kafkaTransactionManager() {
        KafkaTransactionManager<String, Object> ktm =
                new KafkaTransactionManager<>(transactionalProducerFactory());
        ktm.setTransactionSynchronization(AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
        return ktm;
    }

    /**=======================================Высокопроизводительный продюсер=========================================**/

    /**
     * ProducerFactory для высокопроизводительного продюсера.
     * Оптимизирован для максимальной пропускной способности.
     */
    @Bean
    public ProducerFactory<String, Object> highThroughputProducerFactory() {
        Map<String, Object> configProps = getBaseProducerConfigs();
        // Меньше подтверждений для скорости
        // Влияние: Подтверждение только от лидера
        // Рекомендация: 1 для демо; all для продакшена, если нужна надёжность
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");

        // Меньше ретраев
        // Рекомендация: 3 для демо; 2-5 для продакшена
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        // Отключение идемпотентности для скорости
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        // Больший батч
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
        // Дольше задержки для батчинга
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 20);

        // Больший буфер
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);

        // Быстрая компрессия
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate для высокопроизводительного продюсера.
     * Подходит для стриминга больших объёмов данных.
     */
    @Bean
    public KafkaTemplate<String, Object> highThroughputKafkaTemplate() {
        return new KafkaTemplate<>(highThroughputProducerFactory());
    }

    /**=====================================Низколатентный продюсер===========================================**/

    /**
     * ProducerFactory для низколатентного продюсера.
     * Оптимизирован для минимальной задержки отправки.
     */
    @Bean
    public ProducerFactory<String, Object> lowLatencyProducerFactory() {
        Map<String, Object> configProps = getBaseProducerConfigs();
        // Отключение батчинга
        // Влияние: Сообщения отправляются сразу
        // Рекомендация: 0 для демо и продакшена
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 0);

        // Немедленная отправка
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0);

        // Без компрессии
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");

        // Короткие таймауты
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate для низколатентного продюсера.
     * Подходит для уведомлений или критических сообщений.
     */
    @Bean
    public KafkaTemplate<String, Object> lowLatencyKafkaTemplate() {
        return new KafkaTemplate<>(lowLatencyProducerFactory());
    }

    /**=========================================Максимально надёжный продюсер=======================================**/

    /**
     * ProducerFactory для максимально надёжного продюсера.
     * Гарантирует доставку и порядок сообщений.
     */
    @Bean
    public ProducerFactory<String, Object> reliableProducerFactory() {
        Map<String, Object> configProps = getBaseProducerConfigs();
        // Максимальная надёжность
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, retryMaxAttempts);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Длинные таймауты
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 300000);

        // Один запрос в полёте для сохранения порядка
        // Влияние: Гарантирует порядок сообщений, но снижает производительность
        // Рекомендация: 1 для демо и продакшена; 5 для стандартного
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * KafkaTemplate для максимально надёжного продюсера.
     * Используется для критически важных данных.
     */
    @Bean
    public KafkaTemplate<String, Object> reliableKafkaTemplate() {
        return new KafkaTemplate<>(reliableProducerFactory());
    }
}