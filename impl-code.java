// File: src/main/java/com/dataplatform/Application.java
package com.dataplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableKafka
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

// File: src/main/java/com/dataplatform/models/events/BaseEvent.java
package com.dataplatform.models.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class BaseEvent {
    @JsonProperty("event_id")
    private String eventId = UUID.randomUUID().toString();
    
    @JsonProperty("event_timestamp")
    private Instant eventTimestamp = Instant.now();
    
    @JsonProperty("user_id")
    private String userId;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    @JsonProperty("platform")
    private String platform;
    
    @JsonProperty("app_version")
    private String appVersion;
    
    @JsonProperty("ip_address")
    private String ipAddress;
}

// File: src/main/java/com/dataplatform/models/events/ProductViewEvent.java
package com.dataplatform.models.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductViewEvent extends BaseEvent {
    @JsonProperty("product_id")
    private String productId;
    
    @JsonProperty("category_id")
    private String categoryId;
    
    @JsonProperty("view_duration_seconds")
    private Integer viewDurationSeconds;
    
    @JsonProperty("referrer_page")
    private String referrerPage;
    
    @JsonProperty("is_recommendation")
    private Boolean isRecommendation;
    
    @JsonProperty("search_query")
    private String searchQuery;
}

// File: src/main/java/com/dataplatform/models/events/PurchaseEvent.java
package com.dataplatform.models.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PurchaseEvent extends BaseEvent {
    @JsonProperty("order_id")
    private String orderId;
    
    @JsonProperty("product_ids")
    private List<String> productIds;
    
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    
    @JsonProperty("currency")
    private String currency;
    
    @JsonProperty("payment_method")
    private String paymentMethod;
    
    @JsonProperty("shipping_method")
    private String shippingMethod;
    
    @JsonProperty("coupon_code")
    private String couponCode;
    
    @JsonProperty("is_gift")
    private Boolean isGift;
}

// File: src/main/java/com/dataplatform/config/KafkaConfig.java
package com.dataplatform.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.topics.product-views.partitions:10}")
    private Integer productViewsPartitions;

    @Value("${spring.kafka.topics.product-views.replication-factor:3}")
    private Short productViewsReplicationFactor;

    @Value("${spring.kafka.topics.purchases.partitions:10}")
    private Integer purchasesPartitions;

    @Value("${spring.kafka.topics.purchases.replication-factor:3}")
    private Short purchasesReplicationFactor;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public NewTopic productViewsTopic() {
        return new NewTopic("product-views", productViewsPartitions, productViewsReplicationFactor);
    }

    @Bean
    public NewTopic purchasesTopic() {
        return new NewTopic("purchases", purchasesPartitions, purchasesReplicationFactor);
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // Configure error handling with retry
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(1000L, 3));
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }
}

// File: src/main/java/com/dataplatform/producers/EventProducerService.java
package com.dataplatform.producers;

import com.dataplatform.models.events.BaseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventProducerService {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public <T extends BaseEvent> void sendEvent(String topic, T event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String key = event.getUserId() != null ? event.getUserId() : event.getEventId();
            String value = objectMapper.writeValueAsString(event);
            
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, value);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    sample.stop(meterRegistry.timer("kafka.producer", "topic", topic, "result", "success"));
                    log.debug("Event successfully sent to topic: {}, partition: {}, offset: {}", 
                        topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                } else {
                    sample.stop(meterRegistry.timer("kafka.producer", "topic", topic, "result", "failure"));
                    log.error("Failed to send event to topic: {}, error: {}", topic, ex.getMessage(), ex);
                    
                    // Count failures by topic and error type
                    meterRegistry.counter("kafka.producer.failures", 
                        "topic", topic, 
                        "error", ex.getClass().getSimpleName()).increment();
                }
            });
            
            // Count events produced by topic
            meterRegistry.counter("kafka.producer.events", "topic", topic).increment();
            
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("kafka.producer", "topic", topic, "result", "error"));
            log.error("Error serializing event: {}", e.getMessage(), e);
            
            // Count serialization errors
            meterRegistry.counter("kafka.producer.errors", 
                "topic", topic, 
                "type", "serialization").increment();
                
            throw new RuntimeException("Error sending event to Kafka", e);
        }
    }
}

