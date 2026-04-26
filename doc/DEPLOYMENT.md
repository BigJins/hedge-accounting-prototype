# 배포 가이드

이 프로젝트는 포트폴리오 시연 목적의 PoC이므로, 프런트엔드와 백엔드를 분리해서 배포한다.

## 권장 구조

```text
Vercel Frontend
  -> Render Backend (Spring Boot)
  -> Render PostgreSQL
```

## Backend - Render

1. Render에서 PostgreSQL을 생성한다.
2. Web Service를 생성하고 GitHub 저장소를 연결한다.
3. Root Directory를 `backend`로 설정한다.
4. Runtime은 Docker를 선택한다.
5. 환경변수를 설정한다.

| Key | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | Render PostgreSQL JDBC URL |
| `DB_USERNAME` | Render PostgreSQL username |
| `DB_PASSWORD` | Render PostgreSQL password |
| `APP_CORS_ALLOWED_ORIGINS` | Vercel 프런트엔드 주소. 예: `https://hedge-demo.vercel.app` |

`DB_URL`은 다음 형식이어야 한다.

```text
jdbc:postgresql://<host>:<port>/<database>
```

## Frontend - Vercel

1. Vercel에서 GitHub 저장소를 연결한다.
2. Root Directory를 `frontend`로 설정한다.
3. Build Command는 `npm run build`로 둔다.
4. Output Directory는 `dist`로 둔다.
5. 환경변수를 설정한다.

| Key | Value |
|---|---|
| `VITE_API_BASE_URL` | Render 백엔드 주소 + `/api`. 예: `https://hedge-backend.onrender.com/api` |

## 배포 후 확인

1. 프런트 링크 접속
2. 공정가치 평가 등록
3. 헤지 지정
4. 유효성 테스트 실행
5. 자동 분개 이력 조회
6. IRS FVH 상각 분개 카드 확인

## 제출 시 표기

- 배포 링크: Vercel URL
- 백엔드 API: Render URL
- GitHub: Private Repository 접근 권한 부여 또는 ZIP 제출
