package com.hedge.prototype.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 — PoC 시연용.
 *
 * <p>수주용 프로토타입이므로 인증/인가 없이 모든 API를 허용합니다.
 * CSRF는 REST API (Stateless) 특성상 비활성화합니다.
 *
 * <p>⚠️ 본개발 전환 시 반드시 인증/인가 정책 수립 후 재구성 필요.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // REST API — CSRF 토큰 불필요 (Stateless)
                .csrf(AbstractHttpConfigurer::disable)
                // PoC: 모든 요청 허용 (인증 없음)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
