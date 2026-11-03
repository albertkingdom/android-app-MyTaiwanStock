package com.example.mynewsapp.ui.tradeimport

import android.content.Context
import android.net.Uri
import com.example.mynewsapp.ui.tradeimport.core.PixelElement
import com.example.mynewsapp.ui.tradeimport.core.RecognizedPage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** The image could not be read or recognized. */
class TradeRecognitionException(cause: Throwable) : Exception(cause)

/**
 * Reads the text of a screenshot on the device with the bundled ML Kit Chinese model. Nothing is
 * uploaded and the recognized text is never logged.
 */
class TradeTextRecognizer(private val context: Context) {

    /** One client for the whole import; [close] releases it. */
    private val recognizerLazy = lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val recognizer by recognizerLazy

    fun close() {
        if (recognizerLazy.isInitialized()) recognizer.close()
    }

    /** Throws [TradeRecognitionException] when the image cannot be read or recognition fails. */
    suspend fun recognize(uri: Uri): RecognizedPage = withContext(Dispatchers.IO) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // IOException, or SecurityException when the read permission is gone.
            throw TradeRecognitionException(e)
        } catch (e: OutOfMemoryError) {
            // A very long scroll capture.
            throw TradeRecognitionException(e)
        }

        try {
            val text = recognizer.process(image).await()
            // The boxes refer to the upright image.
            val rotated = image.rotationDegrees == 90 || image.rotationDegrees == 270
            val width = if (rotated) image.height else image.width
            val height = if (rotated) image.width else image.height
            val lines = text.textBlocks.flatMap { it.lines }.map { line ->
                line.elements.mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    PixelElement(element.text, box.left, box.top, box.right, box.bottom)
                }
            }.filter { it.isNotEmpty() }
            RecognizedPage(width, height, lines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw TradeRecognitionException(e)
        } catch (e: OutOfMemoryError) {
            throw TradeRecognitionException(e)
        }
    }
}
