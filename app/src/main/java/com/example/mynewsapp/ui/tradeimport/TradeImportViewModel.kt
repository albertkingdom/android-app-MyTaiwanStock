package com.example.mynewsapp.ui.tradeimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.mynewsapp.BuildConfig
import com.example.mynewsapp.MyApplication
import com.example.mynewsapp.ui.tradeimport.core.NothingWrittenFailure
import com.example.mynewsapp.ui.tradeimport.core.ListChoice
import com.example.mynewsapp.ui.tradeimport.core.ParsedTrade
import com.example.mynewsapp.ui.tradeimport.core.PreviewItem
import com.example.mynewsapp.ui.tradeimport.core.RecognizedPage
import com.example.mynewsapp.ui.tradeimport.core.StockNameResolver
import com.example.mynewsapp.ui.tradeimport.core.TextLineSplitter
import com.example.mynewsapp.ui.tradeimport.core.TradeImportSession
import com.example.mynewsapp.ui.tradeimport.core.TradeScreenshotParser
import com.example.mynewsapp.ui.tradeimport.core.TradeSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** What the import screen shows. */
sealed class TradeImportState {
    /** Waiting for images (the photo picker is open). */
    object Idle : TradeImportState()
    data class Recognizing(val done: Int, val total: Int) : TradeImportState()
    /** No image contained a trade row. */
    object NoTrade : TradeImportState()
    /** An image could not be read or recognized; nothing was written. */
    object Failed : TradeImportState()
    /** The local records or the stock directory could not be read; nothing was written. */
    object ReadFailed : TradeImportState()
    /** The write ended with an unexpected error; the repository shows the message. */
    object WriteInterrupted : TradeImportState()
    data class Preview(
        val items: List<PreviewItem>,
        val lists: List<ListChoice>,
        val selectedList: ListChoice?,
        val showsNoListNotice: Boolean,
        val isImportEnabled: Boolean,
        val isImporting: Boolean,
    ) : TradeImportState()
    data class Imported(val count: Int) : TradeImportState()
}

class TradeImportViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as MyApplication
    private val repository = TradeImportRepository(application, app.database, app.repository)
    private val recognizer = TradeTextRecognizer(application)

    override fun onCleared() {
        recognizer.close()
    }

    private val _state = MutableLiveData<TradeImportState>(TradeImportState.Idle)
    val state: LiveData<TradeImportState> = _state

    private var session: TradeImportSession? = null
    private var pages: List<RecognizedPage> = emptyList()
    private var started = false
    private var confirming = false

    /** Images being imported, kept across process death. */
    val savedUris: List<Uri>?
        get() = savedState.get<ArrayList<String>>(KEY_URIS)?.map(Uri::parse)

    /** The photo picker was opened and has not returned a result yet. */
    var isPickerPending: Boolean
        get() = savedState.get<Boolean>(KEY_PICKER_PENDING) ?: false
        set(value) {
            savedState[KEY_PICKER_PENDING] = value
        }

    val hasStarted: Boolean get() = started

    /**
     * Recognizes [uris] one at a time and opens the preview. Only the first call has an effect.
     *
     * The default list is [currentListId] when it exists. A share starts a new activity whose list
     * screen has not chosen a list yet, so [lastViewedListIndex] (the list screen's saved position in
     * the same list order) is the fallback.
     */
    fun start(uris: List<Uri>, currentListId: Int?, lastViewedListIndex: Int?, saveUris: Boolean) {
        if (started) return
        started = true
        // Shared images are already kept in the navigation arguments; keep only picked ones here.
        if (saveUris) savedState[KEY_URIS] = ArrayList(uris.map(Uri::toString))
        viewModelScope.launch { recognize(uris, currentListId, lastViewedListIndex) }
    }

    private suspend fun recognize(uris: List<Uri>, currentListId: Int?, lastViewedListIndex: Int?) {
        val recognized = mutableListOf<RecognizedPage>()
        val trades = mutableListOf<ParsedTrade>()
        try {
            for ((index, uri) in uris.withIndex()) {
                _state.value = TradeImportState.Recognizing(index, uris.size)
                val page = recognizer.recognize(uri)
                recognized.add(page)
                // Kept only for the debug-only fixture export, also when a later page fails.
                if (BuildConfig.DEBUG) pages = recognized.toList()
                trades += withContext(Dispatchers.Default) { TradeScreenshotParser.parse(TextLineSplitter.boxes(page)) }
            }
            if (trades.isEmpty()) {
                _state.value = TradeImportState.NoTrade
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // TradeRecognitionException, or an unexpected failure (also out of memory) while parsing.
            _state.value = TradeImportState.Failed
            return
        }
        try {
            val directory = withContext(Dispatchers.IO) { StockDirectory.load(getApplication()) }
            val lists = repository.lists()
            val defaultListId = currentListId?.takeIf { id -> lists.any { it.id == id } }
                ?: lastViewedListIndex?.let { lists.getOrNull(it)?.id }
            val existing = repository.existingTrades()
            // Normalizing the directory names and keying the history is too much work for the main thread.
            session = withContext(Dispatchers.Default) {
                TradeImportSession(
                    trades = trades,
                    resolver = StockNameResolver(directory.entries),
                    isKnownCode = directory::isKnownCode,
                    existingTrades = existing,
                    lists = lists,
                    currentListId = defaultListId,
                )
            }
            publish()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Reading the stock directory or the local records failed (also out of memory); the images were fine.
            _state.value = TradeImportState.ReadFailed
        }
    }

    private fun publish(isImporting: Boolean = false) {
        val s = session ?: return
        _state.value = TradeImportState.Preview(
            items = s.items,
            lists = s.lists,
            selectedList = s.selectedList,
            showsNoListNotice = s.showsNoListNotice,
            isImportEnabled = s.isImportEnabled && !isImporting,
            isImporting = isImporting,
        )
    }

    // preview actions

    fun toggleSelection(id: Int) = change { it.toggleSelection(id) }

    fun selectList(id: Int?) = change { it.selectList(id) }

    /** Applies the trade editor's values; text fields follow the editor input rules. */
    fun applyEdit(id: Int, stockNoText: String, priceText: String, amountText: String, date: LocalDate?, side: TradeSide) =
        change {
            it.applyEdit(
                id, stockNoText, TradeImportSession.parsePrice(priceText), TradeImportSession.parseAmount(amountText), date, side,
            )
        }

    private fun change(block: (TradeImportSession) -> Unit) {
        if (confirming) return
        val s = session ?: return
        block(s)
        publish()
    }

    /**
     * Writes the selected trades once. A second call while writing, or after writing, is ignored.
     * The repository shows the outcome as a message, also when this screen is already closed.
     */
    fun confirmImport() {
        val s = session ?: return
        // A click queued just before the button was disabled.
        if (confirming || !s.isImportEnabled) return
        confirming = true
        // Lock the preview right away, so nothing changes between the tap and the write.
        publish(isImporting = true)
        val written = repository.write { used, inLists -> s.buildImportPlan(used, inLists) }
        viewModelScope.launch {
            try {
                _state.value = TradeImportState.Imported(written.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The repository fails the result with any Throwable, an OutOfMemoryError included.
                if (e is NothingWrittenFailure) {
                    // Nothing was written (for a changed preview the statuses are already updated).
                    unlock()
                } else {
                    // Unexpected: the preview closes so the same trades cannot be written twice.
                    _state.value = TradeImportState.WriteInterrupted
                }
            }
        }
    }

    /** Nothing was written: the preview can be edited and confirmed again. */
    private fun unlock() {
        confirming = false
        publish()
    }

    /** Debug builds only: the raw recognizer output as Kotlin test data. */
    fun recognizedFixture(): String = TextLineSplitter.toKotlinFixture(pages)

    private companion object {
        // Not "imageUris": the handle also holds the fragment's navigation arguments under that name.
        const val KEY_URIS = "recognizedUris"
        const val KEY_PICKER_PENDING = "pickerPending"
    }
}
