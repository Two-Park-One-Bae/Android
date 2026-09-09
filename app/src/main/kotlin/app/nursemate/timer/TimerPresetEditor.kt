package app.nursemate.timer

import app.nursemate.core.data.timer.TimerPresetRepository
import app.nursemate.core.model.TimerCategory
import app.nursemate.core.model.TimerPreset
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 프리셋 추가·수정·삭제 — 정본 `C3 프리셋 시트 — 편집 모드` · `C3 프리셋 추가` ·
 * `C3 프리셋 편집` · `C3 프리셋 삭제 확인`.
 *
 * ## 리스트와 분리한 이유
 * 편집은 **시트 안에서만 사는 상태**다(모드 · 열린 폼 · 삭제 확인). 리스트 ViewModel 에
 * 같이 두면 화면 상태와 시트 상태가 뒤섞여 어느 쪽이 화면을 흔드는지 흐려진다.
 */
class TimerPresetEditor(private val repository: TimerPresetRepository, private val scope: CoroutineScope) {

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    /** 열려 있는 편집 폼. 값이 있으면 시트가 뜬다. */
    private val _form = MutableStateFlow<PresetForm?>(null)
    val form: StateFlow<PresetForm?> = _form.asStateFlow()

    /** 삭제 확인을 기다리는 프리셋. 되돌릴 수 없어 한 번 묻는다(정본 삭제 확인 모달). */
    private val _deleting = MutableStateFlow<TimerPreset?>(null)
    val deleting: StateFlow<TimerPreset?> = _deleting.asStateFlow()

    fun toggleEditing() {
        _editing.value = !_editing.value
    }

    /** 시트가 닫히면 편집 모드도 푼다 — 다음에 열었을 때 편집 상태로 남아 있으면 놀란다. */
    fun reset() {
        _editing.value = false
        _form.value = null
        _deleting.value = null
    }

    fun add() {
        _form.value = PresetForm(null)
    }

    fun edit(preset: TimerPreset) {
        _form.value = PresetForm(preset)
    }

    fun closeForm() {
        _form.value = null
    }

    fun askDelete(preset: TimerPreset) {
        _deleting.value = preset
    }

    fun cancelDelete() {
        _deleting.value = null
    }

    fun save(label: String, category: TimerCategory, seconds: Int) {
        val existing = _form.value?.preset
        val next = existing?.copy(label = label, category = category, durationSeconds = seconds)
            ?: TimerPreset(
                id = UUID.randomUUID().toString(),
                label = label,
                category = category,
                durationSeconds = seconds,
                // 기본 6종과 구분한다. 사용자가 만든 것은 되살리지 않는다.
                isDefault = false,
                // 실제 순서는 저장소가 맨 뒤로 매긴다.
                sortOrder = Int.MAX_VALUE
            )
        scope.launch {
            repository.upsert(next)
            _form.value = null
        }
    }

    /** 확인을 거친 삭제. 폼에서 지웠으면 폼도 함께 닫는다. */
    fun confirmDelete() {
        val target = _deleting.value ?: return
        scope.launch {
            repository.delete(target.id)
            _deleting.value = null
            if (_form.value?.preset?.id == target.id) _form.value = null
        }
    }
}

/** 열린 편집 폼. [preset] 이 null 이면 추가다. */
data class PresetForm(val preset: TimerPreset?)
