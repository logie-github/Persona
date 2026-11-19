package com.logie.persona.backend.steam

import com.logie.persona.backend.catalog.Origin
import com.logie.persona.backend.catalog.Version
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the app shows while a game installs. Nothing here names the store: the person sees a game, a step and a fraction.
 */
data class InstallProgress(val step: Step, val fraction: Float, val detail: String? = null) {
    enum class Step { CONNECTING, CHECKING, DOWNLOADING, FINISHING }
}

class NotOwned(val version: Version) : Exception("${version.label} isn't in this account.")

/**
 * Downloads and installs a game on the backend: a saved sign-in, the account's licences, the Windows depots, the files into
 * [dir]. No screen, no store client; the app runs it from a foreground service and shows only [InstallProgress].
 * An interrupted install resumes (the depot downloader keeps its own staging and skips finished files).
 */
class Installer(private val store: LoginStore) {

    suspend fun install(version: Version, dir: File, onProgress: (InstallProgress) -> Unit) = withContext(Dispatchers.IO) {
        val appId = requireNotNull(version.appId) { "${version.id} isn't a downloaded version" }
        check(version.origin == Origin.ACCOUNT_DOWNLOAD)
        val login = store.load() ?: throw SignInRequired("Not signed in.")
        dir.mkdirs()
        onProgress(InstallProgress(InstallProgress.Step.CONNECTING, 0f))
        openConnection().use { conn ->
            val licences = conn.logOn(login)
            onProgress(InstallProgress(InstallProgress.Step.CHECKING, 0f))
            val done = CompletableDeferred<Throwable?>()
            val tracker = Tracker(onProgress, done)
            DepotDownloader(conn.client, licences, androidEmulation = true).use { dl ->
                dl.addListener(tracker)
                dl.add(AppItem(appId, installDirectory = dir.absolutePath, os = "windows", osArch = "64", verify = true))
                dl.finishAdding()
                val failure = done.await()
                if (failure != null) throw failure
            }
            onProgress(InstallProgress(InstallProgress.Step.FINISHING, 1f))
        }
    }

    /** Turns the downloader's events into [InstallProgress]; depots count equally by their own fractions. */
    internal class Tracker(
        private val onProgress: (InstallProgress) -> Unit,
        private val done: CompletableDeferred<Throwable?>,
    ) : IDownloadListener {
        private val depots = HashMap<Int, Float>()
        private var finished = 0

        @Synchronized
        override fun onChunkCompleted(depotId: Int, depotPercentComplete: Float, compressedBytes: Long, uncompressedBytes: Long) {
            depots[depotId] = depotPercentComplete
            onProgress(InstallProgress(InstallProgress.Step.DOWNLOADING, overall()))
        }

        @Synchronized
        override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
            depots[depotId] = 1f
            finished++
            onProgress(InstallProgress(InstallProgress.Step.DOWNLOADING, overall()))
        }

        private fun overall(): Float = if (depots.isEmpty()) 0f else depots.values.sum() / depots.size

        override fun onDownloadCompleted(item: DownloadItem) { done.complete(null) }
        override fun onDownloadFailed(item: DownloadItem, error: Throwable) { done.complete(error) }
    }
}
