package io.base14.scout.android.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.base14.scout.android.ScoutBridge

class ScoutBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? =
        when (method) {
            "getContext" -> Bundle().apply { putString("context", runCatching { ScoutBridge.context() }.getOrNull()) }
            "readOwner" -> Bundle().apply { putString("owner", runCatching { ScoutBridge.readOwner() }.getOrNull()) }
            else -> null
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        args: Array<out String>?,
        sort: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        args: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        args: Array<out String>?,
    ): Int = 0
}
