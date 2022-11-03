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
    }

}