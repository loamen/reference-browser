/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.service.fxa.manager.SCOPE_PROFILE
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import mozilla.components.support.ktx.android.view.showKeyboard
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.R.string.pref_key_about_page
import org.mozilla.reference.browser.R.string.pref_key_firefox_account
import org.mozilla.reference.browser.R.string.pref_key_make_default_browser
import org.mozilla.reference.browser.R.string.pref_key_override_amo_collection
import org.mozilla.reference.browser.R.string.pref_key_pair_sign_in
import org.mozilla.reference.browser.R.string.pref_key_privacy
import org.mozilla.reference.browser.R.string.pref_key_remote_debugging
import org.mozilla.reference.browser.R.string.pref_key_sign_in
import org.mozilla.reference.browser.autofill.AutofillPreference
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.getPreference
import org.mozilla.reference.browser.ext.getPreferenceKey
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.sync.BrowserFxAEntryPoint
import top.yooho.browser.config.PrefConst
import top.yooho.browser.ui.dialogs.LanguageChangeDialog
import top.yooho.browser.utils.PrefUtil
import top.yooho.setting.CustomizationSettingsFragment
import top.yooho.setting.InstalledSearchEnginesSettingsFragment
import java.util.Locale
import kotlin.system.exitProcess

private typealias RBSettings = org.mozilla.reference.browser.settings.Settings

class SettingsFragment : PreferenceFragmentCompat() {
    interface ActionBarUpdater {
        fun updateTitle(titleResId: Int)
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()

        setupPreferences()
        getActionBarUpdater().apply {
            updateTitle(R.string.settings)
        }
    }

