/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.browser

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.res.ResourcesCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.awesomebar.AwesomeBarFeature
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.readerview.view.ReaderViewControlsBar
import mozilla.components.feature.syncedtabs.SyncedTabsStorageSuggestionProvider
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.feature.tabs.toolbar.TabsToolbarFeature
import mozilla.components.feature.toolbar.WebExtensionToolbarFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.lib.state.ext.flow
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.search.AwesomeBarWrapper
import org.mozilla.reference.browser.settings.Settings
import org.mozilla.reference.browser.settings.SettingsActivity
import org.mozilla.reference.browser.tabs.TabsTrayFragment

/**
 * Fragment used for browsing the web within the main app.
 */
class BrowserFragment :
    BaseBrowserFragment(),
    UserInteractionHandler {
    private val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewIntegration>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()
    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()

    private val awesomeBar: AwesomeBarWrapper
        get() = requireView().findViewById(R.id.awesomeBar)
    private val toolbar: BrowserToolbar
        get() = requireView().findViewById(R.id.toolbar)
    private val engineView: EngineView
        get() = requireView().findViewById<View>(R.id.engineView) as EngineView
    private val readerViewBar: ReaderViewControlsBar
        get() = requireView().findViewById(R.id.readerViewBar)
    private val readerViewAppearanceButton: FloatingActionButton
        get() = requireView().findViewById(R.id.readerViewAppearanceButton)
    private lateinit var backButton: android.widget.ImageButton
    private lateinit var forwardButton: android.widget.ImageButton
    private lateinit var settingsButton: android.widget.ImageButton

    override val shouldUseComposeUI: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(
            getString(R.string.pref_key_compose_ui),
            false,
        )

    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        AwesomeBarFeature(awesomeBar, toolbar, engineView)
            .addSearchProvider(
                requireComponents.core.store,
                requireComponents.useCases.searchUseCases.defaultSearch,
                fetchClient = requireComponents.core.client,
                mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                engine = requireComponents.core.engine,
                limit = 5,
                filterExactMatch = true,
            ).addSessionProvider(
                resources,
                requireComponents.core.store,
                requireComponents.useCases.tabsUseCases.selectTab,
            ).addHistoryProvider(
                requireComponents.core.historyStorage,
                requireComponents.useCases.sessionUseCases.loadUrl,
            ).addClipboardProvider(requireContext(), requireComponents.useCases.sessionUseCases.loadUrl)

        // We cannot really add a `addSyncedTabsProvider` to `AwesomeBarFeature` coz that would create
        // a dependency on feature-syncedtabs (which depends on Sync).
        awesomeBar.addProviders(
            SyncedTabsStorageSuggestionProvider(
                requireComponents.backgroundServices.syncedTabsStorage,
                requireComponents.useCases.tabsUseCases.addTab,
                requireComponents.core.icons,
            ),
        )

        TabsToolbarFeature(
            toolbar = toolbar,
            sessionId = sessionId,
            store = requireComponents.core.store,
            showTabs = ::showTabs,
            lifecycleOwner = this,
        )

        thumbnailsFeature.set(
            feature = BrowserThumbnails(
                requireContext(),
                engineView,
                requireComponents.core.store,
            ),
            owner = this,
            view = view,
        )

        readerViewFeature.set(
            feature = ReaderViewIntegration(
                requireContext(),
                requireComponents.core.engine,
                requireComponents.core.store,
                toolbar,
                readerViewBar,
                readerViewAppearanceButton,
            ),
            owner = this,
            view = view,
        )

//        webExtToolbarFeature.set(
//            feature = WebExtensionToolbarFeature(
//                toolbar,
//                requireContext().components.core.store,
//            ),
//            owner = this,
//            view = view,
//        )

        windowFeature.set(
            feature = WindowFeature(
                store = requireComponents.core.store,
                tabsUseCases = requireComponents.useCases.tabsUseCases,
            ),
            owner = this,
            view = view,
        )

        engineView.setDynamicToolbarMaxHeight(resources.getDimensionPixelSize(R.dimen.browser_toolbar_height))

        //首页和底部菜单
        homepageAndBottomMenu()
    }

    private fun showTabs() {
        // For now we are performing manual fragment transactions here. Once we can use the new
        // navigation support library we may want to pass navigation graphs around.
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            replace(R.id.container, TabsTrayFragment())
            commit()
        }
    }

    override fun onBackPressed(): Boolean = readerViewFeature.onBackPressed() || super.onBackPressed()

    companion object {
        fun create(sessionId: String? = null) =
            BrowserFragment().apply {
            arguments = Bundle().apply {
                putSessionId(sessionId)
            }
        }
    }

    private fun onHomeButtonClicked() {
        // 使用EXTERNAL标志来避免在地址栏显示URL
        requireComponents.useCases.sessionUseCases.loadUrl.invoke(
            getString(top.yooho.browser.R.string.const_nav_url),
            flags = mozilla.components.concept.engine.EngineSession.LoadUrlFlags.none()
        )
    }

    private fun homepageAndBottomMenu(){
        //首页按钮
        val homeAction = BrowserToolbar.Button(
            imageDrawable = ResourcesCompat.getDrawable(
                resources,
                mozilla.components.ui.icons.R.drawable.mozac_ic_home_24,
                null,
            )!!,
            contentDescription = requireContext().getString(top.yooho.browser.R.string.browser_toolbar_home),
            iconTintColorResource = themeManager.getIconColor(),
            listener = ::onHomeButtonClicked,
        )
        if (Settings.shouldShowHomeButton(requireContext())) {
            toolbar.addNavigationAction(homeAction)
        }

        // Bottom bar buttons
        backButton = requireView().findViewById(R.id.actionBack)
        forwardButton = requireView().findViewById(R.id.actionForward)
        settingsButton = requireView().findViewById(R.id.actionSettings)

        // Apply tint to match theme
        val tint = androidx.core.content.ContextCompat.getColorStateList(requireContext(), themeManager.getIconColor())
        backButton.imageTintList = tint
        forwardButton.imageTintList = tint
        settingsButton.imageTintList = tint

        backButton.setOnClickListener {
            // Always attempt to go back; engine/store will ignore if not possible
            requireComponents.useCases.sessionUseCases.goBack.invoke()
        }

        forwardButton.setOnClickListener {
            // Always attempt to go forward; engine/store will ignore if not possible
            requireComponents.useCases.sessionUseCases.goForward.invoke()
        }

        settingsButton.setOnClickListener {
            val intent = android.content.Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }

        // Observe tab if we want to reflect navigation availability on UI later
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireComponents.core.store
                    .flow()
                    .map { state -> state.selectedTab }
                    .distinctUntilChanged()
                    .collect { tab ->
                        // Example: adjust alpha to reflect enabled-state visually
                        val canBack = tab?.content?.canGoBack == true
                        val canFwd = tab?.content?.canGoForward == true
                        backButton.alpha = if (canBack) 1f else 0.4f
                        forwardButton.alpha = if (canFwd) 1f else 0.4f
                        
                        // 同时启用/禁用按钮点击，而不仅仅是改变透明度
                        backButton.isEnabled = canBack
                        forwardButton.isEnabled = canFwd
                    }
            }
        }
    }
}
