package kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.config.ContainerProperties;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertThat;
import static org.springframework.kafka.test.hamcrest.KafkaMatchers.*;

/**
 * https://docs.spring.io/spring-kafka/docs/1.3.10.RELEASE/reference/html/_introduction.html#_with_java_configuration
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {KafkaTemplateJavaConfigTest.Config.class}, loader = AnnotationConfigContextLoader.class)
public class KafkaTemplateJavaConfigTest {
    private static final String TOPIC = "TOPIC";

    @Autowired
    private KafkaTemplate<Integer, String> template;
    private BlockingQueue<ConsumerRecord<Integer, String>> receivedKafkaMessages = new LinkedBlockingQueue<>();

    @ClassRule
    public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, TOPIC);

    @Test
    public void testSimple() throws Exception {
        createKafkaMessageListenerContainer();

        template.send(TOPIC, 0, "foo");
        template.flush();

        ConsumerRecord<Integer, String> received = receivedKafkaMessages.poll(10, TimeUnit.SECONDS);
        assertThat(received, hasKey(0));
        assertThat(received, hasPartition(0));
        assertThat(received, hasValue("foo"));
    }

    private void createKafkaMessageListenerContainer() throws Exception {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("testT", "false",
                embeddedKafka);
        DefaultKafkaConsumerFactory<Integer, String> cf =
                new DefaultKafkaConsumerFactory<Integer, String>(consumerProps);
        ContainerProperties containerProperties = new ContainerProperties(TOPIC);
        KafkaMessageListenerContainer<Integer, String> container =
                new KafkaMessageListenerContainer<>(cf, containerProperties);
        container.setupMessageListener(new MessageListener<Integer, String>() {
            @Override
            public void onMessage(ConsumerRecord<Integer, String> record) {
                receivedKafkaMessages.add(record);
            }
        });
        container.setBeanName("templateTests");
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @Configuration
    @EnableKafka
    static class Config {

        @Bean
        ConcurrentKafkaListenerContainerFactory<Integer, String> kafkaListenerContainerFactory() {
            ConcurrentKafkaListenerContainerFactory<Integer, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory());
            return factory;
        }

        @Bean
        public ConsumerFactory<Integer, String> consumerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

            return new DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        public ProducerFactory<Integer, String> producerFactory() {
            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        public KafkaTemplate<Integer, String> kafkaTemplate() {
            return new KafkaTemplate<>(producerFactory());
        }

    }
}

