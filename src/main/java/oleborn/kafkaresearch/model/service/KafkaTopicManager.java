package oleborn.kafkaresearch.model.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class KafkaTopicManager {

    private AdminClient adminClient;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.user-events}")
    private String userEventsTopic;

    @Value("${app.kafka.topics.order-events}")
    private String orderEventsTopic;

    @Value("${app.kafka.topics.dead-letter-queue}")
    private String dlqTopic;


    /**
     * Инициализирует AdminClient и создаёт необходимые топики при старте приложения.
     */
    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(props);

        createDefaultTopics();
    }

    /**
     * Создаёт предопределённые топики из конфигурации.
     * По умолчанию создаёт топики с 3 партициями и фактором репликации 3.
     */
    private void createDefaultTopics() {
        List<String> topics = new ArrayList<>(Arrays.asList(userEventsTopic, orderEventsTopic, dlqTopic));

        for (String topic : topics) {
            createTopicIfNotExists(topic, 3, (short) 3);
        }
    }

    /**
     * Создаёт топик, если он не существует.
     * Если топик существует, проверяет его параметры.
     */
    public void createTopicIfNotExists(String topicName, int numPartitions, short replicationFactor) {
        try {
            if (topicExists(topicName)) {
                log.warn("Topic '{}' already exists. Checking parameters...", topicName);
                validateTopicConfig(topicName, numPartitions, replicationFactor);
                return;
            }

            NewTopic newTopic = new NewTopic(topicName, numPartitions, replicationFactor);
            adminClient.createTopics(Collections.singleton(newTopic)).all().get();
            log.info("Topic '{}' created successfully (Partitions: {}, Replication: {})",
                    topicName, numPartitions, replicationFactor);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create topic '{}'", topicName, e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Удаляет топик, если он существует.
     */
    public void deleteTopicIfExists(String topicName) {
        try {
            if (!topicExists(topicName)) {
                log.warn("Topic '{}' does not exist. Skipping deletion.", topicName);
                return;
            }

            adminClient.deleteTopics(Collections.singletonList(topicName)).all().get();
            log.info("Topic '{}' deleted successfully", topicName);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to delete topic '{}'", topicName, e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Проверяет существование топика.
     */
    public boolean topicExists(String topicName) throws ExecutionException, InterruptedException {
        return adminClient.listTopics().names().get().contains(topicName);
    }

    /**
     * Проверяет конфигурацию существующего топика.
     */
    private void validateTopicConfig(String topicName, int expectedPartitions, short expectedReplication)
            throws ExecutionException, InterruptedException {

        DescribeTopicsResult describeResult = adminClient.describeTopics(Collections.singletonList(topicName));
        TopicDescription description = describeResult.topicNameValues().get(topicName).get();

        if (description.partitions().size() != expectedPartitions) {
            log.error("Topic '{}' exists but has {} partitions (expected {})",
                    topicName, description.partitions().size(), expectedPartitions);
        }

        short actualReplication = (short) description.partitions().getFirst().replicas().size();
        if (actualReplication != expectedReplication) {
            log.error("Topic '{}' has replication factor {} (expected {})",
                    topicName, actualReplication, expectedReplication);
        }
    }

    /**
     * Закрывает AdminClient при уничтожении бина.
     */
    @PreDestroy
    public void close() {
        if (adminClient != null) {
            adminClient.close();
            log.info("Kafka AdminClient closed");
        }
    }
}