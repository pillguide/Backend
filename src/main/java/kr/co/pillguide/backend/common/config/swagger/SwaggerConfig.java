package kr.co.pillguide.backend.common.config.swagger;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;


@OpenAPIDefinition(
        info = @Info(title = "약학다식 API 명세서", description = "약학다식 API 명세서", version = "v1"),
        servers = {
                @Server(url = "${openapi.server.local}", description = "로컬 서버 URL"),
                @Server(url = "${openapi.server.prod}", description = "운영 서버 URL")
        }
)
@Configuration
public class SwaggerConfig {

    @Value("${jwt.access.header}")
    private String accessTokenHeader;

    @Bean
    public OpenAPI openAPI() {

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER);

        SecurityRequirement securityRequirement = new SecurityRequirement().addList(accessTokenHeader);

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("bearer", securityScheme))
                .security(Collections.singletonList(securityRequirement));
    }
}
