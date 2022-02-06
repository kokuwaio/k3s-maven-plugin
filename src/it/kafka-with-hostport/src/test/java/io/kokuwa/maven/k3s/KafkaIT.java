package io.kokuwa.maven.k3s;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.UUIDDeserializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class KafkaIT {

	KafkaProducer<String, UUID> producer;
	KafkaConsumer<String, UUID> consumer;

	@BeforeEach
	void setUp() {
		var config = Map.<String, Object>of(
				"group.id", "test",
				"bootstrap.servers", "kafka.127.0.0.1.nip.io:9091",
				"acks", "all",
				"auto.offset.reset", "earliest",
				"auto.commit.interval.ms", "1000",
				"key.serializer", StringSerializer.class.getName(),
				"key.deserializer", StringDeserializer.class.getName(),
				"value.serializer", UUIDSerializer.class.getName(),
				"value.deserializer", UUIDDeserializer.class.getName());
		producer = new KafkaProducer<>(config);
		consumer = new KafkaConsumer<>(config);
	}

	@Test
	void test() throws InterruptedException, ExecutionException {

		var topic = "test";
		var key = UUID.randomUUID().toString();
		var value = UUID.randomUUID();

		producer.send(new ProducerRecord<>(topic, key, value)).get();

		consumer.subscribe(Set.of(topic));
		var consumerRecords = consumer.poll(Duration.ofSeconds(60));
		consumer.commitSync();
		assertEquals(1, consumerRecords.count(), "count");
		var consumerRecord = consumerRecords.iterator().next();
		assertEquals(key, consumerRecord.key(), "key");
		assertEquals(value, consumerRecord.value(), "value");

	}
}
