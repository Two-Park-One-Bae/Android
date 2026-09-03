package app.nursemate.home

import app.nursemate.core.designsystem.R as DsR

/** 하단 탭. spec/design hero-home-real.png 기준 4종. */
enum class NmTab(val label: String, val iconRes: Int) {
    Home("홈", DsR.drawable.nm_ic_home),
    Pill("알약", DsR.drawable.nm_ic_pill),
    Timer("타이머", DsR.drawable.nm_ic_timer),
    Settings("설정", DsR.drawable.nm_ic_settings)
}
