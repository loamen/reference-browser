package top.yooho.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.Window
import androidx.core.content.ContextCompat
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.support.ktx.android.content.getColorFromAttr
import mozilla.components.support.ktx.android.content.res.resolveAttribute
import mozilla.components.support.ktx.android.view.createWindowInsetsController
import org.mozilla.reference.browser.R

abstract class ThemeManager {

    /**
     * Handles status bar theme change since the window does not dynamically recreate
     */

    abstract fun applyStatusBarTheme()

    @Suppress("DEPRECATION")
    fun updateNavigationBar(window: Window, context: Context) {
        window.navigationBarColor = context.getColorFromAttr(R.attr.layer1)
    }

    fun clearLightSystemBars(window: Window) {
        // API level can display handle light navigation bar color
        window.createWindowInsetsController().isAppearanceLightNavigationBars = false
    }

    abstract fun applyTheme(toolbar: BrowserToolbar)

    @Suppress("DEPRECATION")
    fun updateLightSystemBars(window: Window, context: Context) {
        // API level can display handle light navigation bar color
        window.createWindowInsetsController().isAppearanceLightNavigationBars = true
        updateNavigationBar(window, context)
    }

    fun updateDarkSystemBars(window: Window, context: Context) {
        // API level can display handle light navigation bar color
        window.createWindowInsetsController().isAppearanceLightNavigationBars = false
        updateNavigationBar(window, context)
    }

    abstract fun applyStatusBarThemeTabsTray()

    abstract fun getContext(): Context
    abstract fun getIconColor(): Int
}

class DefaultThemeManager(
    private val activity: Activity
) : ThemeManager() {

    private var currentContext:Context = activity

    override fun applyStatusBarThemeTabsTray() {
        when (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO,
            -> {
                updateLightSystemBars(activity.window, activity)
            }
            Configuration.UI_MODE_NIGHT_UNDEFINED, //we assume dark mode is default
            Configuration.UI_MODE_NIGHT_YES -> {
                updateDarkSystemBars(activity.window, activity)
            }
        }
    }

    override fun getContext(): Context {
        return currentContext
    }

    override fun getIconColor(): Int {
            return R.color.fx_mobile_private_icon_color_primary
    }

    override fun applyStatusBarTheme() {
        when (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO,
                -> {
                updateLightSystemBars(activity.window, currentContext)
            }
            Configuration.UI_MODE_NIGHT_UNDEFINED, //we assume dark mode is default
            Configuration.UI_MODE_NIGHT_YES -> {
                updateDarkSystemBars(activity.window, currentContext)
            }
        }
    }

    override fun applyTheme(toolbar: BrowserToolbar) {
        applyStatusBarTheme()
        
        toolbar.background = ContextCompat.getDrawable(currentContext, R.drawable.toolbar_dark_background)

        var textPrimary = ContextCompat.getColor(currentContext, currentContext.theme.resolveAttribute(R.attr.textPrimary))
        var textSecondary = ContextCompat.getColor(currentContext, currentContext.theme.resolveAttribute(R.attr.textSecondary))

        toolbar.edit.colors = toolbar.edit.colors.copy(
            text = textPrimary,
            hint = textSecondary
        )
        toolbar.display.colors = toolbar.display.colors.copy(
            text = textPrimary,
            hint = textSecondary,
            siteInfoIconSecure = textPrimary,
            siteInfoIconInsecure = textPrimary,
            menu = textPrimary,

        )

        /*
        * When switching between modes, we need to set the url background to something else before
        * before setting it to the correct url background
        * */
        toolbar.display.setUrlBackground(ContextCompat.getDrawable(currentContext, R.drawable.toolbar_dark_background))
        toolbar.display.setUrlBackground(ContextCompat.getDrawable(currentContext, R.drawable.url_background))
    }

}
