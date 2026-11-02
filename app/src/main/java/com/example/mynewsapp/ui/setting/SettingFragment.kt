package com.example.mynewsapp.ui.setting

import android.os.Bundle
import androidx.navigation.fragment.findNavController

import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.mynewsapp.R

class SettingFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.fragment_setting, rootKey)

        preferenceManager.findPreference<Preference>("account")?.let {
            it.setOnPreferenceClickListener {
                findNavController().navigate(SettingFragmentDirections.actionSettingFragmentToAccountFragment())
                true
            }
        }
        preferenceManager.findPreference<Preference>(FEE_SETTINGS_KEY)?.setOnPreferenceClickListener {
            findNavController().navigate(SettingFragmentDirections.actionSettingFragmentToFeeSettingsFragment())
            true
        }
    }

    /** The fee row states what is saved, so it is refreshed whenever the screen comes back. */
    override fun onResume() {
        super.onResume()
        preferenceManager.findPreference<Preference>(FEE_SETTINGS_KEY)?.summary =
            FeeSettingsStore(requireContext()).load().summaryText
    }

    private companion object {
        const val FEE_SETTINGS_KEY = "fee_settings"
    }
}
