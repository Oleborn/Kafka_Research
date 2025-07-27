package oleborn.kafkaresearch.controller;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final KafkaAdmin kafkaAdmin;

    @GetMapping("/cluster")
    public String getClusterInfo() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterResult clusterResult = adminClient.describeCluster();
            return "Cluster ID: " + clusterResult.clusterId().get() + 
                   ", Controller: " + clusterResult.controller().get() + 
                   ", Nodes: " + clusterResult.nodes().get();
        }
    }

    @GetMapping("/topics")
    public String getTopicsInfo() throws ExecutionException, InterruptedException {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeTopicsResult topicsResult = adminClient.describeTopics(Collections.singletonList("order-events"));
            TopicDescription topicDescription = topicsResult.values().get("order-events").get();
            return "Topic: " + topicDescription.name() + 
                   ", Partitions: " + topicDescription.partitions().size() + 
                   ", Replicas: " + topicDescription.partitions().getFirst().replicas();
        }
    }
}