    private fun setupPreferences() {
        val signInKey = requireContext().getPreferenceKey(pref_key_sign_in)
        val signInPairKey = requireContext().getPreferenceKey(pref_key_pair_sign_in)
        val firefoxAccountKey = requireContext().getPreferenceKey(pref_key_firefox_account)
        val makeDefaultBrowserKey = requireContext().getPreferenceKey(pref_key_make_default_browser)
        val remoteDebuggingKey = requireContext().getPreferenceKey(pref_key_remote_debugging)
        val aboutPageKey = requireContext().getPreferenceKey(pref_key_about_page)
        val privacyKey = requireContext().getPreferenceKey(pref_key_privacy)
        val customAddonsKey = requireContext().getPreferenceKey(pref_key_override_amo_collection)
        val autofillPreferenceKey = requireContext().getPreferenceKey(R.string.pref_key_autofill)

        val preferenceSignIn = findPreference<Preference>(signInKey)
        val preferencePairSignIn = findPreference<Preference>(signInPairKey)
        val preferenceFirefoxAccount = findPreference<Preference>(firefoxAccountKey)
        val preferenceMakeDefaultBrowser = findPreference<Preference>(makeDefaultBrowserKey)

        //loamen
        val changeLanguageKey = requireContext().getPreferenceKey(R.string.pref_key_change_language)
        val preferenceChangeLanguage = findPreference<Preference>(changeLanguageKey)
        preferenceChangeLanguage?.onPreferenceClickListener = getClickListenerForLanguageChange()
        preferenceChangeLanguage?.summary = getDisplayLanguage(getCurrentLocale())

        val preferenceRemoteDebugging = findPreference<SwitchPreferenceCompat>(remoteDebuggingKey)
        val preferenceAboutPage = findPreference<Preference>(aboutPageKey)
        val preferencePrivacy = findPreference<Preference>(privacyKey)
        val preferenceCustomAddons = findPreference<Preference>(customAddonsKey)
        val preferenceAutofill = findPreference<AutofillPreference>(autofillPreferenceKey)

        // loamen隐藏compose_ui设置项
        val composeUIKey = requireContext().getPreferenceKey(R.string.pref_key_compose_ui)
        val preferenceComposeUI = findPreference<SwitchPreferenceCompat>(composeUIKey)
        preferenceComposeUI?.isVisible = false

//        val accountManager = requireComponents.backgroundServices.accountManager
//        if (accountManager.authenticatedAccount() != null) {
//            preferenceSignIn?.isVisible = false
//            preferencePairSignIn?.isVisible = false
//            preferenceFirefoxAccount?.summary = accountManager.accountProfile()?.email.orEmpty()
//            preferenceFirefoxAccount?.onPreferenceClickListener = getClickListenerForFirefoxAccount()
//        } else {
//            preferenceSignIn?.isVisible = true
//            preferenceFirefoxAccount?.isVisible = false
//            preferenceFirefoxAccount?.onPreferenceClickListener = null
//            preferenceSignIn?.onPreferenceClickListener = getClickListenerForSignIn()
//            preferencePairSignIn?.isVisible = true
//            preferencePairSignIn?.onPreferenceClickListener = getClickListenerForPairingSignIn()
//        }

        //loamen 隐藏
        preferenceSignIn?.isVisible = false
        preferencePairSignIn?.isVisible = false
        preferenceFirefoxAccount?.isVisible = false

        val  searchEngineKey = requireContext().getPreferenceKey(top.yooho.browser.R.string.pref_key_search_engine)
            val preferenceSearchEngine = findPreference<Preference>(searchEngineKey)
        preferenceSearchEngine?.onPreferenceClickListener = getClickListenerForSearch()

        preferenceSearchEngine?.summary = getString(
            top.yooho.browser.R.string.setting_item_selected,
            requireContext().components.core.store.state.search.selectedOrDefaultSearchEngine?.name
        )
        getPreference(top.yooho.browser.R.string.pref_key_customization)?.onPreferenceClickListener =
            getClickListenerForCustomization()


        if (!AutofillPreference.isSupported(requireContext())) {
            preferenceAutofill?.isVisible = false
        } else {
            (preferenceAutofill as AutofillPreference).updateSwitch()
        }

        preferenceMakeDefaultBrowser?.onPreferenceClickListener = getClickListenerForMakeDefaultBrowser()
        preferenceRemoteDebugging?.onPreferenceChangeListener = getChangeListenerForRemoteDebugging()
        preferenceAboutPage?.onPreferenceClickListener = getAboutPageListener()
        preferencePrivacy?.onPreferenceClickListener = getClickListenerForPrivacy()
        preferenceCustomAddons?.onPreferenceClickListener = getClickListenerForCustomAddons()

        // 移除开发者工具分类（PreferenceCategory）
        val developerToolsCategory = findPreference<Preference>("developer_tools_category")
        // 开发者模式状态
        val isDeveloperMode = PrefUtil.getBoolean(requireContext(), PrefConst.DEVELOPER_MODE_KEY, false)
        if (developerToolsCategory != null && !isDeveloperMode) {
            preferenceScreen.removePreference(developerToolsCategory)
        }
    }

