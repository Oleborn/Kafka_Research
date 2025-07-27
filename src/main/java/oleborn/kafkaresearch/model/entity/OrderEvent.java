package oleborn.kafkaresearch.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import oleborn.kafkaresearch.model.dictionary.OrderEventType;
import oleborn.kafkaresearch.model.dictionary.OrderStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "orders")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonProperty("orderId")
    @Column(name = "order_id")
    private String orderId;

    @JsonProperty("userId")
    @Column(name = "user_id")
    private String userId;

    @JsonProperty("eventType")
    @Column(name = "event_type")
    @Enumerated(EnumType.STRING)
    private OrderEventType eventType;

    @JsonProperty("orderStatus")
    @Column(name = "order_status")
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    @JsonProperty("totalAmount")
    @Column(name = "total_amount", precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @JsonProperty("currency")
    @Column(name = "currency")
    private String currency;

    @JsonProperty("items")
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @JsonProperty("createdAt")
    @CreationTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    @UpdateTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("paymentMethod")
    @Column(name = "payment_method")
    private String paymentMethod;

    @JsonProperty("shippingAddress")
    @Column(name = "shipping_address")
    private String shippingAddress;
}