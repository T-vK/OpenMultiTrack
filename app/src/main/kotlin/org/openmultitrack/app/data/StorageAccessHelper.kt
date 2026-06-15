package org.openmultitrack.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/** Checks writable storage paths and opens system screens when access is missing. */
object StorageAccessHelper {
    fun canWriteTo(path: String): Boolean {
        val dir = File(path)
        if (!dir.exists()) return dir.mkdirs()
        return dir.isDirectory && dir.canWrite()
    }

    fun hasManageAllFilesAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return Environment.isExternalStorageManager()
    }

    fun isAppScopedStoragePath(context: Context, path: String): Boolean {
        val appRoot = context.getExternalFilesDir(null)?.absolutePath ?: return false
        return path.startsWith(appRoot)
    }

    fun requiresManageAllFilesAccess(context: Context, path: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (isAppScopedStoragePath(context, path)) return false
        if (hasManageAllFilesAccess(context)) return false
        val root = Environment.getExternalStorageDirectory().absolutePath
        return path.startsWith(root) ||
            path.startsWith("/storage/") ||
            path.startsWith("/mnt/")
    }

    fun needsLegacyStoragePermission(context: Context, path: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        val root = Environment.getExternalStorageDirectory().absolutePath
        if (!path.startsWith(root)) return false
        return !hasManageAllFilesAccess(context)
    }

    @Deprecated("Use requiresManageAllFilesAccess", ReplaceWith("requiresManageAllFilesAccess(context, path)"))
    fun needsManageAllFilesAccess(context: Context, path: String): Boolean =
        requiresManageAllFilesAccess(context, path)

    fun openManageAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetails)
        }
    }

    fun openAppBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }.getOrElse {
                openBatteryOptimizationSettings(context)
            }
        } else {
            openBatteryOptimizationSettings(context)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
