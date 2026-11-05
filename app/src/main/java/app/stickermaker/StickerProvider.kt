package app.stickermaker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class StickerProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var auth: String

    override fun onCreate(): Boolean {
        auth = "${context!!.packageName}.stickercontentprovider"
        matcher.addURI(auth, "metadata", 1)
        matcher.addURI(auth, "metadata/*", 2)
        matcher.addURI(auth, "stickers/*", 3)
        matcher.addURI(auth, "stickers_asset/*/*", 4)
        return true
    }

    // id del pack -> animado?
    private fun packs() = listOf("static" to false, "anim" to true)
        .filter { Media.stickers(context!!, it.second).size >= 3 }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? {
        val c = context!!
        return when (matcher.match(uri)) {
            1, 2 -> {
                val cols = arrayOf(
                    "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
                    "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
                    "sticker_pack_publisher_email", "sticker_pack_publisher_website",
                    "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
                    "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
                )
                val m = MatrixCursor(cols)
                val only = if (matcher.match(uri) == 2) uri.lastPathSegment else null
                packs().filter { only == null || it.first == only }.forEach { (id, anim) ->
                    m.addRow(arrayOf(
                        id, if (anim) "Mis stickers animados" else "Mis stickers", "Sticker Maker",
                        "tray.png", "", "", "", "", "", "", Media.version(c), 0, if (anim) 1 else 0
                    ))
                }
                m
            }
            3 -> {
                val id = uri.lastPathSegment!!
                val m = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text"))
                Media.stickers(c, id == "anim").forEach { m.addRow(arrayOf(it.name, "😀", "Sticker")) }
                m
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (matcher.match(uri) != 4) return null
        val seg = uri.pathSegments
        val f = File(Media.packDir(context!!, seg[1] == "anim"), seg[2])
        if (!f.exists()) return null
        return AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, f.length())
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        1 -> "vnd.android.cursor.dir/vnd.$auth.metadata"
        2 -> "vnd.android.cursor.item/vnd.$auth.metadata"
        3 -> "vnd.android.cursor.dir/vnd.$auth.stickers"
        4 -> if (uri.lastPathSegment == "tray.png") "image/png" else "image/webp"
        else -> null
    }

    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
    override fun delete(u: Uri, s: String?, a: Array<String>?) = 0
}
