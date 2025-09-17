/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import mozilla.components.Build
import org.mozilla.geckoview.BuildConfig.MOZ_APP_BUILDID
import org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.settings.about.AboutItem
import org.mozilla.reference.browser.settings.about.AboutItemType
import org.mozilla.reference.browser.settings.about.AboutPageAdapter
import org.mozilla.reference.browser.settings.about.AboutPageItem
import org.mozilla.reference.browser.settings.about.AboutPageListener

class AboutFragment : Fragment(), AboutPageListener {
    private var aboutPageAdapter: AboutPageAdapter? = null

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

        val aboutText = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val geckoVersion = PackageInfoCompat.getLongVersionCode(packageInfo).toString() + " GV: " +
                MOZ_APP_VERSION + "-" + MOZ_APP_BUILDID
            String.format(
                "%s (Build #%s)\n",
                packageInfo.versionName,
                geckoVersion,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }

        val versionInfo = String.format(
            "%s \uD83D\uDCE6: %s, %s\n\uD83D\uDEA2: %s",
            aboutText,
            Build.VERSION,
            Build.GIT_HASH,
            Build.APPLICATION_SERVICES_VERSION,
        )
        val content = HtmlCompat.fromHtml(
            resources.getString(R.string.about_content, appName),
            FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM,
        )

        val aboutView = view.findViewById<TextView>(R.id.about_content)
        aboutView.text = content

        val versionInfoView = view.findViewById<TextView>(R.id.about_text)
        versionInfoView.text = versionInfo

        versionInfoView.setOnClickListener { v ->
            val clipBoard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipBoard.setPrimaryClip(ClipData.newPlainText(versionInfo, versionInfo))

            Toast.makeText(requireContext(), getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
        }

        setupAboutList(view)
    }

    private fun setupAboutList(view: View) {
        aboutPageAdapter = AboutPageAdapter(this)

        val aboutList = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.about_list)
        aboutList.adapter = aboutPageAdapter
        aboutList.addItemDecoration(
            DividerItemDecoration(
                requireContext(),
                DividerItemDecoration.VERTICAL
            )
        )

        aboutPageAdapter?.submitList(populateAboutList())
    }

    private fun populateAboutList(): List<AboutPageItem> {
        return listOf(
            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.SUPPORT,
                    "https://support.mozilla.org"
                ),
                getString(R.string.about_support)
            ),

            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.PRIVACY_NOTICE,
                    "https://www.mozilla.org/privacy"
                ),
                getString(R.string.about_privacy_notice)
            ),
            AboutPageItem(
                AboutItem.ExternalLink(
                    AboutItemType.LICENSING_INFO,
                    "about:license"
                ),
                getString(R.string.about_licensing_information)
            )
        )
    }

    override fun onAboutItemClicked(item: org.mozilla.reference.browser.settings.about.AboutItem) {
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
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        aboutPageAdapter = null
    }
}
