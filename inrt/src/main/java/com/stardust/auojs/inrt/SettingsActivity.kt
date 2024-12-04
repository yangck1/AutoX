package com.stardust.auojs.inrt

import android.os.Bundle
import android.util.Log
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.autojs.autoxjs.inrt.R


/**
 * Created by Stardust on 2017/12/8.
 */

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupViews()
    }

    private fun setupViews() {
        setContentView(R.layout.activity_settings)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_setting, PreferenceFragment()).commit()
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setTitle(R.string.text_settings)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class PreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreate(@Nullable savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.preference)
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
           /* preference.setOnPreferenceClickListener {
                when (preference.key) {
                    getString(R.string.key_enable_floating_window) -> {
                        with(FloatingWindowPermissions) {
                            if (!isCanDrawOverlays(this@PreferenceFragment.requireContext())) {
                                getCanDrawOverlaysIntent()?.let { startActivity(it) }
                                return@setOnPreferenceClickListener false
                            }
                            return@setOnPreferenceClickListener true
                        }
                    }
                }
            }*/

            Log.d("inrt", "onPreferenceTreeClick: ")
            Log.d("inrt", "onPreferenceTreeClick: " + Pref.shouldEnableFloatingWindow());
            return super.onPreferenceTreeClick(preference)
        }

    }


}