    private fun getClickListenerForMakeDefaultBrowser(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            )
            startActivity(intent)
            true
        }

    //loamen 切换语言
    private fun getClickListenerForLanguageChange(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            val languageChangeDialog = LanguageChangeDialog(
                requireContext(),
                object : LanguageChangeDialog.SetLanguageListener {
                    override fun onLanguageSelected(locale: Locale) {
                        // update language
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.create(Locale.forLanguageTag(locale.toLanguageTag())),
                        )
                        top.yooho.browser.settings.Settings.clearAnnouncementData(requireContext())
                        // 更新设置项中的语言显示
                        findPreference<Preference>(requireContext().getPreferenceKey(R.string.pref_key_change_language))?.summary = getDisplayLanguage(locale)
                    }
                },
            )

            languageChangeDialog.getDialog().show()
            true
        }
    }

    //loamen 设置搜索引擎
    private fun getClickListenerForSearch(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            // 如果NavController不可用，使用传统方式替换Fragment
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, InstalledSearchEnginesSettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }
    }
    //loamen 设置自定义插件
    private fun getClickListenerForCustomization(): OnPreferenceClickListener {
        return OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, CustomizationSettingsFragment())
                .addToBackStack(null)
                .commit()
            true
        }
    }

    private fun getClickListenerForSignIn(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            requireComponents.services.accountsAuthFeature.beginAuthentication(
                requireContext(),
                BrowserFxAEntryPoint.HomeMenu,
                setOf(SCOPE_PROFILE, SCOPE_SYNC),
            )
            activity?.finish()
            true
        }

    private fun getClickListenerForPairingSignIn(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, PairSettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.pair_preferences)
            }
            true
        }

    private fun getClickListenerForFirefoxAccount(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, AccountSettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.account_settings)
            }
            true
        }

    private fun getClickListenerForPrivacy(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, PrivacySettingsFragment())
                .addToBackStack(null)
                .commit()
            getActionBarUpdater().apply {
                updateTitle(R.string.privacy_settings)
            }
            true
        }

    private fun getChangeListenerForRemoteDebugging(): OnPreferenceChangeListener =
        OnPreferenceChangeListener { _, newValue ->
            requireComponents.core.engine.settings.remoteDebuggingEnabled = newValue as Boolean
            true
        }

    private fun getAboutPageListener(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .replace(R.id.container, AboutFragment())
                .addToBackStack(null)
                .commit()
            true
        }

    private fun getActionBarUpdater() = activity as ActionBarUpdater

    private fun getClickListenerForCustomAddons(): OnPreferenceClickListener =
        OnPreferenceClickListener {
            val context = requireContext()
            val dialogView = View.inflate(context, R.layout.amo_collection_override_dialog, null)
            val userView = dialogView.findViewById<EditText>(R.id.custom_amo_user)
            val collectionView = dialogView.findViewById<EditText>(R.id.custom_amo_collection)

            AlertDialog
                .Builder(context)
                .apply {
                    setTitle(context.getString(R.string.preferences_customize_amo_collection))
                    setView(dialogView)
                    setNegativeButton(R.string.customize_addon_collection_cancel) { dialog: DialogInterface, _ ->
                        dialog.cancel()
                    }

                    setPositiveButton(R.string.customize_addon_collection_ok) { _, _ ->
                        RBSettings.setOverrideAmoUser(context, userView.text.toString())
                        RBSettings.setOverrideAmoCollection(context, collectionView.text.toString())

                        Toast
                            .makeText(
                                context,
                                getString(R.string.toast_customize_addon_collection_done),
                                Toast.LENGTH_LONG,
                            ).show()

                        Handler(Looper.getMainLooper()).postDelayed(
                            {
                                exitProcess(0)
                            },
                            AMO_COLLECTION_OVERRIDE_EXIT_DELAY,
                        )
                    }

                    collectionView.setText(RBSettings.getOverrideAmoCollection(context))
                    userView.setText(RBSettings.getOverrideAmoUser(context))
                    userView.requestFocus()
                    userView.showKeyboard()
                    create()
                }.show()
            true
        }

    // 根据语言代码返回对应的显示名称
    private fun getDisplayLanguage(locale: Locale): String {
        val context = requireContext()
        return when (locale.language) {
            "zh" -> {
                // 区分简体中文和繁体中文
                when (locale.country) {
                    "TW" -> context.getString(top.yooho.browser.R.string.language_zh_tw)
                    else -> context.getString(top.yooho.browser.R.string.language_zh_cn)
                }
            }
            "en" -> context.getString(top.yooho.browser.R.string.language_en)
            else -> locale.displayLanguage
        }
    }

    companion object {
        private const val AMO_COLLECTION_OVERRIDE_EXIT_DELAY = 3000L

        //loamen
        fun getCurrentLocale(): Locale = (
                AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault()
                )
    }
}