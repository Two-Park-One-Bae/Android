package app.nursemate.core.network.di

import javax.inject.Qualifier

/**
 * 인터셉터가 없는 `OkHttpClient`.
 *
 * 우리 서버가 아닌 곳(S3 presigned 업로드 등)으로 보내는 요청에만 쓴다.
 * 기본 클라이언트에는 인증 헤더·에러 변환·401 재시도가 붙어 있어 맞지 않는다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient
