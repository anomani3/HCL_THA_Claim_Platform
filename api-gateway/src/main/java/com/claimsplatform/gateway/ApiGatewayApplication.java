package com.claimsplatform.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /** Open http://localhost:8080 and land on Swagger UI. */
    @Bean
    RouterFunction<ServerResponse> rootRedirect() {
        return RouterFunctions.route(GET("/"),
                request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui.html")).build());
    }
}
