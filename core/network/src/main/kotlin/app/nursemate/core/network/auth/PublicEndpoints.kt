package app.nursemate.core.network.auth

/**
 * 토큰 없이 부르는 경로.
 *
 * 서버 `WebConfig` 와 짝을 맞춘다 — 거기서 `/api` 하위 전체에 인증 인터셉터를 걸고 아래 둘만 뺀다.
 * 헬스 체크는 `/api` 밖이라 애초에 안 걸린다.
 *
 * ⚠️ **경로 단위라 메서드를 구분하지 않는다.** 서버도 같다. 이 경로에 다른 메서드를 추가하면
 * 그것도 인증 없이 나간다.
 *
 * 정본: `spec/api/openapi.yaml` 의 `security: []` 오퍼레이션.
 */
internal val PUBLIC_PATHS = setOf(
    "/actuator/health",
    "/api/v0/auth/kakao/token",
    "/api/v0/consents"
)

/** 경로가 공개 목록에 있는지. 쿼리스트링은 이미 떨어져 나온 값이 들어온다. */
internal fun isPublicPath(path: String): Boolean = path in PUBLIC_PATHS
