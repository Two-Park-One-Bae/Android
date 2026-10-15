package app.nursemate.core.timer

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 남은 시간을 초 단위로 줄이는 화면이 함께 쓰는 시각 흐름 — 홈 · 타이머 탭.
 *
 * 모으기 시작하면 **바로 지금 시각**을 내고 [periodMs] 마다 다시 낸다. 모으는 쪽이 사라지면 멈춘다.
 * 같은 반복문이 화면마다 따로 있으면 주기 · 시작 시점이 어긋나 두 탭의 남은 시간이 1초씩 달라진다.
 */
fun clockTicks(periodMs: Long = TICK_MS): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}

/** 남은 시간을 그리는 주기. */
const val TICK_MS = 1_000L
