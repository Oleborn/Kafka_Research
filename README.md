# Kafka_Research

## Обзор

Этот репозиторий содержит проект, демонстрирующий настройку и использование Apache Kafka. Он включает в себя конфигурации Docker Compose для быстрого развертывания кластеров Kafka, а также примеры использования Kafka Producer и Consumer. Проект также содержит подробное руководство по Apache Kafka для Java-разработчиков.

## Особенности

*   **Быстрое развертывание Kafka**: Используйте Docker Compose для запуска одноузлового или многоузлового кластера Kafka.
*   **Примеры Producer/Consumer**: Изучите, как отправлять и получать сообщения из Kafka с помощью примеров кода на Java.

## Документация
- [Руководство по Kafka](GUIDE.md)

## Начало работы

### Предварительные требования

Для запуска этого проекта вам потребуется:

*   Docker и Docker Compose
*   Java Development Kit (JDK) 17 или выше
*   Apache Maven

### Развертывание кластера Kafka

Вы можете выбрать один из двух вариантов развертывания кластера Kafka с помощью Docker Compose:

#### Одноузловой кластер Kafka (для разработки и тестирования)

Этот вариант использует `KafkaDocker-compose.yaml` для запуска одного брокера Kafka с поддержкой KRaft.

1.  Перейдите в корневой каталог проекта:

    ```bash
    cd Kafka_Research
    ```

2.  Запустите кластер:

    ```bash
    docker-compose -f KafkaDocker-compose.yaml up -d
    ```

3.  Проверьте статус запущенных контейнеров:

    ```bash
    docker-compose -f KafkaDocker-compose.yaml ps
    ```

#### Многоузловой кластер Kafka (для продакшен-среды)

Этот вариант использует `KafkaClusterDocker-compose.yaml` для запуска трех брокеров Kafka с поддержкой KRaft, что обеспечивает отказоустойчивость и высокую доступность.

1.  Перейдите в корневой каталог проекта:

    ```bash
    cd Kafka_Research
    ```

2.  Запустите кластер:

    ```bash
    docker-compose -f KafkaClusterDocker-compose.yaml up -d
    ```

3.  Проверьте статус запущенных контейнеров:

    ```bash
    docker-compose -f KafkaClusterDocker-compose.yaml ps
    ```

### Запуск примеров Producer/Consumer

Примеры кода Producer и Consumer находятся в директории `src/main/java/oleborn/kafkaresearch`. Чтобы запустить их:

1.  Соберите проект с помощью Maven:

    ```bash
    mvn clean install
    ```

2.  Запустите Producer (пример):

    ```bash
    java -jar target/kafka-research-1.0-SNAPSHOT.jar producer
    ```

3.  Запустите Consumer (пример):

    ```bash
    java -jar target/kafka-research-1.0-SNAPSHOT.jar consumer
    ```

## Структура проекта

```
Kafka_Research/
├── src/
│   ├── main/
│   │   ├── java/oleborn/kafkaresearch/
│   │   │   └── ... (файлы Producer/Consumer)
│   │   └── resources/
│   └── test/
├── .gitignore
├── GUIDE.md
├── KafkaClusterDocker-compose.yaml
├── KafkaDocker-compose.yaml
├── README.md
└── pom.xml
```

*   `src/`: Исходный код проекта.
*   `GUIDE.md`: Подробное руководство по Apache Kafka.
*   `KafkaClusterDocker-compose.yaml`: Конфигурация Docker Compose для многоузлового кластера Kafka.
*   `KafkaDocker-compose.yaml`: Конфигурация Docker Compose для одноузлового кластера Kafka.
*   `pom.xml`: Файл конфигурации Maven.

## Контакты

Если у вас есть вопросы, свяжитесь с автором: Oleborn.

