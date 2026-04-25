# /test-domain — 도메인 단위 테스트만 빠르게 실행

Spring 컨텍스트 없이 도메인 단위 테스트만 실행해줘. (빠른 피드백용)

## 실행 커맨드

```bash
cd backend && ./gradlew test \
  --tests "com.hedge.prototype.hedge.domain.*" \
  --tests "com.hedge.prototype.valuation.domain.*" \
  --tests "com.hedge.prototype.effectiveness.domain.*" \
  --tests "com.hedge.prototype.journal.domain.*"
```

## 실행 후 처리

1. 테스트 결과 요약 출력
2. 실패한 테스트가 있으면:
   - 실패 원인 (예외 메시지, 기댓값 vs 실제값) 분석
   - 코드 수정이 필요한지 vs 테스트 수정이 필요한지 판단
   - 수정 방법 제안
3. 전체 통과 시: "✅ 도메인 테스트 N개 모두 통과"

## 참고
통합 테스트까지 포함하려면 `/check` 커맨드를 사용해줘.
