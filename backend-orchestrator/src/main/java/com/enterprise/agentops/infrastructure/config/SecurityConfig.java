package com.enterprise.agentops.infrastructure.config;

import com.enterprise.agentops.infrastructure.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuración Zero-Trust de Spring Security 6.
 * Enforce RBAC estricto, sesiones sin estado (Stateless), y cabeceras de endurecimiento bancario.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .xssProtection(HeadersConfigurer.XXssConfig::disable) // Modern browsers deprecate X-XSS-Protection in favor of CSP
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none';"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos de diagnóstico
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Observabilidad y métricas restringidas
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").hasAnyRole("ARCHITECT", "OPERATOR", "AUDITOR")
                        // Checkpoint HITL de aprobación: Requiere exclusivamente rol ARCHITECT (Principio de 4 ojos)
                        .requestMatchers("/migrations/tasks/*/approve").hasRole("ARCHITECT")
                        // Despacho de migración y lectura operativa
                        .requestMatchers("/migrations/**").hasAnyRole("ARCHITECT", "OPERATOR")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new ApiKeyAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
