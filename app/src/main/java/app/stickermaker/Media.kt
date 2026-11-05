package app.stickermaker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File

object Media {
    const val STATIC_MAX = 100 * 1024
    const val ANIM_MAX = 500 * 1024

    fun packDir(c: Context, animated: Boolean) =
        File(c.filesDir, if (animated) "anim" else "static").also { it.mkdirs() }

    fun stickers(c: Context, animated: Boolean): List<File> =
        packDir(c, animated).listFiles { f -> f.extension == "webp" }?.sortedBy { it.name }.orEmpty()

    fun version(c: Context): String =
        c.getSharedPreferences("p", 0).getInt("v", 1).toString()

    private fun bump(c: Context) {
        val p = c.getSharedPreferences("p", 0)
        p.edit().putInt("v", p.getInt("v", 1) + 1).apply()
    }

    fun delete(c: Context, f: File, animated: Boolean) {
        f.delete(); updateTray(c, animated); bump(c)
    }

    private fun encode(bmp: Bitmap, lossless: Boolean, q: Int): ByteArray {
        val fmt = if (Build.VERSION.SDK_INT >= 30)
            (if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY)
        else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        return ByteArrayOutputStream().also { bmp.compress(fmt, q, it) }.toByteArray()
    }

    /** quality null = original (sin pérdida si el sistema lo permite). Devuelve el peso final en KB. */
    fun saveStatic(c: Context, src: File, quality: Int?): Int {
        val raw = BitmapFactory.decodeFile(src.path) ?: error("No se pudo leer la imagen")
        val bmp = Bitmap.createScaledBitmap(raw, 512, 512, true)
        var q = quality ?: 95
        var data: ByteArray =
            if (quality == null && Build.VERSION.SDK_INT >= 30) encode(bmp, true, 100)
            else encode(bmp, false, q)
        while (data.size > STATIC_MAX && q > 5) {
            q -= 5
            data = encode(bmp, false, q)
        }
        if (data.size > STATIC_MAX) error("No se logró bajar de 100 KB")
        File(packDir(c, false), "s${System.currentTimeMillis()}.webp").writeBytes(data)
        updateTray(c, false); bump(c)
        return data.size / 1024
    }

    /** GIF o video -> WebP animado 512x512, máx 10 s y 500 KB. Requiere ffmpeg-kit. */
    fun saveAnimated(c: Context, uri: Uri, quality: Int?): Int {
        val input = File(c.cacheDir, "in.bin")
        c.contentResolver.openInputStream(uri)!!.use { i -> input.outputStream().use { i.copyTo(it) } }
        val out = File(packDir(c, true), "a${System.currentTimeMillis()}.webp")
        var q = quality ?: 95
        while (q >= 5) {
            val fps = if (q > 50) 15 else 10
            val vf = "crop='min(iw,ih)':'min(iw,ih)',scale=512:512:flags=lanczos,fps=$fps"
            out.delete()
            val ok = ffmpeg("-y -i \"${input.path}\" -t 10 -an -vf \"$vf\" -c:v libwebp -lossless 0 " +
                "-q:v $q -loop 0 -compression_level 6 \"${out.path}\"")
            if (!ok) error("FFmpeg falló al convertir")
            if (out.length() <= ANIM_MAX) {
                updateTray(c, true); bump(c)
                return (out.length() / 1024).toInt()
            }
            q -= 10
        }
        out.delete()
        error("No se logró bajar de 500 KB")
    }

    private fun ffmpeg(cmd: String): Boolean = try {
        val k = Class.forName("com.arthenica.ffmpegkit.FFmpegKit")
        val s = k.getMethod("execute", String::class.java).invoke(null, cmd)!!
        val rc = s.javaClass.getMethod("getReturnCode").invoke(s)!!
        rc.javaClass.getMethod("isValueSuccess").invoke(rc) as Boolean
    } catch (e: ClassNotFoundException) {
        error("FFmpeg no está incluido en esta compilación (ver app/build.gradle.kts)")
    }

    /** Icono de bandeja 96x96 PNG a partir del primer sticker. */
    private fun updateTray(c: Context, animated: Boolean) {
        val tray = File(packDir(c, animated), "tray.png")
        val first = stickers(c, animated).firstOrNull() ?: run { tray.delete(); return }
        val b = BitmapFactory.decodeFile(first.path) ?: return
        tray.outputStream().use { Bitmap.createScaledBitmap(b, 96, 96, true).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
