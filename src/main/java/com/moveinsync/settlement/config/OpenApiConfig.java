package com.moveinsync.settlement.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "basicAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MoveInSync Vendor & Driver Settlement Engine API")
                        .version("1.0.0")
                        .description("Production-grade month-end settlement, contract rate resolution, cumulative TDS, invoice reconciliation, dispute delta adjustments, and audit trail.")
                        .contact(new Contact()
                                .name("MoveInSync Engineering Team")
                                .email("dev@moveinsync.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://www.moveinsync.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication using role credentials (admin/admin123, approver/approver123, employee/employee123)")));
    }
}
