/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.browser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.res.ResourcesCompat
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
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.feature.addons.menu.createMenuCandidate
import mozilla.components.lib.state.ext.flow
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.addons.AddonsActivity
import org.mozilla.reference.browser.ext.requireComponents
import org.mozilla.reference.browser.search.AwesomeBarWrapper
import org.mozilla.reference.browser.settings.Settings
import org.mozilla.reference.browser.settings.SettingsActivity
import org.mozilla.reference.browser.tabs.TabsTrayFragment
import top.yooho.browser.ui.addons.AddonsSheetDialogFragment
import top.yooho.browser.ui.settings.SettingsSheetDialogFragment
import top.yooho.browser.ui.settings.SettingsSheetDialogFragment.SettingsSheetListener
import top.yooho.browser.R as browserR

/**
 * Fragment used for browsing the web within the main app.
 */
class BrowserFragment :
    BaseBrowserFragment(),
    UserInteractionHandler,
    SettingsSheetListener {
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
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var addonButton: ImageButton

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
            flags = mozilla.components.concept.engine.EngineSession.LoadUrlFlags.none(),
        )
    }

    private fun homepageAndBottomMenu() {
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
        addonButton = requireView().findViewById(R.id.actionAddons)

        // Apply tint to match theme
        val tint = androidx.core.content.ContextCompat.getColorStateList(requireContext(), themeManager.getIconColor())
        backButton.imageTintList = tint
        forwardButton.imageTintList = tint
        settingsButton.imageTintList = tint
        addonButton.imageTintList = tint

        backButton.setOnClickListener {
            // Always attempt to go back; engine/store will ignore if not possible
            requireComponents.useCases.sessionUseCases.goBack.invoke()
        }

        forwardButton.setOnClickListener {
            // Always attempt to go forward; engine/store will ignore if not possible
            requireComponents.useCases.sessionUseCases.goForward.invoke()
        }

        addonButton.setOnClickListener {
            val selectedTab = requireComponents.core.store.state.selectedTab

            if (selectedTab == null) {
                // 没有打开网页，直接跳转到AddonsActivity
                val intent = android.content.Intent(requireContext(), AddonsActivity::class.java)
                startActivity(intent)
            } else {
                // 有打开网页，打印网址日志
                val url = selectedTab.content.url

                // 如果网址为homepage，则跳转到AddonsActivity
                if (url.contains(getString(top.yooho.browser.R.string.const_nav_url))
                    || !url.startsWith("http")
                ) {
                    val intent = android.content.Intent(requireContext(), AddonsActivity::class.java)
                    startActivity(intent)
                } else {
                    println("Current URL: $url")
                    showAddonsSheet()
                }
            }
        }

        settingsButton.setOnClickListener {
            showSettingsSheet()
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

    private fun showAddonsSheet() {
        val state = requireComponents.core.store.state
        val tab = state.selectedTab
        val extensions = state.extensions.values.toList()

        // Align filtering with WebExtensionNestedMenuCandidate from AC:
        // - Only enabled extensions
        // - If current tab is private, exclude extensions that are not allowed in private browsing
        // - Sort by name
        val visibleExtensions: MutableList<WebExtensionState> = mutableListOf()

        extensions
            .filter { it.enabled }
            .filterNot { !it.allowedInPrivateBrowsing && tab?.content?.private == true }
            .sortedBy { it.name }
            .forEach { extension ->
//                val tabExtensionState = tab?.extensionState?.get(extension.id)
                if (!extension.isBuiltIn) {
                    visibleExtensions.add(extension)
                }
            }

        val sheet = AddonsSheetDialogFragment.createFrom(visibleExtensions)
        sheet.loadAddonIcon = lambda@{ addonId, heightPx, onLoaded ->
            val ext = visibleExtensions.firstOrNull { it.id == addonId }
            if (ext == null) {
                onLoaded(null)
                return@lambda
            }
            val tabExtState = tab?.extensionState?.get(addonId)
            val action = ext.browserAction?.copyWithOverride(tabExtState?.browserAction)
                ?: ext.pageAction?.copyWithOverride(tabExtState?.pageAction)
            val loadIcon = action?.loadIcon
            if (loadIcon == null) {
                onLoaded(null)
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val bitmap = loadIcon.invoke(heightPx)
                        val drawable = bitmap?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                        onLoaded(drawable)
                    } catch (_: Throwable) {
                        onLoaded(null)
                    }
                }
            }
        }
        sheet.onAddonSelected = { addonId ->
            // Mirror AC's behavior: prefer invoking the extension's action; fallback to details
            val tab = requireComponents.core.store.state.selectedTab
            if (addonId == "builtin://addons_manager") {
                val intent = Intent(requireContext(), AddonsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                requireContext().startActivity(intent)

            } else {
                val ext = visibleExtensions.firstOrNull { it.id == addonId }
                if (ext != null) {
                    val tabExtensionState = tab?.extensionState?.get(addonId)
                    val browserAction = ext.browserAction?.copyWithOverride(tabExtensionState?.browserAction)
                    val pageAction = ext.pageAction?.copyWithOverride(tabExtensionState?.pageAction)
                    when {
                        browserAction != null -> browserAction.onClick()
                        pageAction != null -> pageAction.onClick()
                        else -> {
                            val intent = android.content.Intent().apply {
                                setClassName(
                                    requireContext().packageName,
                                    "${requireContext().packageName}.addons.InstalledAddonDetailsActivity",
                                )
                                putExtra("EXTRA_ADDON_ID", addonId)
                                putExtra("addonId", addonId)
                            }
                            try {
                                startActivity(intent)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        }
        sheet.show(parentFragmentManager, "addons_sheet")
    }

    private fun showSettingsSheet() {
        settingsSheet = SettingsSheetDialogFragment.create()
        // 设置监听器以处理设置项点击事件
        settingsSheet?.setSettingsSheetListener(this)
        settingsSheet?.show(parentFragmentManager, "settings_sheet")
    }

    /**
     * 处理设置项点击事件
     */
    override fun onSettingsItemClicked(title: String) {
        // 这里根据不同的设置项标题执行相应的操作
        when (title) {
            getString(browserR.string.share) -> {

            }
            getString(browserR.string.dark_mode) -> {
                // 处理夜间模式点击事件
            }
            getString(browserR.string.request_desktop_site) -> {
                // 请求桌面版
                // 可以切换应用的夜间模式
                // themeManager.toggleNightMode()
            }
            getString(browserR.string.private_mode) -> {

            }
            // 可以继续添加其他设置项的处理逻辑
            else -> {
                // 默认处理
                println("Settings item clicked: $title")
            }
        }
    }

    // 保存SettingsSheetDialogFragment的引用
    private var settingsSheet: SettingsSheetDialogFragment? = null

    /**
     * 处理用户个人资料点击事件
     */
    override fun onUserProfileClicked() {
        // 处理用户图标和名称点击事件
        Toast.makeText(context, "User profile clicked", Toast.LENGTH_SHORT).show()
        
        // 修改userIcon和userName的值
        settingsSheet?.apply {
            // 这里可以根据实际需求修改图标和名称
            userName.text = "已登录用户"
            // 示例：更改图标颜色或图片
            userIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(userIcon.context, android.R.color.holo_blue_light))
        }
    }

    /**
     * 处理设置按钮点击事件
     */
    override fun onSettingsButtonClicked() {
        // 处理设置按钮点击事件
        val intent = Intent(context, SettingsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context?.startActivity(intent)
    }
}
