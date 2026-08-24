package app.nursemate.core.model

/**
 * 도메인 모델 자리 — P1(네트워크 기반)·P2(Pill API)에서 openapi v0.23.0 기준으로 채운다.
 * 서버 enum은 항상 UNKNOWN fallback을 갖는다 (spec/api/domains/enums.md).
 */
data class Pill(val pillCode: String)
