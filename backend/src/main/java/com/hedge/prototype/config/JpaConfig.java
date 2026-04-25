package com.hedge.prototype.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * JPA 설정 — 감사 추적(Auditing) 활성화
 *
 * @see K-IFRS 1107호 (금융상품 공시 — 변경 이력 보존)
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {

    /**
     * 현재 사용자 제공자.
     * PoC: 고정 사용자 반환.
     * 본 개발 시 Spring Security 연동으로 교체.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of("system");
    }
}
