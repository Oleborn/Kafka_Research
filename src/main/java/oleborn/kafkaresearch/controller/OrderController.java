package oleborn.kafkaresearch.controller;

import lombok.RequiredArgsConstructor;
import oleborn.kafkaresearch.model.dto.OrderEventDto;
import oleborn.kafkaresearch.model.entity.OrderEvent;
import oleborn.kafkaresearch.model.service.OrderService;
import oleborn.kafkaresearch.model.service.producer.KafkaProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final KafkaProducer kafkaProducer;
    private final OrderService orderService;

    @PostMapping("/standard")
    public ResponseEntity<String> sendStandardOrder(@RequestBody OrderEventDto orderEventDto) {
        kafkaProducer.sendStandard(orderEventDto.orderId(), orderEventDto);
        return ResponseEntity.ok("Order sent via standard producer");
    }

    @PostMapping("/transactional")
    public ResponseEntity<String> sendTransactionalOrder(@RequestBody OrderEventDto orderEventDto) {
        kafkaProducer.sendTransactional(orderEventDto.orderId(), orderEventDto);
        return ResponseEntity.ok("Order sent via transactional producer");
    }

    @PostMapping("/high-throughput")
    public ResponseEntity<String> sendHighThroughputOrder(@RequestBody OrderEventDto orderEventDto) {
        kafkaProducer.sendHighThroughput(orderEventDto.orderId(), orderEventDto);
        return ResponseEntity.ok("Order sent via high throughput producer");
    }

    @PostMapping("/low-latency")
    public ResponseEntity<String> sendLowLatencyOrder(@RequestBody OrderEventDto orderEventDto) {
        kafkaProducer.sendLowLatency(orderEventDto.orderId(), orderEventDto);
        return ResponseEntity.ok("Order sent via low latency producer");
    }

    @PostMapping("/reliable")
    public ResponseEntity<String> sendReliableOrder(@RequestBody OrderEventDto orderEventDto) {
        kafkaProducer.sendReliable(orderEventDto.orderId(), orderEventDto);
        return ResponseEntity.ok("Order sent via reliable producer");
    }

    @GetMapping
    public ResponseEntity<List<OrderEvent>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<List<OrderEvent>> getOrdersByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrdersByOrderId(orderId));
    }
}