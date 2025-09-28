/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser.settings

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.mozilla.reference.browser.R

object Settings {
    fun isTelemetryEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(R.string.pref_key_telemetry),
            true,
        )

    fun getOverrideAmoUser(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context.getString(R.string.pref_key_override_amo_user),
            "",
        ) ?: ""

    fun getOverrideAmoCollection(context: Context): String =
        PreferenceManager.getDefaultSharedPreferences(context).getString(
            context.getString(R.string.pref_key_override_amo_collection),
            "",
        ) ?: ""

    fun setOverrideAmoUser(
        context: Context,
        value: String,
    ) {
        val key = context.getString(R.string.pref_key_override_amo_user)
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(key, value)
        }
    }

    fun setOverrideAmoCollection(
        context: Context,
        value: String,
    ) {
        val key = context.getString(R.string.pref_key_override_amo_collection)
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(key, value)
        }
    }

    fun isAmoCollectionOverrideConfigured(context: Context): Boolean =
        getOverrideAmoUser(context).isNotEmpty() && getOverrideAmoCollection(context).isNotEmpty()

    //loamen 主题
    fun getAppTheme(context: Context): Int {
        val themeString = PreferenceManager.getDefaultSharedPreferences(context).getString(
            context.getString(top.yooho.browser.R.string.pref_key_theme),
            context.getString(top.yooho.browser.R.string.preferences_theme_default),
        )
        return themeString!!.toInt()
    }

    fun setAppTheme(context: Context, mode: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit {
            putString(
                context.getString(top.yooho.browser.R.string.pref_key_theme),
                mode.toString(),
            )
        }
    }

    private fun putString(p0: String, p1: Int) {}

    //是否限制主页
    fun shouldShowHomeButton(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
            context.getString(top.yooho.browser.R.string.pref_key_show_home_button), true,
        )
}
