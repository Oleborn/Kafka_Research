package oleborn.kafkaresearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
public class KafkaResearchApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaResearchApplication.class, args);
	}

}
