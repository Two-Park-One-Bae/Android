package app.nursemate.home

import app.nursemate.core.designsystem.v2.NmSymbols

/** 하단 탭 4종 — V2.1.0 「Tab Bar」. 아이콘은 Material Symbols(NM-608). */
enum class NmTab(val label: String, val iconRes: Int) {
    Home("홈", NmSymbols.Home),
    Pill("알약", NmSymbols.Pill),
    Timer("타이머", NmSymbols.Timer),
    Settings("설정", NmSymbols.Settings)
}
