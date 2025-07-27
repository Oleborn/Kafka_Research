package oleborn.kafkaresearch.model.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.transaction.KafkaAwareTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Конфигурация Kafka Consumer для Spring Kafka.
 * Настраивает консьюмеры для разных сценариев: стандартный, пакетный, высокопроизводительный, низколатентный.
 * Используется с application.yml (bootstrap-servers, group-id, retry).
 *
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers; // Адреса брокеров (localhost:9092,localhost:9094,localhost:9096 для хоста)

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId; // Базовый ID группы консьюмеров (kafka-demo-consumer-group)

    // Параметры ретраев из application.yml
    @Value("${app.kafka.retry.backoff-delay}")
    private long retryBackoffDelay; // Начальная задержка ретрая (1000 мс)

    @Value("${app.kafka.retry.multiplier}")
    private double retryMultiplier; // Множитель экспоненциальной задержки (2.0)

    @Value("${app.kafka.retry.max-delay}")
    private long retryMaxDelay; // Максимальная задержка ретрая (10000 мс)

    /**
     * Базовая конфигурация для всех консьюмеров.
     * Использует параметры из application.yml и добавляет общие настройки.
     * @return Map с настройками консьюмера.
     */
    private Map<String, Object> getBaseConsumerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        // Адреса брокеров Kafka
        // Влияние: Определяет, к каким брокерам подключается консьюмер
        // Рекомендация: localhost:9094,localhost:9096,localhost:9098 (для хоста); kafka1:9092,kafka2:9092,kafka3:9092 (в Docker)
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Десериализаторы для ключей и значений
        // Влияние: Преобразуют байты в строки (StringDeserializer) для обработки сообщений
        // Рекомендация: StringDeserializer для строк; JsonDeserializer/AvroDeserializer для сложных объектов
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        //Обязательно добавить доверенные пакеты для работы тут десериализатора
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        // Политика чтения при отсутствии смещений
        // Влияние: earliest — читать с начала топика; latest — только новые сообщения
        // Рекомендация: earliest для демо (чтобы видеть все сообщения); latest для продакшена (чтобы не обрабатывать старые)
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Автоматический коммит смещений
        // Влияние: true — Kafka коммитит смещения автоматически; false — ручной коммит через Acknowledgment
        // Рекомендация: true для демо (простота); false для продакшена (контроль обработки)
        /*
            При true:
            Consumer периодически (через auto.commit.interval.ms, по умолчанию 5 сек) отправляет брокеру подтверждение о последнем успешно обработанном сообщении
            Происходит в фоновом режиме, без участия разработчика

            При false:
            Требуется явный вызов acknowledge() в коде потребителя
            Полный контроль над моментом подтверждения обработки
         */
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        // MAX_POLL_RECORDS_CONFIG определяет максимальное количество записей, которое потребитель (consumer) может получить за один вызов метода poll().
        // Влияние: Ограничивает объем данных за запрос (меньше — ниже нагрузка, больше — выше пропускная способность)
        // Рекомендация: 500 для демо; 100-1000 в зависимости от нагрузки
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // Максимальный интервал между poll-запросами
        // Влияние: Если консьюмер не вызывает poll в течение этого времени, брокер считает его мёртвым
        // Рекомендация: 300000 (5 минут) для стабильности; 180000 (3 минуты) для быстрого обнаружения сбоев
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);

        // Таймаут сессии консьюмера
        // Влияние: Время, за которое брокер считает консьюмер живым без heartbeat
        // Рекомендация: 30000 (30 секунд); 10000-45000 в зависимости от нагрузки
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);

        // Интервал отправки heartbeat
        // Влияние: Частота проверки живости консьюмера (должно быть < session-timeout-ms/3)
        // Рекомендация: 3000 (3 секунды); 1000-5000
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);

        return configProps;
    }

    /**
     * ConsumerFactory для стандартных консьюмеров.
     * Используется для обработки сообщений с умеренной нагрузкой.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = getBaseConsumerConfigs();
        // ID группы консьюмеров
        // Влияние: Координирует чтение партиций между консьюмерами в группе
        // Рекомендация: Уникальное имя (kafka-demo-consumer-group); описательное для продакшена (order-group)
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Фабрика для создания контейнеров слушателей Kafka с поддержкой транзакций.
     * Создает контейнеры с ручным управлением подтверждением сообщений.
     *
     * @param consumerFactory фабрика для создания потребителей Kafka
     * @param kafkaTransactionManager менеджер транзакций Kafka
     * @return настроенная фабрика контейнеров слушателей
     *
     * @see org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
     * @see org.springframework.kafka.listener.ContainerProperties.AckMode
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,

            @Qualifier("consumerKafkaTransactionManager")
            KafkaAwareTransactionManager<String, Object> kafkaTransactionManager
    ) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setKafkaAwareTransactionManager(kafkaTransactionManager);
        /*
        MANUAL/MANUAL_IMMEDIATE - ручное подтверждение через Acknowledgment
        RECORD - подтверждение после каждой записи
        BATCH - подтверждение после обработки пакета
        TIME - подтверждение по таймеру
        */
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /**
     * Создает транзакционную фабрику продюсеров для потребителей Kafka.
     * <p>
     * Конфигурирует продюсер с:
     * <ul>
     *   <li>Сериализацией ключей как String</li>
     *   <li>Сериализацией значений как JSON</li>
     *   <li>Поддержкой транзакций с уникальным ID</li>
     * </ul>
     *
     * @return настроенная транзакционная фабрика продюсеров
     * @throws IllegalStateException если не удалось создать фабрику
     *
     * @see org.springframework.kafka.core.DefaultKafkaProducerFactory
     * @see org.apache.kafka.clients.producer.ProducerConfig
     */
    @Bean(name = "consumerTransactionalProducerFactory")
    public ProducerFactory<String, Object> consumerTransactionalProducerFactory()  {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tx-" + UUID.randomUUID());

        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(configProps);
        factory.setTransactionIdPrefix("tx-");
        return factory;
    }

    /**
     * Создает менеджер транзакций Kafka для потребителей.
     * <p>
     * Обеспечивает транзакционную обработку сообщений, связывая
     * подтверждение офсетов с отправкой новых сообщений в одной транзакции.
     *
     * @param producerFactory фабрика продюсеров, должна поддерживать транзакции
     * @return настроенный менеджер транзакций
     * @throws IllegalArgumentException если producerFactory не поддерживает транзакции
     *
     * @see org.springframework.kafka.transaction.KafkaTransactionManager
     * @see #consumerTransactionalProducerFactory
     */
    @Bean(name = "consumerKafkaTransactionManager")
    public KafkaAwareTransactionManager<String, Object> consumerKafkaTransactionManager(

            @Qualifier("consumerTransactionalProducerFactory")
            ProducerFactory<String, Object> producerFactory
    ) {
        return new KafkaTransactionManager<>(producerFactory);
    }

    /**===================================Для пакетной обработки===================================================*/

    /**
     * ConsumerFactory для пакетной обработки.
     * Оптимизирован для обработки больших объёмов сообщений.
     */
    @Bean
    public ConsumerFactory<String, String> batchConsumerFactory() {
        Map<String, Object> configProps = getBaseConsumerConfigs();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-batch");

        // Меньше записей за poll для пакетной обработки
        // Влияние: Снижает нагрузку на обработку одного пакета
        // Рекомендация: 100 для демо; 50-500 для продакшена
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // Минимальный объём данных для fetch
        // Влияние: Консьюмер ждёт, пока накопится указанный объём данных
        // Рекомендация: 1024 (1 КБ) для умеренной нагрузки; 10000-50000 для высокой
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);

        // Максимальное время ожидания fetch
        // Влияние: Консьюмер ждёт данные до указанного времени
        // Рекомендация: 500 мс для демо; 100-1000 мс для продакшена
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * KafkaListenerContainerFactory для пакетной обработки.
     * Обрабатывает сообщения списком (List<String>).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());

        // Включает пакетную обработку
        // Влияние: @KafkaListener получает List<String> вместо отдельных сообщений
        // Рекомендация: true для пакетной обработки
        factory.setBatchListener(true);

        // Режим коммита
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        // Один поток для упрощения обработки пакетов
        // Рекомендация: 1 для демо; 1-3 для продакшена
        factory.setConcurrency(1);

        // Обработчик ошибок с ретраями
        ExponentialBackOff backOff = new ExponentialBackOff(retryBackoffDelay, retryMultiplier);
        backOff.setMaxElapsedTime(retryMaxDelay);
        factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));

        return factory;
    }

    /**===================================Для высокопроизводительных консьюмеров==========================================*/

    /**
     * ConsumerFactory для высокопроизводительных консьюмеров.
     * Оптимизирован для максимальной пропускной способности.
     */
    @Bean
    public ConsumerFactory<String, String> highThroughputConsumerFactory() {
        Map<String, Object> configProps = getBaseConsumerConfigs();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-high-throughput");

        // Больше записей за poll
        // Влияние: Увеличивает объем обрабатываемых данных за раз
        // Рекомендация: 1000 для демо; 500-5000 для продакшена
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);

        // Больший объём данных для fetch
        // Влияние: Консьюмер получает больше данных за раз, снижая число запросов
        // Рекомендация: 50000 (50 КБ) для демо; 100000-500000 для продакшена
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 50000);

        // Меньше время ожидания fetch
        // Влияние: Ускоряет получение данных при высокой нагрузке
        // Рекомендация: 100 мс для демо; 50-200 мс для продакшена
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100);

        // Размер буфера приёма
        // Влияние: Увеличивает буфер для сетевых данных
        // Рекомендация: 65536 (64 КБ) для демо; 131072-524288 для продакшена
        configProps.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, 65536);

        // Размер буфера отправки
        // Влияние: Увеличивает буфер для отправки подтверждений
        // Рекомендация: 131072 (128 КБ) для демо; 262144-524288 для продакшена
        configProps.put(ConsumerConfig.SEND_BUFFER_CONFIG, 131072);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * KafkaListenerContainerFactory для высокопроизводительных консьюмеров.
     * Подходит для стриминга больших объёмов данных.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> highThroughputKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(highThroughputConsumerFactory());

        // Режим коммита
        // Влияние: BATCH для авто-коммита оптимизирует производительность
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        // Больше потоков для параллелизма
        // Рекомендация: 5 для демо (для топиков с 3+ партициями); равно числу партиций
        factory.setConcurrency(5);

        // Обработчик ошибок
        ExponentialBackOff backOff = new ExponentialBackOff(retryBackoffDelay, retryMultiplier);
        backOff.setMaxElapsedTime(retryMaxDelay);
        factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));

        return factory;
    }

    /**===================================Для консьюмеров с низкой задержкой==========================================*/

    /**
     * ConsumerFactory для консьюмеров с низкой задержкой.
     * Оптимизирован для минимальной задержки обработки сообщений.
     */
    @Bean
    public ConsumerFactory<String, String> lowLatencyConsumerFactory() {
        Map<String, Object> configProps = getBaseConsumerConfigs();
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId + "-low-latency");

        // Одна запись за poll
        // Влияние: Минимизирует задержку обработки
        // Рекомендация: 1 для демо и продакшена (увеличивает частоту poll)
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);

        // Минимальный объём данных для fetch
        // Влияние: Консьюмер получает данные сразу, не ожидая накопления
        // Рекомендация: 1 для демо и продакшена
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);

        // Без ожидания fetch
        // Влияние: Немедленное получение данных
        // Рекомендация: 0 для демо и продакшена
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 0);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * KafkaListenerContainerFactory для консьюмеров с низкой задержкой.
     * Подходит для уведомлений или критически важных сообщений.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> lowLatencyKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(lowLatencyConsumerFactory());

        // Режим коммита
        // Влияние: RECORD коммитит после каждой записи для минимальной задержки
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // Один поток для предсказуемой задержки
        // Рекомендация: 1 для демо и продакшена
        factory.setConcurrency(1);

        // Обработчик ошибок
        ExponentialBackOff backOff = new ExponentialBackOff(retryBackoffDelay, retryMultiplier);
        backOff.setMaxElapsedTime(retryMaxDelay);
        factory.setCommonErrorHandler(new DefaultErrorHandler(backOff));

        return factory;
    }
}