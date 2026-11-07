// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** "1-5,8" -> índices 0-based. Vazio = todas. Devolve null se estiver inválido. */
fun parseFaixa(txt: String, n: Int): List<Int>? {
    if (txt.isBlank()) return (0 until n).toList()
    val out = LinkedHashSet<Int>()
    for (parte in txt.replace(" ", "").split(",")) {
        if (parte.isEmpty()) continue
        val ab = parte.split("-")
        val a = (if (ab[0].isEmpty()) "1" else ab[0]).toIntOrNull() ?: return null
        val b = if (ab.size > 1) ((if (ab[1].isEmpty()) n.toString() else ab[1]).toIntOrNull() ?: return null) else a
        if (a < 1 || b > n || a > b) return null
        for (i in a..b) out.add(i - 1)
    }
    return out.toList()
}

private val FORMATOS = listOf(
    Triple("PDF (páginas como imagens JPEG)", "pdf", "application/pdf"),
    Triple("CBZ (quadrinho)", "cbz", "application/vnd.comicbook+zip"),
    Triple("EPUB (layout fixo, páginas como imagens)", "epub", "application/epub+zip"),
    Triple("ZIP de imagens JPEG", "zip", "application/zip")
)

/** Exporta o projeto (ou páginas escolhidas) para PDF, CBZ, EPUB ou ZIP, com qualidade e tamanho à escolha. */
@Composable
fun DialogoExportar(proj: Projeto, cfg: Config, selecao: List<Int>, onFechar: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    val n = proj.estado.paginas.size
    var fmt by remember { mutableIntStateOf(0) }
    var escopoPag by remember { mutableIntStateOf(if (selecao.isNotEmpty()) 1 else 0) }
    var faixa by remember { mutableStateOf("") }
    var qualidade by remember { mutableFloatStateOf(cfg.qualidade.toFloat()) }
    var lado by remember { mutableIntStateOf(cfg.ladoMax) }
    var progresso by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pendentes by remember { mutableStateOf<List<Int>>(emptyList()) }

    val criar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val op = OpcoesExport(qualidade.toInt(), lado)
        val indices = pendentes
        val formato = FORMATOS[fmt].second
        progresso = Pair(0, indices.size)
        job = escopo.launch {
            try {
                val saida = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Não consegui gravar neste local.")
                saida.use { os ->
                    val prog: (Int, Int) -> Unit = { a, b -> progresso = Pair(a, b) }
                    when (formato) {
                        "pdf" -> Exportador.paraPdf(proj, indices, os, op, prog)
                        "epub" -> Exportador.paraEpub(proj, indices, proj.nome.substringBeforeLast('.'), os, op, prog)
                        else -> Exportador.paraCbz(proj, indices, os, op, prog)
                    }
                }
                Som.concluir(ctx)
                Toast.makeText(ctx, "Exportado com sucesso.", Toast.LENGTH_LONG).show()
                progresso = null
                onFechar()
            } catch (e: CancellationException) {
                progresso = null
                Toast.makeText(ctx, "Cancelado.", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                progresso = null
                Toast.makeText(ctx, "Erro: ${e.message ?: e}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (progresso != null) {
        val (a, b) = progresso!!
        AlertDialog(
            onDismissRequest = { }, title = { Text("Exportando...") },
            text = { Text("Página $a de $b") },
            confirmButton = { TextButton(onClick = { job?.cancel() }) { Text("Cancelar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onFechar, title = { Text("Exportar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Formato", fontWeight = FontWeight.Bold)
                FORMATOS.forEachIndexed { i, f ->
                    Row(Modifier.fillMaxWidth().clickable { fmt = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fmt == i, onClick = { fmt = i }); Text(f.first, fontSize = 14.sp)
                    }
                }
                Text("Páginas", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                val opcoes = listOf("Todas ($n)", "Selecionadas (${selecao.size})", "Intervalo")
                opcoes.forEachIndexed { i, rot ->
                    Row(Modifier.fillMaxWidth().clickable { if (i != 1 || selecao.isNotEmpty()) escopoPag = i }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = escopoPag == i, enabled = i != 1 || selecao.isNotEmpty(), onClick = { escopoPag = i }); Text(rot, fontSize = 14.sp)
                    }
                }
                if (escopoPag == 2) OutlinedTextField(value = faixa, onValueChange = { faixa = it }, singleLine = true, label = { Text("Ex.: 1-5,8") })
                Text("Qualidade JPEG: ${qualidade.toInt()}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Slider(value = qualidade, onValueChange = { qualidade = it }, valueRange = 40f..100f)
                Text("Tamanho (maior lado)", fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    for ((rot, v) in listOf("1200" to 1200, "1800" to 1800, "2400" to 2400, "3200" to 3200, "Original" to 0)) {
                        TextButton(onClick = { lado = v }) { Text(rot, color = if (lado == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }
                    }
                }
                Text("Menor qualidade/tamanho = arquivo menor. PDFs exportados guardam cada página como imagem.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val indices = when (escopoPag) {
                    0 -> (0 until n).toList()
                    1 -> selecao
                    else -> parseFaixa(faixa, n) ?: run { Toast.makeText(ctx, "Intervalo inválido.", Toast.LENGTH_SHORT).show(); return@TextButton }
                }
                if (indices.isEmpty()) return@TextButton
                cfg.qualidade = qualidade.toInt(); cfg.ladoMax = lado
                pendentes = indices
                criar.launch(proj.nome.substringBeforeLast('.') + "." + FORMATOS[fmt].second)
            }) { Text("Escolher destino") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

// ============================ captura de tela ============================

/** Seleção de uma área da tela (arrastar). Devolve o retângulo em pixels da janela. */
@Composable
fun CapturaSelecao(onPronto: (Offset, Size) -> Unit, onCancelar: () -> Unit) {
    var ini by remember { mutableStateOf<Offset?>(null) }
    var fim by remember { mutableStateOf<Offset?>(null) }
    var origem by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxSize().background(Color(0x55000000))
            .onGloballyPositioned { origem = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { ini = it; fim = it },
                    onDrag = { c, _ -> fim = c.position; c.consume() },
                    onDragEnd = { }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val a = ini; val b = fim
            if (a != null && b != null) {
                drawRect(Color(0xFF3B82F6), Offset(minOf(a.x, b.x), minOf(a.y, b.y)), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)), style = Stroke(4f))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(16.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)) {
            Text("Arraste para marcar a área", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            TextButton(onClick = {
                val a = ini; val b = fim
                if (a != null && b != null && kotlin.math.abs(b.x - a.x) > 30 && kotlin.math.abs(b.y - a.y) > 30) {
                    onPronto(Offset(minOf(a.x, b.x) + origem.x, minOf(a.y, b.y) + origem.y), Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)))
                }
            }) { Text("Capturar") }
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    }
}

@Composable
fun ResultadoCaptura(bmp: Bitmap, onFechar: () -> Unit) {
    val ctx = LocalContext.current
    val salvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                Som.concluir(ctx)
                Toast.makeText(ctx, "Captura salva.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun arquivoTemp(): android.net.Uri {
        val dir = File(ctx.cacheDir, "abertos").apply { mkdirs() }
        val f = File(dir, "captura_${System.currentTimeMillis()}.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }
    AlertDialog(
        onDismissRequest = onFechar, title = { Text("Captura") },
        text = { Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp)) },
        confirmButton = {
            Row {
                TextButton(onClick = { salvar.launch("captura.png") }) { Text("Salvar") }
                TextButton(onClick = {
                    val uri = arquivoTemp()
                    val i = Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    ctx.startActivity(Intent.createChooser(i, "Compartilhar captura"))
                }) { Text("Compartilhar") }
                TextButton(onClick = {
                    try {
                        val uri = arquivoTemp()
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "captura", uri))
                        Toast.makeText(ctx, "Copiada.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Não consegui copiar; use Compartilhar.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Copiar") }
            }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}
