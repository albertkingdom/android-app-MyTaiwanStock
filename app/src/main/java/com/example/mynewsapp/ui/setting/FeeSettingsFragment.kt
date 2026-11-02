package com.example.mynewsapp.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.mynewsapp.R
import com.example.mynewsapp.databinding.FragmentFeeSettingsBinding
import com.example.mynewsapp.tradecost.FeeSettings
import com.example.mynewsapp.tradecost.FeeSettingsException
import com.example.mynewsapp.util.hideKeyboard

/** The page behind the fee row of the settings screen: the discount and both minimum fees, saved together. */
class FeeSettingsFragment : Fragment() {
    private var _binding: FragmentFeeSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var store: FeeSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeeSettingsBinding.inflate(inflater, container, false)
        store = FeeSettingsStore(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // After a configuration change the fields keep what the user typed.
        if (savedInstanceState == null) {
            val settings = store.load()
            binding.discount.editText?.setText(settings.discount.toPlainString())
            binding.wholeLotMinimumFee.editText?.setText(settings.wholeLotMinimumFee.toString())
            binding.oddLotMinimumFee.editText?.setText(settings.oddLotMinimumFee.toString())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.fee_settings_option_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.saveFeeSettings -> {
            save()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    /** Validates the three fields together. When any of them is invalid, nothing is saved and the page stays open. */
    private fun save() {
        binding.discount.error = null
        binding.wholeLotMinimumFee.error = null
        binding.oddLotMinimumFee.error = null
        try {
            val settings = FeeSettings.parse(
                discountText = binding.discount.editText?.text?.toString(),
                wholeLotMinimumText = binding.wholeLotMinimumFee.editText?.text?.toString(),
                oddLotMinimumText = binding.oddLotMinimumFee.editText?.text?.toString(),
            )
            store.save(settings)
            view?.hideKeyboard()
            findNavController().popBackStack()
        } catch (e: FeeSettingsException) {
            val field = when (e.reason) {
                FeeSettingsException.Reason.INVALID_DISCOUNT -> binding.discount
                FeeSettingsException.Reason.INVALID_WHOLE_LOT_MINIMUM_FEE -> binding.wholeLotMinimumFee
                FeeSettingsException.Reason.INVALID_ODD_LOT_MINIMUM_FEE -> binding.oddLotMinimumFee
            }
            field.error = e.reason.message
            field.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
