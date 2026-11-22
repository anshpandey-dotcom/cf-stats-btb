package com.fleetmc.flipshelf.translate

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class MlKitTranslator(private val target: String = TranslateLanguage.HINDI) : FallbackTranslator.OfflineEngine {

    private var translator: Translator? = null

    private fun detect(sample: String): String {
        val client = LanguageIdentification.getClient(LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.4f).build())
        try {
            return Tasks.await(client.identifyLanguage(sample))
        } finally {
            client.close()
        }
    }

    override fun prepare(sourceTag: String?, sample: String) {
        val tag = sourceTag ?: detect(sample)
        val source = TranslateLanguage.fromLanguageTag(tag) ?: throw IllegalArgumentException("Language not supported: $tag")
        val options = TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        val t = Translation.getClient(options)
        translator = t
        Tasks.await(t.downloadModelIfNeeded(DownloadConditions.Builder().build()))
    }

    override fun translate(text: String): String {
        val t = translator ?: throw IllegalStateException("Translator not prepared")
        return Tasks.await(t.translate(text))
    }

    override fun close() {
        translator?.close()
        translator = null
    }
}
