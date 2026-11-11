package com.grabber.model

/** Stato della trascrizione offline di una registrazione, salvato nel database con il suo nome. */
enum class TranscriptionStatus {
    NOT_REQUESTED,
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    val isInProgress: Boolean get() = this == PENDING || this == PROCESSING
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED

    companion object {
        /** Un valore sconosciuto o assente equivale a nessuna trascrizione richiesta. */
        fun fromStorage(value: String?): TranscriptionStatus =
            entries.firstOrNull { it.name == value } ?: NOT_REQUESTED
    }
}
