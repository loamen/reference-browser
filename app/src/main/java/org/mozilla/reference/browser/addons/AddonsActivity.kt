/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.addons

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import mozilla.components.support.ktx.android.view.setupPersistentInsets
import org.mozilla.reference.browser.R

/**
 * An activity to manage add-ons.
 */
class AddonsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_main)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT))
        window.setupPersistentInsets(true)

//        setTitle(R.string.add_ons)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.container, AddonsFragment())
                commit()
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.install_add_ons_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.addons_menu_install_from_file -> {
                // 通过FragmentManager找到AddonsFragment并调用其打开文件选择器的方法
                val fragment = supportFragmentManager.findFragmentById(R.id.container) as? AddonsFragment
                fragment?.openFilePicker()
                true
            }
            R.id.addons_menu_install_from_url -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.container) as? AddonsFragment
                fragment?.openUrlInstaller()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
