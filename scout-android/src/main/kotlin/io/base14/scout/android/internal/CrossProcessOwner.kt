package io.base14.scout.android.internal

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Process

internal object CrossProcessOwner {
    fun queryContext(context: Context): String? =
        runCatching {
            val uri = Uri.parse("content://${context.packageName}.scout.bridge")
            context.contentResolver.call(uri, "getContext", null, null)?.getString("context")
        }.getOrNull()

    fun isMainProcess(context: Context): Boolean = currentProcessName(context) == context.packageName

    private fun currentProcessName(context: Context): String =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val pid = Process.myPid()
                am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: context.packageName
            }
        }.getOrDefault(context.packageName)
}
