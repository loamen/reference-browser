/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.intent.ext.EXTRA_SESSION_ID
import mozilla.components.lib.crash.Crash
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.setupPersistentInsets
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.webextensions.WebExtensionPopupObserver
import org.mozilla.reference.browser.addons.WebExtensionActionPopupActivity
import org.mozilla.reference.browser.browser.BrowserFragment
import org.mozilla.reference.browser.browser.CrashIntegration
import org.mozilla.reference.browser.ext.components
import top.yooho.browser.ui.StandbyFragment
import top.yooho.ui.theme.DefaultThemeManager
import top.yooho.ui.theme.ThemeManager

/**
 * Activity that holds the [BrowserFragment].
 */
open class BrowserActivity : AppCompatActivity(), StandbyFragment.NavigationListener {
    private lateinit var crashIntegration: CrashIntegration
    lateinit var themeManager: ThemeManager

    private val sessionId: String?
        get() = SafeIntent(intent).getStringExtra(EXTRA_SESSION_ID)

    private val webExtensionPopupObserver by lazy {
        WebExtensionPopupObserver(components.core.store, ::openPopup)
    }

    /**
     * Returns a new instance of [BrowserFragment] to display.
     */
    open fun createBrowserFragment(sessionId: String?): Fragment = BrowserFragment.create(sessionId)

    /**
     * loamen
     * Returns a new instance of [StandbyFragment] to display at startup.
     */
    open fun createStandbyFragment(): Fragment = StandbyFragment.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        themeManager = DefaultThemeManager( this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT))
        window.setupPersistentInsets()

        components.notificationsDelegate.bindToActivity(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.container, createStandbyFragment())
                commit()
            }
        }

        crashIntegration = CrashIntegration(this, components.analytics.crashReporter) { crash ->
            onNonFatalCrash(crash)
        }
        lifecycle.addObserver(crashIntegration)

        NotificationManager.checkAndNotifyPolicy(this)
        lifecycle.addObserver(webExtensionPopupObserver)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                private var isPromptShown = false
                private var lastBackPressTime: Long = 0

                override fun handleOnBackPressed() {
                    supportFragmentManager.fragments.forEach {
                        if (it is UserInteractionHandler && it.onBackPressed()) {
                            return
                        }
                    }

                    if (!isPromptShown) {
                        // 显示提示，要求用户再按一次返回键
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            getString(R.string.press_again_to_exit),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        isPromptShown = true
                        lastBackPressTime = System.currentTimeMillis()
                    } else {
                        // 检查两次按下的时间间隔是否在3秒内
                        if (System.currentTimeMillis() - lastBackPressTime <= 3000) {
                            // 用户再次按下返回键，关闭 Activity
                            if (isEnabled) {
                                isEnabled = false
                                onBackPressedDispatcher.onBackPressed()
                            }
                        } else {
                            // 超过3秒，重置状态
                            isPromptShown = false
                        }
                    }
                }
            },
        )

        //loamen初始化搜索引擎
        if (components.core.store.state.search.userSelectedSearchEngineId == null) {
            val baiduEngine = components.core.store.state.search.searchEngines.firstOrNull {
                it.name.contains("百度") || it.name.lowercase().contains("baidu")
            }
            if (baiduEngine != null) {
                components.useCases.searchUseCases.selectSearchEngine(baiduEngine)
            }
        }

        // 处理从AboutFragment等传入的URL
        val extraUrl = intent.getStringExtra("EXTRA_URL")
        if (extraUrl != null) {
            components.useCases.sessionUseCases.loadUrl.invoke(extraUrl)
        }
    }

    @Suppress("DEPRECATION") // ComponentActivity wants us to use registerForActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Logger.info(
            "Activity onActivityResult received with " +
                "requestCode: $requestCode, resultCode: $resultCode, data: $data",
        )

        supportFragmentManager.fragments.forEach {
            if (it is ActivityResultHandler && it.onActivityResult(requestCode, data, resultCode)) {
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        components.notificationsDelegate.unBindActivity(this)
    }

    override fun onUserLeaveHint() {
        supportFragmentManager.fragments.forEach {
            if (it is UserInteractionHandler && it.onHomePressed()) {
                return
            }
        }

        super.onUserLeaveHint()
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? =
        when (name) {
            EngineView::class.java.name -> {
                components.core.engine
                .createView(context, attrs)
                .asView()
            }

            else -> {
                super.onCreateView(parent, name, context, attrs)
            }
        }

    private fun onNonFatalCrash(crash: Crash) {
        Snackbar
            .make(findViewById(android.R.id.content), R.string.crash_report_non_fatal_message, LENGTH_LONG)
            .setAction(R.string.crash_report_non_fatal_action) {
                crashIntegration.sendCrashReport(crash)
            }.show()
    }

    private fun openPopup(webExtensionState: WebExtensionState) {
        val intent = Intent(this, WebExtensionActionPopupActivity::class.java)
        intent.putExtra("web_extension_id", webExtensionState.id)
        intent.putExtra("web_extension_name", webExtensionState.name)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    /**
     * Implementation of StandbyFragment.NavigationListener
     */
    override fun onNavigateToBrowser() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, createBrowserFragment(sessionId))
            .commit()

        // 只有在没有打开任何页面时才加载指定网址，且不在地址栏显示
        if (components.core.store.state.tabs.isEmpty()) {
            // 使用EXTERNAL标志来避免在地址栏显示URL
            components.useCases.sessionUseCases.loadUrl.invoke(
                getString(top.yooho.browser.R.string.const_nav_url),
                flags = mozilla.components.concept.engine.EngineSession.LoadUrlFlags.none()
            )
        }
    }

    /**
     *  loamen
     * 打开浏览器，支持新标签页和隐私模式
     */
    fun openToBrowser(url : String? = null, newTab : Boolean = false, private: Boolean = false){
        if (url != null) {
            if (newTab) {
                components.useCases.tabsUseCases.addTab(
                    url = url,
                    selectTab = true,
                    private = private,
                )
            } else {
                components.useCases.sessionUseCases.loadUrl(
                    url = url
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("EXTRA_URL") != null) {
            val url = intent.getStringExtra("EXTRA_URL")
            openToBrowser(url!!,true)
//            components.useCases.sessionUseCases.loadUrl.invoke(url!!)
        }
    }
}
