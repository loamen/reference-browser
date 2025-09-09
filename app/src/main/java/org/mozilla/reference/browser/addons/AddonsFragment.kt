/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.addons

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManagerException
import mozilla.components.feature.addons.ui.AddonsManagerAdapter
import mozilla.components.feature.addons.ui.AddonsManagerAdapterDelegate
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.reference.browser.R
import org.mozilla.reference.browser.ext.components
import mozilla.components.feature.addons.R as addonsR

/**
 * Fragment use for managing add-ons.
 */
class AddonsFragment :
    Fragment(),
    AddonsManagerAdapterDelegate {
    private val webExtensionPromptFeature = ViewBoundFeatureWrapper<WebExtensionPromptFeature>()
    private lateinit var recyclerView: RecyclerView
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var addons: List<Addon>
    private var adapter: AddonsManagerAdapter? = null
    private lateinit var getFileActivityResultLauncher: ActivityResultLauncher<Intent>

    private val addonProgressOverlay: View
        get() = requireView().findViewById(R.id.addonProgressOverlay)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_add_ons, container, false)

    override fun onViewCreated(
        rootView: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(rootView, savedInstanceState)
        bindRecyclerView(rootView)
        webExtensionPromptFeature.set(
            feature = WebExtensionPromptFeature(
                store = requireContext().components.core.store,
                context = requireContext(),
                fragmentManager = parentFragmentManager,
            ),
            owner = this,
            view = rootView,
        )

        // 注册文件选择器
        getFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        installAddonFromFile(uri)
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()

        this@AddonsFragment.view?.let { view ->
            bindRecyclerView(view)
        }

        addonProgressOverlay.visibility = View.GONE
    }

    private fun bindRecyclerView(rootView: View) {
        recyclerView = rootView.findViewById(R.id.add_ons_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        scope.launch {
            try {
                addons = requireContext()
                    .components.core.addonManager
                    .getAddons()

                scope.launch(Dispatchers.Main) {
                    adapter = AddonsManagerAdapter(
                        this@AddonsFragment,
                        addons,
                        store = requireContext().components.core.store,
                    )
                    recyclerView.adapter = adapter
                }
            } catch (e: AddonManagerException) {
                scope.launch(Dispatchers.Main) {
                    Toast
                        .makeText(
                            activity,
                            addonsR.string.mozac_feature_addons_failed_to_query_extensions,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    override fun onAddonItemClicked(addon: Addon) {
        if (addon.isInstalled()) {
            val intent = Intent(context, InstalledAddonDetailsActivity::class.java)
            intent.putExtra("add_on", addon)
            startActivity(intent)
        } else {
            val intent = Intent(context, AddonDetailsActivity::class.java)
            intent.putExtra("add_on", addon)
            startActivity(intent)
        }
    }

    override fun onInstallAddonButtonClicked(addon: Addon) {
        if (isInstallationInProgress) {
            return
        }
        installAddon(addon)
    }

    private val installAddon: ((Addon) -> Unit) = { addon ->
        addonProgressOverlay.visibility = View.VISIBLE
        isInstallationInProgress = true
        requireContext().components.core.addonManager.installAddon(
            url = addon.downloadUrl,
            onSuccess = {
                runIfFragmentIsAttached {
                    isInstallationInProgress = false
                    this@AddonsFragment.view?.let { view ->
                        bindRecyclerView(view)
                    }
                    addonProgressOverlay.visibility = View.GONE
                }
            },
            onError = { _ ->
                runIfFragmentIsAttached {
                    addonProgressOverlay.visibility = View.GONE
                    isInstallationInProgress = false
                }
            },
        )
    }

    //loamen 重写打开文件选择器
    fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/x-xpinstall" // XPI文件是Firefox扩展文件
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            getFileActivityResultLauncher.launch(intent)
        } catch (e: Exception) {
            Logger.error(getString(R.string.unable_to_open_the_file_chooser), e)

            Toast.makeText(
                requireContext(),
                getString(R.string.unable_to_open_the_file_chooser) + e.message,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 从文件安装扩展
     */
    private fun installAddonFromFile(uri: android.net.Uri) {
        addonProgressOverlay.visibility = View.VISIBLE
        isInstallationInProgress = true

        Logger.info("Installing add-on from file: ${uri.toString()}")

        requireContext().components.core.addonManager.installAddon(
            url = uri.toString(),
            onSuccess = {
                scope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        isInstallationInProgress = false
                        this@AddonsFragment.view?.let { view ->
                            bindRecyclerView(view)
                        }
                        addonProgressOverlay.visibility = View.GONE
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.the_add_on_installation_was_successful),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onError = { e ->
                scope.launch(Dispatchers.Main) {
                    runIfFragmentIsAttached {
                        addonProgressOverlay.visibility = View.GONE
                        isInstallationInProgress = false

                        Logger.error(getString(R.string.the_add_on_installation_was_failed), e)

                        Toast.makeText(
                            requireContext(),
                            getString(R.string.the_add_on_installation_was_failed) + e.message,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
        )
    }

    /**
     * Whether or not an add-on installation is in progress.
     */
    private var isInstallationInProgress = false
}
