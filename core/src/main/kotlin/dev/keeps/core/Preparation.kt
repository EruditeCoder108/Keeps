package dev.keeps.core

enum class PreparationChoice { PREPARE, COMPLETE, BUSY, MODEL_NEEDED, UNSUPPORTED, UNAVAILABLE }

/** Bulk preparation never treats a missing model or a finished compatible index as new work. */
object Preparation {
    fun supported(kind: AssetKind, mime: String, name: String): Boolean = when (kind) {
        AssetKind.OTHER -> false
        AssetKind.DOCUMENT -> mime.equals("application/pdf", true) || name.endsWith(".pdf", true)
        else -> true
    }

    fun choice(kind: AssetKind, mime: String, name: String, status: IndexStatus, modelInstalled: Boolean,
               compatible: Boolean, excluded: Boolean = false, trashed: Boolean = false): PreparationChoice = when {
        excluded || trashed || status == IndexStatus.NO_ACCESS -> PreparationChoice.UNAVAILABLE
        !supported(kind, mime, name) -> PreparationChoice.UNSUPPORTED
        !modelInstalled && kind != AssetKind.TEXT -> PreparationChoice.MODEL_NEEDED
        status == IndexStatus.QUEUED || status == IndexStatus.INDEXING -> PreparationChoice.BUSY
        compatible && (status == IndexStatus.AI_READY || status == IndexStatus.PARTIAL ||
            status == IndexStatus.TEXT_READY && !modelInstalled) -> PreparationChoice.COMPLETE
        else -> PreparationChoice.PREPARE
    }
}
