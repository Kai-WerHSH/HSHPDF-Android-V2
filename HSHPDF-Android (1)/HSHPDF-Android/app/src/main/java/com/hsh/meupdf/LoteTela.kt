// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val OPERACOES = listOf(
    "Converter cada arquivo para PDF",
    "Converter cada arquivo para CBZ",
    "Converter cada arquivo para EPUB",
    "Juntar tudo em um único PDF",
    "Juntar tudo em um único CBZ"
)

/** Lote: vários arquivos de uma vez (PDF, CBZ, CBR, EPUB, imagens) para PDF, CBZ ou EPUB, ou juntar tudo em um. */
@Composable
fun LoteTela(cfg: Config, onVoltar: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var arquivos by remember { mutableStateOf<List<Pair<Uri, String>>>(emptyList()) }
    var destino by remember { mutableStateOf<Uri?>(null) }
    var op by remember { mutableIntStateOf(0) }
    var qualidade by remember { mutableFloatStateOf(cfg.qualidade.toFloat()) }
    var lado by remember { mutableIntStateOf(cfg.ladoMax) }
    var rodando by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val log = remember { mutableStateListOf<String>() }

    val escolherArquivos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        arquivos = uris.map { Pair(it, Abertura.nomeDe(ctx, it)) }
    }
    val escolherPasta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) { }
            destino = uri
        }
    }

    fun gravar(pasta: DocumentFile, nome: String, escrever: suspend (java.io.OutputStream) -> Unit): suspend () -> Unit = {
        var nomeFinal = nome
        var k = 2
        while (pasta.findFile(nomeFinal) != null) {
            nomeFinal = nome.substringBeforeLast('.') + "_$k." + nome.substringAfterLast('.')
            k++
        }
        val f = pasta.createFile("application/octet-stream", nomeFinal) ?: throw IllegalStateException("Não consegui criar $nomeFinal na pasta.")
        val os = ctx.contentResolver.openOutputStream(f.uri) ?: throw IllegalStateException("Não consegui gravar $nomeFinal.")
        os.use { escrever(it) }
    }

    fun iniciar() {
        val pasta = destino?.let { DocumentFile.fromTreeUri(ctx, it) }
        if (arquivos.isEmpty() || pasta == null) return
        cfg.qualidade = qualidade.toInt(); cfg.ladoMax = lado
        val opcoes = OpcoesExport(qualidade.toInt(), lado)
        rodando = true
        log.clear()
        job = escopo.launch {
            try {
                val ext = if (op == 0 || op == 3) "pdf" else if (op == 1 || op == 4) "cbz" else "epub"
                suspend fun exportar(p: Projeto, nome: String) {
                    val todas = (0 until p.estado.paginas.size).toList()
                    val gravacao = gravar(pasta, "$nome.$ext") { os ->
                        when (ext) {
                            "pdf" -> Exportador.paraPdf(p, todas, os, opcoes) { _, _ -> }
                            "cbz" -> Exportador.paraCbz(p, todas, os, opcoes) { _, _ -> }
                            else -> Exportador.paraEpub(p, todas, nome, os, opcoes) { _, _ -> }
                        }
                    }
                    gravacao()
                }
                if (op >= 3) {
                    val junto = Projeto("Juntado", null)
                    val paginas = ArrayList<Pagina>()
                    for ((uri, nome) in arquivos) {
                        log.add("Lendo $nome...")
                        val (arq, n) = Abertura.importar(ctx, uri)
                        val f = Abertura.abrirArquivo(arq, n)
                        junto.fontes.add(f)
                        for (i in 0 until f.paginas) {
                            val (w, h) = f.tamanho(i)
                            paginas.add(Pagina(junto.novoId(), f, i, 0, w, h, emptyList()))
                        }
                    }
                    junto.estado = Estado(paginas)
                    log.add("Gravando arquivo único (${paginas.size} páginas)...")
                    exportar(junto, "Juntado")
                    junto.fechar()
                    log.add("✓ Juntado.$ext")
                } else {
                    for ((uri, nome) in arquivos) {
                        log.add("Convertendo $nome...")
                        try {
                            val (arq, n) = Abertura.importar(ctx, uri)
                            val f = Abertura.abrirArquivo(arq, n)
                            val p = Projeto.deFonte(n, null, f)
                            exportar(p, n.substringBeforeLast('.'))
                            p.fechar()
                            log.add("✓ ${n.substringBeforeLast('.')}.$ext")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            log.add("✗ $nome: ${e.message ?: e}")
                        }
                    }
                }
                Som.concluir(ctx)
                log.add("Concluído.")
            } catch (e: CancellationException) {
                log.add("Cancelado.")
            } catch (e: Throwable) {
                log.add("Erro: ${e.message ?: e}")
            } finally {
                rodando = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVoltar) { Text("‹ Voltar") }
            Text("Lote", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("1. Arquivos", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = { escolherArquivos.launch(arrayOf("*/*")) }, enabled = !rodando) {
                Text(if (arquivos.isEmpty()) "Escolher arquivos..." else "${arquivos.size} arquivo(s) — trocar")
            }
            arquivos.take(6).forEach { Text("• ${it.second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (arquivos.size > 6) Text("… e mais ${arquivos.size - 6}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("2. O que fazer", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            OPERACOES.forEachIndexed { i, rot ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !rodando) { op = i }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = op == i, onClick = { op = i }, enabled = !rodando)
                    Text(rot, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Qualidade JPEG: ${qualidade.toInt()}", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
            Slider(value = qualidade, onValueChange = { qualidade = it }, valueRange = 40f..100f, enabled = !rodando)
            Row {
                for ((rot, v) in listOf("1200" to 1200, "1800" to 1800, "2400" to 2400, "3200" to 3200, "Original" to 0)) {
                    TextButton(onClick = { lado = v }, enabled = !rodando) {
                        Text(rot, fontSize = 12.sp, color = if (lado == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("3. Pasta de destino", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Button(onClick = { escolherPasta.launch(null) }, enabled = !rodando) { Text(if (destino == null) "Escolher pasta..." else "Pasta escolhida — trocar") }
            Spacer(Modifier.height(14.dp))
            if (!rodando) {
                Button(onClick = { if (arquivos.isEmpty() || destino == null) Toast.makeText(ctx, "Escolha os arquivos e a pasta.", Toast.LENGTH_SHORT).show() else iniciar() }) {
                    Text("Converter", modifier = Modifier.padding(6.dp))
                }
            } else {
                Button(onClick = { job?.cancel() }) { Text("Cancelar", modifier = Modifier.padding(6.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            for (l in log) Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
