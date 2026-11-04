package com.example.sporty.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.stream.binder.ExtendedProducerProperties;
import org.springframework.cloud.stream.binder.kafka.common.KafkaBinderEnvironmentPostProcessor;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaBinderConfigurationProperties;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaProducerProperties;
import org.springframework.cloud.stream.binder.kafka.utils.BindingUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaProducerSerializationTest {
    @Test
    void outboxUsesStringsWhileCloudBusKeepsByteArrays() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
                .withInitializer(context -> {
                    try {
                        var environment = context.getEnvironment();
                        new YamlPropertySourceLoader().load("serviceYaml", new ClassPathResource("application.yml"))
                                .forEach(environment.getPropertySources()::addLast);
                        new KafkaBinderEnvironmentPostProcessor()
                                .postProcessEnvironment(environment, new SpringApplication());
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(KafkaTemplate.class);
                    var template = context.getBean(KafkaTemplate.class);
                    var producer = template.getProducerFactory().getConfigurationProperties();
                    assertThat(producer.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
                    assertThat(producer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
                    var binder = new KafkaBinderConfigurationProperties(context.getBean(KafkaProperties.class),
                            context.getBeanProvider(KafkaConnectionDetails.class));
                    Binder.get(context.getEnvironment()).bind("spring.cloud.stream.kafka.binder", Bindable.ofInstance(binder));
                    var busProducer = BindingUtils.createProducerConfigs(
                            new ExtendedProducerProperties<>(new KafkaProducerProperties()), binder);
                    assertThat(busProducer.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                            .isEqualTo(ByteArraySerializer.class.getName());
                    assertThat(busProducer.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                            .isEqualTo(ByteArraySerializer.class.getName());
                });
    }
}
