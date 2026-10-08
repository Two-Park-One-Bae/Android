package app.nursemate.core.model

import app.nursemate.core.model.serialization.FallbackEnumSerializer
import kotlinx.serialization.Serializable

/**
 * 로그인 공급자.
 *
 * 정본 `spec/api/openapi.yaml` 의 `AuthProvider`. 네이버 등이 추가돼도 앱이 깨지지 않게
 * [UNKNOWN] 을 둔다 — 서버가 값을 하나 늘리는 순간 구버전이 응답 전체를 못 읽는 일을 막는다.
 */
@Serializable(with = AuthProviderSerializer::class)
enum class AuthProvider { GOOGLE, APPLE, KAKAO, UNKNOWN }

object AuthProviderSerializer : FallbackEnumSerializer<AuthProvider>(
    "AuthProvider",
    AuthProvider.entries.toTypedArray(),
    AuthProvider.UNKNOWN
)

/**
 * 동의 항목.
 *
 * **필수 여부는 앱이 정하지 않는다** — 서버가 `ConsentDefinition.required` 로 내려준다
 * (spec §선택 동의). 아래 주석의 필수·선택은 지금 서버가 주는 값을 적어 둔 것이다.
 *
 * - [TERMS] · [PRIVACY] — 필수
 * - [OVERSEAS] — **선택**. 광고 유입 측정에 따른 국외 이전·제3자 제공(NM-548).
 *   거부해도 앱을 그대로 쓴다. 이 항목이 `agreed && satisfied` 일 때만 측정 SDK 를 켠다
 */
@Serializable(with = ConsentTypeSerializer::class)
enum class ConsentType { TERMS, PRIVACY, OVERSEAS, UNKNOWN }

object ConsentTypeSerializer : FallbackEnumSerializer<ConsentType>(
    "ConsentType",
    ConsentType.entries.toTypedArray(),
    ConsentType.UNKNOWN
)

/**
 * 항목별 동의 상태.
 *
 * [agreed] 와 [satisfied] 는 다르다 — 예전 버전에 동의했다면 `agreed=true` 인데
 * `satisfied=false` 다(약관이 개정된 경우). 게이트 판단은 [satisfied] 로 한다.
 */
@Serializable
data class ConsentStatus(
    val type: ConsentType,
    val agreed: Boolean,
    /** 동의한 문서 버전(시행일 `YYYY-MM-DD`). 미동의면 null. */
    val version: String? = null,
    val satisfied: Boolean
)

/**
 * 회원.
 *
 * 정본 `spec/api/domains/auth.md`. `userId` 는 Firebase UID 를 그대로 쓴다 — 별도 대리키가 없다.
 *
 * `createdAt` 은 `date-time` 문자열 그대로 둔다. 지금 쓸 데가 없고, 파싱하려면
 * kotlinx-datetime 을 들여야 한다. 필요해지면 그때 타입을 올린다.
 */
@Serializable
data class User(
    val userId: String,
    val provider: AuthProvider,
    val providerUserId: String,
    val createdAt: String,
    val consents: List<ConsentStatus> = emptyList(),
    /**
     * 필수 동의 미충족 여부. true 면 동의 온보딩으로 보낸다.
     *
     * ⚠️ **클라이언트가 직접 계산하지 않는다.** [consents] 를 훑어 판단하면 서버가 필수 항목을
     * 늘렸을 때 어긋난다. 서버가 준 이 값을 그대로 믿는다(spec §동의 온보딩).
     */
    val onboardingRequired: Boolean
) {
    /**
     * 약관 **개정**으로 다시 묻는 것인가 — 예전 버전에 동의한 기록이 있는데 지금은 미충족.
     *
     * 최초 가입자와 갈라 **안내 문구만** 정하는 값이다(spec §개정 재동의). 들여보낼지 말지는
     * 그대로 [onboardingRequired] 가 쥔다 — 위의 「클라이언트가 직접 계산하지 않는다」는
     * **그 게이트**에 대한 말이고, 여기서 그 판단을 다시 하지 않는다. 이 값이 틀려도 문구만
     * 어긋날 뿐, 막을 사람을 들이거나 들여보낼 사람을 막지 않는다.
     *
     * `agreed && !satisfied` = 「동의는 했는데 그 버전이 지금 필수 버전이 아니다」 = 개정.
     * 한 번도 동의한 적 없는 항목은 `agreed == false` 라 걸리지 않는다.
     *
     * ## ⚠️ **선택 항목은 세지 않는다** (NM-548)
     * spec 이 「동의 기록이 있는 **필수** 항목」으로 못박는다. [ConsentType.OVERSEAS] 는
     * 옛 버전에 동의한 상태로 오래 남을 수 있는데, 그걸 세면 **선택 항목 하나 때문에
     * 「약관이 변경되었어요」가 뜬다** — 필수 약관은 그대로인데.
     *
     * [ConsentStatus] 에는 필수 여부가 없어(서버는 `ConsentDefinition.required` 로 준다)
     * 타입으로 가른다. 서버가 선택 항목을 더 늘리면 여기도 함께 고쳐야 한다 — 틀려도
     * 문구만 어긋나는 자리라 이 정도로 둔다.
     */
    val needsReconsent: Boolean
        get() = consents.any { it.type !in OPTIONAL_TYPES && it.agreed && !it.satisfied }

    /**
     * 국외 이전·제3자 제공에 **지금 버전으로** 동의했는가 — 측정 SDK 를 켜는 유일한 조건이다.
     *
     * `agreed && satisfied` 둘 다 본다. 옛 버전에 동의한 상태(`agreed && !satisfied`)는
     * **미동의로 다룬다**(spec §선택 동의) — 고지사항이 바뀌었는데 옛 동의로 계속 보내면 안 된다.
     */
    val overseasConsented: Boolean
        get() = consents.any { it.type == ConsentType.OVERSEAS && it.agreed && it.satisfied }

    private companion object {
        /** 서버가 선택으로 내려주는 항목. [needsReconsent] 가 세지 않는다. */
        val OPTIONAL_TYPES = setOf(ConsentType.OVERSEAS)
    }
}

/**
 * 서버가 정의한 동의 항목 — `GET /api/v0/consents` 응답.
 *
 * ⚠️ **버전·항목명·URL 을 앱에 하드코딩하지 않는다**(spec §동의 온보딩). 약관을 개정하면
 * 서버가 [version] 을 올리고, 앱은 받은 값 그대로 화면을 그리고 그대로 되돌려 보낸다.
 * 앱에 박아 두면 개정 때마다 스토어 심사를 기다려야 한다.
 */
@Serializable
data class ConsentDefinition(
    val type: ConsentType,
    /** 게시본 시행일 `YYYY-MM-DD`(동일자 재개정은 `.N`). */
    val version: String,
    val required: Boolean,
    /** 전문 URL. '보기'가 이 주소를 연다. */
    val policyUrl: String,
    /** 표시용 항목명. 화면 문구는 이걸 쓴다. */
    val title: String
)

/** 동의 저장 요청 항목 — `POST /api/v0/users/me/consents`. */
@Serializable
data class ConsentAgreement(val type: ConsentType, val version: String, val agreed: Boolean)
