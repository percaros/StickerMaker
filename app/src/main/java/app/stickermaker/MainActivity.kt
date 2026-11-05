package app.stickermaker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }

    @Composable
    fun App() {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var tab by remember { mutableIntStateOf(0) }          // 0 = estáticos, 1 = animados
        var quality by remember { mutableFloatStateOf(80f) }
        var original by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var files by remember { mutableStateOf(Media.stickers(ctx, false)) }
        fun refresh() { files = Media.stickers(ctx, tab == 1) }
        LaunchedEffect(tab) { refresh() }
        fun q(): Int? = if (original) null else quality.toInt()

        fun run(job: () -> Int) = scope.launch {
            busy = true
            val msg = withContext(Dispatchers.IO) {
                runCatching(job).fold({ "Sticker guardado ($it KB)" }, { it.message ?: "Error" })
            }
            busy = false; refresh()
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }

        val cropper = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val out = r.data?.let { UCrop.getOutput(it) }
            if (r.resultCode == RESULT_OK && out != null) run { Media.saveStatic(ctx, File(out.path!!), q()) }
        }
        val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val opts = UCrop.Options().apply { setCompressionFormat(Bitmap.CompressFormat.PNG) }
            val dest = Uri.fromFile(File(ctx.cacheDir, "crop_${System.currentTimeMillis()}.png"))
            cropper.launch(
                UCrop.of(uri, dest).withAspectRatio(1f, 1f).withMaxResultSize(1024, 1024)
                    .withOptions(opts).getIntent(ctx)
            )
        }
        val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { run { Media.saveAnimated(ctx, it, q()) } }
        }

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Estáticos") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Animados") })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(original, { original = it }); Text("Calidad original")
            }
            if (!original) {
                Text("Calidad: ${quality.toInt()}")
                Slider(quality, { quality = it }, valueRange = 1f..100f)
            }
            Text("Si pasa del límite de WhatsApp (${if (tab == 0) "100" else "500"} KB) se baja sola.",
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                if (tab == 0) imgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else videoPicker.launch(arrayOf("image/gif", "video/*"))
            }) { Text(if (busy) "Procesando..." else if (tab == 0) "Elegir imagen y recortar" else "Elegir GIF o video") }
            Spacer(Modifier.height(8.dp))
            Text("${files.size} stickers (mínimo 3, máximo 30). Toca uno para borrarlo.")
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f)) {
                items(files, key = { it.path }) { f ->
                    val bmp = remember(f.path) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                    bmp?.let {
                        Image(it, null, Modifier.padding(4.dp).aspectRatio(1f).clickable {
                            Media.delete(ctx, f, tab == 1); refresh()
                        })
                    }
                }
            }
            Button(
                enabled = files.size in 3..30, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val i = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
                        putExtra("sticker_pack_id", if (tab == 1) "anim" else "static")
                        putExtra("sticker_pack_authority", "$packageName.stickercontentprovider")
                        putExtra("sticker_pack_name", if (tab == 1) "Mis stickers animados" else "Mis stickers")
                    }
                    try { startActivity(i) } catch (e: ActivityNotFoundException) {
                        Toast.makeText(ctx, "WhatsApp no respondió. ¿Está instalado y actualizado?", Toast.LENGTH_LONG).show()
                    }
                }
            ) { Text("Agregar a WhatsApp") }
        }
    }
}
