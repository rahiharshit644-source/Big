package com.soltini.app.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vosk.Model
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unpacks the bundled offline Vosk English model (assets/model-en-us) into internal
 * storage on first run and keeps one shared [Model] in memory for wake-word listening.
 */
object VoskModelManager {
    private const val TAG = "VoskModelManager"
    private const val ASSET_DIR = "model-en-us"

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    @Volatile
    var model: Model? = null
        private set

    private val isInitializing = AtomicBoolean(false)

    fun init(context: Context, scope: CoroutineScope) {
        if (model != null) return
        if (!isInitializing.compareAndSet(false, true)) return

        scope.launch(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val hasAsset = try {
                    !appContext.assets.list(ASSET_DIR).isNullOrEmpty()
                } catch (_: Exception) { false }
                if (!hasAsset) {
                    Log.e(TAG, "assets/$ASSET_DIR is missing - copy the model folder into app/src/main/assets/. Wake word disabled.")
                    return@launch
                }

                val targetDir = File(appContext.filesDir, "vosk-model")
                val marker = File(targetDir, "am/final.mdl")
                if (!marker.exists() || marker.length() == 0L) {
                    Log.i(TAG, "Unpacking Vosk model to ${targetDir.absolutePath}")
                    targetDir.deleteRecursively()
                    targetDir.mkdirs()
                    copyAssetFolder(appContext.assets, ASSET_DIR, targetDir)
                }

                model = Model(targetDir.absolutePath)
                _isModelReady.value = true
                Log.i(TAG, "Vosk model ready")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load Vosk model: ${e.message}", e)
            } finally {
                isInitializing.set(false)
            }
        }
    }

    private fun copyAssetFolder(assetManager: AssetManager, fromAssetPath: String, toDir: File) {
        val list = assetManager.list(fromAssetPath)
        if (list.isNullOrEmpty()) {
            try {
                assetManager.open(fromAssetPath).use { input ->
                    toDir.parentFile?.mkdirs()
                    FileOutputStream(toDir).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not copy asset $fromAssetPath: ${e.message}")
            }
        } else {
            toDir.mkdirs()
            for (item in list) {
                copyAssetFolder(assetManager, "$fromAssetPath/$item", File(toDir, item))
            }
        }
    }
}
