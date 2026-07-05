package io.base14.scout.kmp

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

internal object ScoutAppHolder {
    @Volatile
    var application: Application? = null
}

/**
 * Captures the [Application] before the app's own `onCreate`, so `Scout.initialize(config)` in
 * common code doesn't need a Context passed in. Registered in the library manifest; auto-merged.
 */
class ScoutInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        ScoutAppHolder.application = context?.applicationContext as? Application
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
