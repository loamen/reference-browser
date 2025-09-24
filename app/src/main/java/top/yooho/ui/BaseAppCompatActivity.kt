package top.yooho.ui

import android.os.Bundle
import android.os.PersistableBundle
import androidx.appcompat.app.AppCompatActivity
import top.yooho.ui.theme.DefaultThemeManager
import top.yooho.ui.theme.ThemeManager

open class BaseAppCompatActivity : AppCompatActivity() {
    lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        themeManager = DefaultThemeManager(this)
        super.onCreate(savedInstanceState)
    }

    // 如果需要支持 persistable bundle 的情况，也可以保留这个方法
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        themeManager = DefaultThemeManager(this)
        super.onCreate(savedInstanceState, persistentState)
    }
}

