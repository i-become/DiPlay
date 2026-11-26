package com.shilapi.xcertplay.newui.settings

enum class NewUiUpdateStage {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    VERIFYING,
    READY,
    FAILED,
}

data class NewUiUpdateUiState(
    val stage: NewUiUpdateStage = NewUiUpdateStage.IDLE,
    val message: String? = null,
    val progress: Int? = null,
    val releaseTag: String? = null,
    val releaseTitle: String? = null,
    val releaseDescription: String? = null,
    val savedPath: String? = null,
    val installPreparing: Boolean = false,
)
