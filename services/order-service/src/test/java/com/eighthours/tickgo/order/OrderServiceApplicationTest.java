package com.eighthours.tickgo.order;

import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OrderServiceApplicationTest {

    @Test
    void shouldNotExcludeRocketMqAutoConfiguration() {
        SpringBootApplication annotation = OrderServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(Arrays.asList(annotation.exclude()))
                .doesNotContain(RocketMQAutoConfiguration.class);
    }
}
