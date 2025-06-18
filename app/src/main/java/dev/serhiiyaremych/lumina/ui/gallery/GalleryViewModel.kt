package dev.serhiiyaremych.lumina.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.serhiiyaremych.lumina.domain.model.Media
import dev.serhiiyaremych.lumina.domain.usecase.GetMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupMediaUseCase
import dev.serhiiyaremych.lumina.domain.usecase.GroupingPeriod
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val getMediaUseCase: GetMediaUseCase,
    private val groupMediaUseCase: GroupMediaUseCase
) : ViewModel() {

    var currentPeriod: GroupingPeriod = GroupingPeriod.DAILY
        set(value) {
            field = value
            groupMedia()
        }

    private val _mediaState = MutableStateFlow<List<Media>>(emptyList())
    val mediaState: StateFlow<List<Media>> = _mediaState.asStateFlow()

    private val _groupedMediaState = MutableStateFlow<Map<LocalDate, List<Media>>>(emptyMap())
    val groupedMediaState: StateFlow<Map<LocalDate, List<Media>>> = _groupedMediaState.asStateFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val media = getMediaUseCase()
            _mediaState.value = media
            groupMedia()
        }
    }

    private fun groupMedia() {
        _groupedMediaState.value = groupMediaUseCase(_mediaState.value, currentPeriod)
    }
}
