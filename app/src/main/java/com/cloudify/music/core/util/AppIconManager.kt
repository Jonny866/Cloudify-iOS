package com.cloudify.music.core.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {

    fun setAppIcon(context: Context, theme: String) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName

            val violetComponent = ComponentName(pkg, "$pkg.MainActivityDefault")
            val yellowComponent = ComponentName(pkg, "$pkg.MainActivityYellow")

            val isYellow = theme.equals("yellow", ignoreCase = true)

            val (enableTarget, disableTarget) = if (isYellow) {
                yellowComponent to violetComponent
            } else {
                violetComponent to yellowComponent
            }

            val enableState = pm.getComponentEnabledSetting(enableTarget)
            val disableState = pm.getComponentEnabledSetting(disableTarget)

            if (enableState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED &&
                disableState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                return
            }

            if (!isYellow &&
                enableState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                disableState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                return
            }

            pm.setComponentEnabledSetting(
                enableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            pm.setComponentEnabledSetting(
                disableTarget,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
