package top.yooho.setting

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.mozilla.reference.browser.ext.getPreferenceKey
import top.yooho.browser.R

class CustomizationSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.customization_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        setupPreferences()
        getActionBar().apply{
            show()
            setTitle(R.string.customization_settings)
            setDisplayHomeAsUpEnabled(true)
            setBackgroundDrawable(
                ContextCompat.getColor(requireContext(), R.color.action_bar).toDrawable())
        }
    }

    private fun setupPreferences() {
        val toolbarPositionKey = requireContext().getPreferenceKey(R.string.pref_key_toolbar_position)
        val themeKey = requireContext().getPreferenceKey(R.string.pref_key_theme)

        val preferenceToolbarPosition = findPreference<Preference>(toolbarPositionKey)
        val preferenceTheme = findPreference<Preference>(themeKey)
        preferenceTheme?.onPreferenceChangeListener = getChangeListenerForTheme()
    }


    private fun getActionBar() = (activity as AppCompatActivity).supportActionBar!!

    private fun getChangeListenerForTheme(): Preference.OnPreferenceChangeListener {
        return Preference.OnPreferenceChangeListener { _, newValue ->
            val modeString = newValue as String
            val mode = modeString.toInt()
            if (AppCompatDelegate.getDefaultNightMode() != mode) {
                AppCompatDelegate.setDefaultNightMode(mode)
                activity?.recreate()
                /* TODO: send colorScheme to gecko engine
                with(requireComponents.core) {
                    engine.settings.preferredColorScheme = getPreferredColorScheme()
                }
                 */
            }
            true
        }
    }

    companion object {
        private const val TAG = "CustomizationSettingsFragment"
    }
}
