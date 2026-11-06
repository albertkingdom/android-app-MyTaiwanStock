package com.example.mynewsapp.ui.tradeimport

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.mynewsapp.BuildConfig
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentTradeImportBinding
import com.example.mynewsapp.ui.list.ListViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Trade screenshot import: picks images (or takes the shared ones), recognizes them on the device,
 * and shows a preview. Nothing is written until the user taps the import button.
 */
class TradeImportFragment : Fragment() {

    private var _binding: FragmentTradeImportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TradeImportViewModel by viewModels()
    private val listViewModel: ListViewModel by activityViewModels()

    private lateinit var adapter: TradePreviewAdapter

    /** The list choices bound to the spinner: list id (null for "do not add") and name. */
    private var spinnerChoices: List<Pair<Int?, String>>? = null

    /** The dialog of a final state; dismissed with the view so it never outlives the activity. */
    private var finalDialog: AlertDialog? = null

    /** The open trade editor; dismissed with the view (an unsaved edit is dropped on rotation). */
    private var editDialog: AlertDialog? = null

    private val args: TradeImportFragmentArgs by navArgs()

    /** Check in onResume whether the picker closed without delivering a result. */
    private var checkPickerOnResume = false

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.isPickerPending = false
        checkPickerOnResume = false
        if (uris.isEmpty()) {
            returnToList()
        } else {
            viewModel.start(uris, currentListId(), lastViewedListIndex(), saveUris = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After process death the picker result may never come back.
        if (savedInstanceState != null && viewModel.isPickerPending) checkPickerOnResume = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTradeImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TradePreviewAdapter(
            onToggle = { viewModel.toggleSelection(it.id) },
            onEdit = { item ->
                editDialog?.dismiss()
                editDialog = TradeEditDialog.show(requireContext(), item) { stockNo, price, amount, date, side ->
                    viewModel.applyEdit(item.id, stockNo, price, amount, date, side)
                }
            },
        )
        binding.tradeList.adapter = adapter
        binding.importButton.setOnClickListener { viewModel.confirmImport() }

        if (BuildConfig.DEBUG) addDebugMenu()

        viewModel.state.observe(viewLifecycleOwner) { render(it) }

        if (!viewModel.hasStarted) startOrPick()
    }

    private fun startOrPick() {
        val shared = args.imageUris?.map(Uri::parse)
        when {
            // A share starts a new window whose list screen never ran; only the saved last-viewed list is reliable.
            !shared.isNullOrEmpty() -> viewModel.start(shared, null, lastViewedListIndex(), saveUris = false)
            // Process death: recognize the same images again.
            !viewModel.savedUris.isNullOrEmpty() -> viewModel.start(viewModel.savedUris!!, currentListId(), lastViewedListIndex(), saveUris = false)
            !viewModel.isPickerPending -> {
                viewModel.isPickerPending = true
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (viewModel.isPickerPending) checkPickerOnResume = true
    }

    override fun onResume() {
        super.onResume()
        // A result is delivered when the fragment starts, before this point.
        if (checkPickerOnResume && viewModel.isPickerPending && !viewModel.hasStarted) {
            viewModel.isPickerPending = false
            returnToList()
        }
        checkPickerOnResume = false
    }

    // 0 is the list screen's "not chosen yet"; Room list ids start at 1.
    private fun currentListId(): Int? = listViewModel.currentSelectedFollowingListId.value?.takeIf { it > 0 }

    /** The selected list's tab position, saved by ListViewModel.selectList. */
    private fun lastViewedListIndex(): Int? =
        requireContext().getSharedPreferences(ListViewModel.SHARED_PREF, Context.MODE_PRIVATE).getInt(ListViewModel.KEY_CURRENT_LIST, -1).takeIf { it >= 0 }

    private fun render(state: TradeImportState) {
        when (state) {
            TradeImportState.Idle -> showProgress(getString(R.string.trade_import_loading))
            is TradeImportState.Recognizing ->
                showProgress(getString(R.string.trade_import_recognizing, state.done + 1, state.total))
            TradeImportState.NoTrade -> showFinalMessage(R.string.trade_import_no_trade)
            TradeImportState.Failed -> showFinalMessage(R.string.trade_import_failed)
            TradeImportState.ReadFailed -> showFinalMessage(R.string.trade_import_read_failed)
            // The repository already showed what happened.
            TradeImportState.WriteInterrupted -> returnToList()
            is TradeImportState.Preview -> showPreview(state)
            // The "imported N" message is shown by the repository, also when this screen is gone.
            is TradeImportState.Imported -> returnToList()
        }
    }

    private fun showProgress(text: String) {
        binding.progressGroup.visibility = View.VISIBLE
        binding.previewGroup.visibility = View.GONE
        binding.progressText.text = text
    }

    private fun showFinalMessage(text: Int) {
        binding.progressGroup.visibility = View.GONE
        binding.previewGroup.visibility = View.GONE
        if (finalDialog?.isShowing == true) return
        finalDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage(text)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_confirm_button) { _, _ -> returnToList() }
            .show()
    }

    private fun showPreview(state: TradeImportState.Preview) {
        binding.progressGroup.visibility = View.GONE
        binding.previewGroup.visibility = View.VISIBLE
        adapter.isLocked = state.isImporting
        // An edit cannot be saved while the import is being written.
        if (state.isImporting) editDialog?.dismiss()
        adapter.submitList(state.items)
        binding.importButton.isEnabled = state.isImportEnabled
        binding.importButton.setText(if (state.isImporting) R.string.trade_import_importing else R.string.trade_import_confirm)
        binding.noListNotice.visibility = if (state.showsNoListNotice) View.VISIBLE else View.GONE
        bindListSpinner(state)
    }

    private fun bindListSpinner(state: TradeImportState.Preview) {
        val spinner = binding.listSpinner
        val choices = state.lists.map { it.id to it.name } + (null to getString(R.string.trade_import_no_list))
        // The lists do not change during an import; bind the adapter once per view.
        if (choices != spinnerChoices) {
            spinnerChoices = choices
            spinner.onItemSelectedListener = null
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, choices.map { it.second })
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    viewModel.selectList(choices[position].first)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        val position = choices.indexOfFirst { it.first == state.selectedList?.id }.coerceAtLeast(0)
        if (spinner.selectedItemPosition != position) spinner.setSelection(position, false)
        spinner.isEnabled = !state.isImporting
    }

    /** Debug builds only: copies the raw recognizer output as Kotlin test data. Never logged. */
    private fun addDebugMenu() {
        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menu.add(Menu.NONE, MENU_COPY_FIXTURE, Menu.NONE, R.string.trade_import_copy_fixture)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (menuItem.itemId != MENU_COPY_FIXTURE) return false
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("trade import fixture", viewModel.recognizedFixture()))
                    Toast.makeText(requireContext(), R.string.trade_import_fixture_copied, Toast.LENGTH_SHORT).show()
                    return true
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    /** Back to the stock list, also when a share opened this screen above other screens. */
    private fun returnToList() {
        val nav = findNavController()
        if (!nav.popBackStack(R.id.stockListFragment, false)) nav.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        spinnerChoices = null
        finalDialog?.dismiss()
        finalDialog = null
        editDialog?.dismiss()
        editDialog = null
        _binding = null
    }

    private companion object {
        const val MENU_COPY_FIXTURE = 0x7101
    }
}
