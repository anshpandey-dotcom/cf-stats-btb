package com.fleetmc.kuchlikh.text

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object HindiTranslator {

    private val model = TranslateRemoteModel.Builder(TranslateLanguage.HINDI).build()

    private val client by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.HINDI)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        )
    }

    suspend fun ready(): Boolean =
        runCatching { RemoteModelManager.getInstance().isModelDownloaded(model).await() }.getOrDefault(false)

    suspend fun download() {
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
    }

    suspend fun remove() {
        RemoteModelManager.getInstance().deleteDownloadedModel(model).await()
    }

    suspend fun translate(text: String): String {
        val latin = text.count { (it in 'a'..'z') || (it in 'A'..'Z') }
        val deva = text.count { Transliterator.isDevanagari(it) }
        val source = if (latin > deva) Transliterator.text(text) else text
        val out = StringBuilder()
        source.split("\n").forEachIndexed { index, line ->
            if (index > 0) out.append('\n')
            if (line.isNotBlank()) out.append(client.translate(line).await())
        }
        return out.toString()
    }
}
