package com.moveinsync.settlement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MoveInSync Vendor & Driver Settlement Engine API")
                        .version("1.0.0")
                        .description("Production-grade month-end settlement, contract rate resolution, cumulative TDS, invoice reconciliation, and dispute adjustment engine.")
                        .contact(new Contact()
                                .name("MoveInSync Engineering Team")
                                .email("dev@moveinsync.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.moveinsync.com")));
    }
}
