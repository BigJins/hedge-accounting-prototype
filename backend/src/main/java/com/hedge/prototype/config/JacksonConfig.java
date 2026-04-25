package com.hedge.prototype.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson ObjectMapper 설정.
 *
 * <p>Jackson 3.x (Spring Boot 4)에서 일부 Feature가 이동:
 * <ul>
 *   <li>{@code WRITE_BIGDECIMAL_AS_PLAIN} → {@link StreamWriteFeature}</li>
 *   <li>{@code WRITE_DATES_AS_TIMESTAMPS} → {@link DateTimeFeature}</li>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES} → {@link DeserializationFeature}</li>
 * </ul>
 * application.yml의 spring.jackson.* serialization/deserialization 설정은
 * Jackson 3.x에서 동작하지 않으므로 코드로 직접 구성.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public JsonMapper objectMapper() {
        return JsonMapper.builder()
                // BigDecimal 지수 표기법 방지 (금융 필수) — 예: 1.23E+8 → 123000000
                .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                // 날짜를 타임스탬프 숫자가 아닌 ISO-8601 문자열로 직렬화
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 알 수 없는 JSON 필드 무시 (API 하위 호환성 유지)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
