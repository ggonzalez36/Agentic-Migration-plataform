package com.enterprise.agentops.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación stateless basado en Tokens / API Keys corporativas.
 * Asigna roles de control de acceso (RBAC): ROLE_ARCHITECT, ROLE_OPERATOR o ROLE_AUDITOR.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-KEY";
    private static final String ARCHITECT_KEY = "key-enterprise-architect-secops";
    private static final String OPERATOR_KEY = "key-operator-cicd-runner";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            if (ARCHITECT_KEY.equals(apiKey)) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "lead-architect",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ARCHITECT"),
                                new SimpleGrantedAuthority("ROLE_OPERATOR"),
                                new SimpleGrantedAuthority("ROLE_AUDITOR"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else if (OPERATOR_KEY.equals(apiKey)) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        "cicd-operator",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
