package com.claimsplatform.reporting;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("Reporting Service")
                        .description("Workload, liability exposure and performance reports (read model built from Kafka events)")
                        .version("1.0.0"))
                .servers(List.of(new Server().url("/")));
    }
}
