package br.com.biosolar.citrus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: permite que o dashboard seja servido por outra origem local (ex.: Live Server na porta 5500)
 * ou aberto como arquivo (origem "null"). Origens externas sao recusadas por padrao; configure
 * {@code BIOSOLAR_CORS_ORIGENS} para publicar em outro dominio. A API nao usa cookies nem
 * credenciais, por isso {@code allowCredentials} permanece desativado.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BioSolarProperties properties;

    public WebConfig(BioSolarProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origens = properties.cors().origensPermitidas().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(origens)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
