// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val uriExterna = mutableStateOf<Uri?>(null)
    private val projetos = mutableStateListOf<Projeto>()

    private fun uriDoIntent(i: Intent?): Uri? {
        if (i == null) return null
        return i.data ?: IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
    }

    /** Esconde (ou mostra) hora, bateria e botões do sistema. Deslizar na borda mostra por um instante. */
    fun definirTelaCheia(ligada: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        if (ligada) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val a = window.attributes
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = a
        }
        uriExterna.value = uriDoIntent(intent)
        val cfg = Config(this)
        setContent { App(cfg, uriExterna, projetos) { definirTelaCheia(it) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uriExterna.value = uriDoIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        projetos.forEach { Serial.salvar(this, it) }
    }
}

@Composable
fun App(cfg: Config, uriExterna: MutableState<Uri?>, projetos: MutableList<Projeto>, telaCheia: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var tema by remember { mutableStateOf(cfg.tema) }
    var acento by remember { mutableStateOf(cfg.acento) }
    var ativo by remember { mutableIntStateOf(-1) }
    var noLote by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var recentes by remember { mutableStateOf(cfg.recentes()) }
    val escopo = rememberCoroutineScope()

    fun abrirProjeto(p: Projeto) {
        projetos.add(p)
        ativo = projetos.size - 1
        noLote = false
    }

    fun abrirUri(uri: Uri, restaurar: Boolean = true) {
        val existente = projetos.indexOfFirst { it.chave == uri.toString() }
        if (existente >= 0 && restaurar) { ativo = existente; noLote = false; return }
        escopo.launch {
            carregando = true
            try {
                val chave = uri.toString()
                var recuperado: Projeto? = null
                if (restaurar && Serial.existe(ctx, chave)) {
                    recuperado = Serial.restaurar(ctx, chave)
                    if (recuperado != null) Toast.makeText(ctx, "Edições anteriores restauradas.", Toast.LENGTH_SHORT).show()
                }
                val proj: Projeto = recuperado ?: Abertura.abrir(ctx, uri)
                val ext = proj.nome.substringAfterLast('.', "").lowercase()
                if (cfg.leituraHq && ext in setOf("cbz", "cbr", "zip", "rar", "epub")) proj.somenteLeitura = true
                abrirProjeto(proj)
                cfg.adicionarRecente(uri, proj.nome)
                recentes = cfg.recentes()
            } catch (e: Throwable) {
                erro = e.message ?: e.toString()
            } finally {
                carregando = false
            }
        }
    }

    fun fecharAba(i: Int) {
        if (i !in projetos.indices) return
        val p = projetos[i]
        Serial.salvar(ctx, p)
        p.fechar()
        projetos.removeAt(i)
        if (projetos.isEmpty()) {
            ativo = -1
        } else if (ativo > i) {
            ativo -= 1
        } else if (ativo == i) {
            ativo = minOf(i, projetos.size - 1)
        }
    }

    val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            abrirUri(uri)
        }
    }
    val seletorImagens = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            escopo.launch {
                carregando = true
                try { abrirProjeto(Abertura.deImagens(ctx, uris)) } catch (e: Throwable) { erro = e.message ?: e.toString() } finally { carregando = false }
            }
        }
    }

    LaunchedEffect(uriExterna.value) {
        uriExterna.value?.let { abrirUri(it); uriExterna.value = null }
    }

    BackHandler(enabled = ativo >= 0 || noLote) {
        if (noLote) noLote = false else ativo = -1
    }
    LaunchedEffect(ativo) { if (ativo < 0) telaCheia(false) }

    HshTema(tema, acento) {
        Surface(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                if (projetos.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { ativo = -1; noLote = false }) {
                            Text("⌂", color = if (ativo < 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        projetos.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.nome + (if (p.sujo) " •" else ""), maxLines = 1, fontSize = 13.sp, modifier = Modifier.widthIn(max = 130.dp).clickable { ativo = i; noLote = false }.padding(6.dp),
                                    color = if (i == ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = { fecharAba(i) }) { Text("✕", fontSize = 12.sp) }
                            }
                        }
                    }
                }
                val proj = projetos.getOrNull(ativo)
                when {
                    noLote -> LoteTela(cfg, onVoltar = { noLote = false })
                    proj == null -> InicioTela(
                        cfg = cfg, tema = tema, carregando = carregando, erro = erro, recentes = recentes,
                        onAbrir = { seletor.launch(arrayOf("*/*")) },
                        onImagens = { seletorImagens.launch(arrayOf("image/*")) },
                        onNovo = { abrirProjeto(Abertura.novoEmBranco()) },
                        onLote = { noLote = true },
                        onRecente = { abrirUri(Uri.parse(it.uri)) },
                        onRemover = { cfg.removerRecente(it); recentes = cfg.recentes() },
                        onTema = { tema = it; cfg.tema = it },
                        onAcento = { acento = it; cfg.acento = it },
                        onFecharErro = { erro = null }
                    )
                    else -> LeitorTela(
                        proj = proj, cfg = cfg, onVoltar = { ativo = -1 }, onTelaCheia = telaCheia,
                        onDescartar = {
                            val chave = proj.chave
                            if (chave != null) Serial.apagar(ctx, chave)
                            fecharAba(ativo)
                            if (chave != null) abrirUri(Uri.parse(chave), false)
                        }
                    )
                }
            }
        }
    }
}
