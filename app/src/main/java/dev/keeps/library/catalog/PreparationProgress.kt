package dev.keeps.library.catalog

import dev.keeps.core.*

data class PreparationProgress(
    val supported: Int = 0, val ready: Int = 0, val queued: Int = 0, val running: Int = 0,
    val partial: Int = 0, val failed: Int = 0, val modelNeeded: Int = 0, val unsupported: Int = 0,
    val currentName: String = "", val detail: String = "",
) {
    val active get() = queued + running > 0
    val title get() = if (running > 0) "Preparing content search" else if (queued > 0) "Waiting to prepare" else "Content search preparation"
    val summary get() = "$ready files ready · $queued waiting · $partial partial · $failed failed"

    companion object {
        fun from(assets: List<AssetEntity>, modelInstalled: Boolean, automatic: Boolean? = null): PreparationProgress {
            val included = assets.filter { !it.trashed && !it.contentSearchExcluded && it.status != IndexStatus.NO_ACCESS }
            val supported = included.filter { Preparation.supported(it.kind, it.mimeType, it.name) }
            val eligible = supported.filter { modelInstalled || it.kind == AssetKind.TEXT }
            val queue = eligible.filter { automatic == null || it.automaticPreparation == automatic }
            val current = queue.firstOrNull { it.status == IndexStatus.INDEXING }
            return PreparationProgress(eligible.size,
                eligible.count { it.status == IndexStatus.AI_READY || !modelInstalled && it.status == IndexStatus.TEXT_READY },
                queue.count { it.status == IndexStatus.QUEUED }, queue.count { it.status == IndexStatus.INDEXING },
                eligible.count { it.status == IndexStatus.PARTIAL }, eligible.count { it.status == IndexStatus.FAILED },
                supported.size - eligible.size, included.size - supported.size, current?.name.orEmpty(), current?.detail.orEmpty())
        }
    }
}
