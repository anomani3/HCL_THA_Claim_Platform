package com.claimsplatform.claims.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    NewTopic claimEventsTopic(@Value("${claims.kafka.topic}") String topic,
                              @Value("${claims.kafka.partitions}") int partitions,
                              @Value("${claims.kafka.replicas}") int replicas) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Claims Service")
                        .description("Claim intake, lifecycle and officer workload APIs")
                        .version("1.0.0"))
                // "/" => Swagger UI "Try it out" calls go back through whichever host served the UI (the gateway)
                .servers(java.util.List.of(new Server().url("/")));
    }
}
