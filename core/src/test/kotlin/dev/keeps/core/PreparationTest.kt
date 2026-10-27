package dev.keeps.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PreparationTest {
    private fun choice(kind: AssetKind = AssetKind.IMAGE, status: IndexStatus = IndexStatus.IMPORTED,
                       model: Boolean = true, compatible: Boolean = false, mime: String = "image/png",
                       name: String = "photo.png", excluded: Boolean = false) =
        Preparation.choice(kind, mime, name, status, model, compatible, excluded)

    @Test fun mediaCannotQueueWithoutAModel() {
        for (kind in listOf(AssetKind.IMAGE, AssetKind.VIDEO, AssetKind.AUDIO, AssetKind.DOCUMENT))
            assertEquals(PreparationChoice.MODEL_NEEDED, choice(kind, model = false, mime = "application/pdf", name = "a.pdf"))
        assertEquals(PreparationChoice.PREPARE, choice(AssetKind.TEXT, model = false))
    }
    @Test fun repeatBulkPreparationSkipsCompatibleFinishedWork() {
        assertEquals(PreparationChoice.COMPLETE, choice(status = IndexStatus.AI_READY, compatible = true))
        assertEquals(PreparationChoice.PREPARE, choice(status = IndexStatus.AI_READY, compatible = false))
        assertEquals(PreparationChoice.COMPLETE, choice(status = IndexStatus.PARTIAL, compatible = true))
        assertEquals(PreparationChoice.PREPARE, choice(status = IndexStatus.PARTIAL, compatible = false))
    }
    @Test fun addingAModelUpgradesPreviouslyLexicalText() {
        assertEquals(PreparationChoice.COMPLETE, choice(AssetKind.TEXT, IndexStatus.TEXT_READY, model = false, compatible = true))
        assertEquals(PreparationChoice.PREPARE, choice(AssetKind.TEXT, IndexStatus.TEXT_READY, model = true, compatible = true))
    }
    @Test fun unsupportedDocumentsDoNotFillTheWorkerQueue() {
        assertEquals(PreparationChoice.UNSUPPORTED, choice(AssetKind.DOCUMENT, mime = "application/msword", name = "letter.doc"))
        assertEquals(PreparationChoice.UNSUPPORTED, choice(AssetKind.OTHER))
        assertEquals(PreparationChoice.PREPARE, choice(AssetKind.DOCUMENT, mime = "application/octet-stream", name = "scan.PDF"))
    }
    @Test fun excludedUnavailableAndAlreadyRunningWorkStayOut() {
        assertEquals(PreparationChoice.UNAVAILABLE, choice(excluded = true))
        assertEquals(PreparationChoice.UNAVAILABLE, choice(status = IndexStatus.NO_ACCESS))
        assertEquals(PreparationChoice.BUSY, choice(status = IndexStatus.QUEUED))
        assertEquals(PreparationChoice.BUSY, choice(status = IndexStatus.INDEXING))
    }
}
