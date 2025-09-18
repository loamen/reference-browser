/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import mozilla.components.Build
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION
import org.mozilla.reference.browser.BrowserActivity
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.settings.about.AboutItem
import org.mozilla.reference.browser.settings.about.AboutItemType
import org.mozilla.reference.browser.settings.about.AboutPageAdapter
import org.mozilla.reference.browser.settings.about.AboutPageItem
import org.mozilla.reference.browser.settings.about.AboutPageListener
import top.yooho.browser.config.PrefConst
import top.yooho.browser.utils.PrefUtil

class AboutFragment : Fragment(), AboutPageListener {
    private var aboutPageAdapter: AboutPageAdapter? = null
    private var clickCount = 0
    private var lastClickTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable {
        clickCount = 0
    }
    private var isDeveloperMode = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_about, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val appName = requireContext().resources.getString(R.string.app_name)
        (activity as AppCompatActivity).title = getString(R.string.preferences_about_page)

        // 恢复开发者模式状态
        isDeveloperMode = PrefUtil.getBoolean(requireContext(), PrefConst.DEVELOPER_MODE_KEY, false)

        val aboutText = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val geckoVersion = PackageInfoCompat.getLongVersionCode(packageInfo).toString() + " GV: " +
                    MOZ_APP_VERSION
//            + "-" + MOZ_APP_BUILDID
            String.format(
                "%s (Build #%s)\n",
                packageInfo.versionName,
                geckoVersion,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }

        val versionInfo = String.format(
            "%s \uD83D\uDCE6: %s, \n\uD83D\uDEA2: %s",
            aboutText,
            Build.VERSION,
            Build.APPLICATION_SERVICES_VERSION,
        )
        val content = getString(R.string.about_content, appName)

        val aboutView = view.findViewById<TextView>(R.id.about_content)
        aboutView.text = content

        val versionInfoView = view.findViewById<TextView>(R.id.about_text)
        versionInfoView.text = versionInfo

        versionInfoView.setOnClickListener { v ->
            val clipBoard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipBoard.setPrimaryClip(ClipData.newPlainText(versionInfo, versionInfo))

            Toast.makeText(requireContext(), getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        }

        // 添加wordmark点击监听器
        val wordmarkView = view.findViewById<View>(R.id.wordmark)
        wordmarkView.setOnClickListener {
            handleWordmarkClick()
        }

        setupAboutList(view)
    }

    private fun handleWordmarkClick() {
        val currentTime = System.currentTimeMillis()

        // 如果超过15秒，重置计数
        if (currentTime - lastClickTime > 15000) {
            clickCount = 0
        }

        clickCount++
        lastClickTime = currentTime

        // 移除之前的重置任务
        handler.removeCallbacks(resetRunnable)

        var message = ""

        if (clickCount % 5 == 0) {
            isDeveloperMode = true

            // 重置计数
            clickCount = 0
        } else {
            // 1-5次点击，安排20秒后重置
            handler.postDelayed(resetRunnable, 20000)
            isDeveloperMode = false
            // 第5次点击提示即将进入开发者模式
            if (clickCount == 4) {
                message = getString(top.yooho.browser.R.string.developer_mode_one_more_click)
            }
        }

        PrefUtil.saveBoolean(requireContext(), PrefConst.DEVELOPER_MODE_KEY, isDeveloperMode)

        if (isDeveloperMode) {
            message = getString(top.yooho.browser.R.string.developer_mode_enabled)
        }

        if (!message.isEmpty()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }

        // 更新菜单列表
        aboutPageAdapter?.submitList(populateAboutList())
    }

    private fun setupAboutList(view: View) {
        aboutPageAdapter = AboutPageAdapter(this)

        val aboutList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.about_list)
        aboutList.adapter = aboutPageAdapter
        aboutList.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL,
            ),
        )

        aboutPageAdapter?.submitList(populateAboutList())
    }

    private fun populateAboutList(): List<AboutPageItem> {
        val list = mutableListOf(
            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.SUPPORT,
                    getString(top.yooho.browser.R.string.const_support_url),
                ),
                getString(R.string.about_support),
            ),

            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.PRIVACY_NOTICE,
                    getString(top.yooho.browser.R.string.const_privacy_url),
                ),
                getString(R.string.about_privacy_notice),
            ),
            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.LICENSING_INFO,
                    "about:license",
                ),
                getString(R.string.about_licensing_information),
            ),
        )

        // 如果处于开发者模式，则添加开发者社区选项
        if (isDeveloperMode) {
            list.add(
                0,
                AboutPageItem(
                    AboutItem.ExternalLink(
                        AboutItemType.SUPPORT,
                        getString(top.yooho.browser.R.string.const_homepage_url),
                    ),
                    "官方主页",
                ),
            )
        }

        return list
    }

    override fun onAboutItemClicked(item: AboutItem) {
        when (item) {
            is AboutItem.ExternalLink -> {
                openExternalLink(item.url)
            }

            is AboutItem.Libraries -> {
                // 处理开源库点击事件
            }

            is AboutItem.Crashes -> {
                // 处理崩溃报告点击事件
            }
        }
    }

    private fun openExternalLink(url: String) {
        val intent = Intent(requireActivity(), BrowserActivity::class.java).apply {
            putExtra("EXTRA_URL", url)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        aboutPageAdapter = null
        handler.removeCallbacks(resetRunnable)
    }
}