// File: src/main/java/com/dataplatform/controllers/EventController.java
package com.dataplatform.controllers;

import com.dataplatform.models.events.ProductViewEvent;
import com.dataplatform.models.events.PurchaseEvent;
import com.dataplatform.producers.EventProducerService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {
    private final EventProducerService producerService;
    private final MeterRegistry meterRegistry;
    
    @PostMapping("/product-view")
    public ResponseEntity<String> recordProductView(@RequestBody @Validated ProductViewEvent event) {
        log.debug("Received product view event for product: {}", event.getProductId());
        
        // Ensure timestamp is set
        if (event.getEventTimestamp() == null) {
            event.setEventTimestamp(Instant.now());
        }
        
        // Track metrics
        meterRegistry.counter("api.events", 
            "type", "product_view", 
            "product_id", event.getProductId()).increment();
        
        // Send to Kafka
        producerService.sendEvent("product-views", event);
        
        return ResponseEntity.ok("Event recorded successfully");
    }
    
    @PostMapping("/purchase")
    public ResponseEntity<String> recordPurchase(@RequestBody @Validated PurchaseEvent event) {
        log.debug("Received purchase event for order: {}", event.getOrderId());
        
        // Ensure timestamp is set
        if (event.getEventTimestamp() == null) {
            event.setEventTimestamp(Instant.now());
        }
        
        // Track metrics
        meterRegistry.counter("api.events", 
            "type", "purchase", 
            "payment_method", event.getPaymentMethod()).increment();
        
        meterRegistry.gauge("purchase.amount", 
            event.getTotalAmount().doubleValue());
        
        // Send to Kafka
        producerService.sendEvent("purchases", event);
        
        return ResponseEntity.ok("Purchase recorded successfully");
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Event service is healthy");
    }
}

// File: src/main/java/com/dataplatform/consumers/EventConsumerService.java
package com.dataplatform.consumers;

import com.dataplatform.models.events.ProductViewEvent;
import com.dataplatform.models.events.PurchaseEvent;
import com.dataplatform.services.AnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EventConsumerService {
    private final ObjectMapper objectMapper;
    private final AnalyticsService analyticsService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "product-views", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeProductViewEvent(String message, Acknowledgment acknowledgment) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ProductViewEvent event = objectMapper.readValue(message, ProductViewEvent.class);
            log.debug("Consumed product view event: {}", event.getEventId());
            
            // Process the event
            analyticsService.processProductViewEvent(event);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            // Record metrics
            sample.stop(meterRegistry.timer("kafka.consumer", "topic", "product-views", "result", "success"));
            meterRegistry.counter("kafka.consumer.events", "topic", "product-views").increment();
            
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("kafka.consumer", "topic", "product-views", "result", "error"));
            log.error("Error processing product view event: {}", e.getMessage(), e);
            
            // Don't acknowledge - let the message be redelivered
            // In a real system, we might implement a dead letter queue after X retries
            
            // Record error metrics
            meterRegistry.counter("kafka.consumer.errors", 
                "topic", "product-views", 
                "type", e.getClass().getSimpleName()).increment();
        }
    }

    @KafkaListener(topics = "purchases", groupId = "${spring.kafka.consumer.group-id}")
    public void consumePurchaseEvent(String message, Acknowledgment acknowledgment) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            PurchaseEvent event = objectMapper.readValue(message, PurchaseEvent.class);
            log.debug("Consumed purchase event: {}", event.getEventId());
            
            // Process the event
            analyticsService.processPurchaseEvent(event);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            // Record metrics
            sample.stop(meterRegistry.timer("kafka.consumer", "topic", "purchases", "result", "success"));
            meterRegistry.counter("kafka.consumer.events", "topic", "purchases").increment();
            
        } catch (Exception e) {
            sample.stop(meterRegistry.timer("kafka.consumer", "topic", "purchases", "result", "error"));
            log.error("Error processing purchase event: {}", e.getMessage(), e);
            
            // Record error metrics
            meterRegistry.counter("kafka.consumer.errors", 
                "topic", "purchases", 
                "type", e.getClass().getSimpleName()).increment();
        }
    }
}
