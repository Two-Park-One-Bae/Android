package app.nursemate.core.model

import kotlinx.serialization.json.Json

/**
 * 폰↔워치 사이를 오가는 값의 표기.
 *
 * ## 한쪽만 업데이트돼도 죽지 않아야 한다
 * 폰과 워치는 **따로 배포되고 따로 업데이트된다**(Wear 앱은 별도 APK다). 새 필드가 붙은
 * 스냅샷이 옛 워치에 닿는 일이 정상 경로라 [Json.ignoreUnknownKeys] 를 켠다. 모르는 필드를
 * 만나 예외를 던지면 동기화가 통째로 멈춘다.
 *
 * 기본값이 있는 필드는 인코딩에서 생략된다 — 블루투스로 오가므로 짧을수록 좋다.
 */
private val wireJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

fun encodeSnapshot(snapshot: TimerSnapshot): String = wireJson.encodeToString(TimerSnapshot.serializer(), snapshot)

/** @return 못 읽으면 null. 옛 폰이 보낸 알 수 없는 모양이어도 워치가 죽지 않는다. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun decodeSnapshot(json: String): TimerSnapshot? = try {
    wireJson.decodeFromString(TimerSnapshot.serializer(), json)
} catch (e: Exception) {
    null
}

fun encodeCommand(command: TimerCommand): String = wireJson.encodeToString(TimerCommand.serializer(), command)

/**
 * @return 못 읽으면 null.
 *
 * ⚠️ **모르는 명령은 조용히 버린다.** 새 워치가 옛 폰에 보내는 경우다. 여기서 던지면
 * 리시버가 죽어 그 뒤 정상 명령까지 놓친다.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun decodeCommand(json: String): TimerCommand? = try {
    wireJson.decodeFromString(TimerCommand.serializer(), json)
} catch (e: Exception) {
    null
}

fun encodeReplica(replica: TimerReplica): String = wireJson.encodeToString(TimerReplica.serializer(), replica)

/** @return 못 읽으면 null. 상대가 새 필드를 붙여 보내도 여기서 멈추지 않는다. */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
fun decodeReplica(json: String): TimerReplica? = try {
    wireJson.decodeFromString(TimerReplica.serializer(), json)
} catch (e: Exception) {
    null
}
