package app.nursemate.pill

import app.nursemate.core.model.PillAttribute

/**
 * 알약 하나에 대해 사용자가 고친 값.
 *
 * 속성(색·모양·제형)과 각인을 **한 덩이로** 들고 다닌다. 둘을 따로 두면 화면마다 두 곳에서
 * 꺼내 합쳐야 하는데, 한 군데서 빠뜨리면 고친 값이 안 보이는 화면이 생긴다.
 *
 * @param attribute 색·모양·제형. 앞뒤 각인은 [faces] 가 정본이라 여기 실린 값은 쓰지 않는다.
 * @param faces 각인 입력. '해당 없음'을 담아야 해서 [PillAttribute] 와 따로 둔다([FaceInput]).
 */
data class PillEdit(val attribute: PillAttribute, val faces: FaceInputs)
