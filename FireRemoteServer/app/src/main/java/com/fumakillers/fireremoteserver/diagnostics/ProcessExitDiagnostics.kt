package com.fumakillers.fireremoteserver.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import com.fumakillers.fireremoteserver.logging.RemoteLogger

object ProcessExitDiagnostics {
    fun logPreviousExit(context: Context) {
        try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val previousExit = activityManager
                .getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .firstOrNull()
            if (previousExit == null) {
                RemoteLogger.info(TAG, "Previous process exit information is unavailable")
                return
            }
            RemoteLogger.info(TAG, format(previousExit))
        } catch (error: RuntimeException) {
            RemoteLogger.warn(TAG, "Could not read previous process exit information", error)
        }
    }

    private fun format(info: ApplicationExitInfo): String = buildString {
        append("Previous process exit: reason=")
        append(reasonName(info.reason))
        append('(')
        append(info.reason)
        append(") timestamp=")
        append(info.timestamp)
        append(" importance=")
        append(info.importance)
        append(" pssKb=")
        append(info.pss)
        append(" rssKb=")
        append(info.rss)
        append(" status=")
        append(info.status)
        info.description?.toString()?.replace('\n', ' ')?.takeIf { it.isNotBlank() }?.let {
            append(" description=")
            append(it)
        }
    }

    internal fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        else -> "UNRECOGNIZED"
    }

    private const val TAG = "FireRemoteApplication"
}
