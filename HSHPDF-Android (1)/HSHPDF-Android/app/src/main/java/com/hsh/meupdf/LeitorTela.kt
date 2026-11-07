// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class DialogoTexto(val titulo: String, val inicial: String, val ok: (String) -> Unit)
data class ResBusca(val pagina: Int, val r: Resultado)

@Composable
fun LeitorTela(proj: Projeto, cfg: Config, onVoltar: () -> Unit, onTelaCheia: (Boolean) -> Unit, onDescartar: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val escopo = rememberCoroutineScope()
    val lista = rememberLazyListState()
    val ed = remember(proj) { EstadoEdicao() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var mostrarMini by remember { mutableStateOf(false) }
    var miniAberto by remember { mutableStateOf(cfg.miniAberto) }
    var menu by remember { mutableStateOf(false) }
    var modoPaginas by remember { mutableStateOf(false) }
    var buscaAberta by remember { mutableStateOf(false) }
    var textoBusca by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<ResBusca>>(emptyList()) }
    var atual by remember { mutableIntStateOf(-1) }
    var buscando by remember { mutableStateOf(false) }
    var irPara by remember { mutableStateOf(false) }
    var telaCheia by remember { mutableStateOf(cfg.telaCheia) }
    var dialogoTexto by remember { mutableStateOf<DialogoTexto?>(null) }
    var exportarSel by remember { mutableStateOf<List<Int>?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var capturaBmp by remember { mutableStateOf<Bitmap?>(null) }
    var carimbosAberto by remember { mutableStateOf(false) }
    var insercao by remember { mutableStateOf<Pair<Fonte, String>?>(null) }
    var pendenteImg by remember { mutableStateOf<Pair<Int, PontoN?>?>(null) }
    var dDescartar by remember { mutableStateOf(false) }
    val paginas = proj.estado.paginas
    val paginaAtual by remember { derivedStateOf { lista.firstVisibleItemIndex + 1 } }
    val porPagina = remember(resultados) { resultados.groupBy { it.pagina } }

    LaunchedEffect(proj.estado) { delay(1200); Serial.salvar(ctx, proj) }
    LaunchedEffect(telaCheia) { onTelaCheia(telaCheia) }
    DisposableEffect(Unit) { onDispose { onTelaCheia(false); Serial.salvar(ctx, proj) } }

    val escolherImagem = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val alvo = pendenteImg
        pendenteImg = null
        if (uri != null && alvo != null) {
            escopo.launch {
                try {
                    val arq = withContext(Dispatchers.IO) { copiarParaItens(ctx, uri) }
                    val bmp = ImagensCache.obter(arq.absolutePath) ?: throw IllegalStateException("Imagem inválida.")
                    val w = 0.4f
                    val h = w * bmp.height / bmp.width
                    val idx = alvo.first.coerceIn(0, proj.estado.paginas.size - 1)
                    val p = alvo.second ?: PontoN(0.5f, proj.estado.paginas[idx].razao / 2f)
                    val id = proj.novoId()
                    proj.adicionarItem(idx, Imagem(id, p.x - w / 2, p.y - h / 2, w, h, arq.absolutePath, 1f))
                    ed.selecionado = Pair(proj.estado.paginas[idx].id, id)
                } catch (e: Throwable) {
                    Toast.makeText(ctx, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val escolherArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            escopo.launch {
                try {
                    val (arq, nome) = Abertura.importar(ctx, uri)
                    insercao = Pair(Abertura.abrirArquivo(arq, nome), nome)
                } catch (e: Throwable) {
                    Toast.makeText(ctx, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val pedirTexto: (String, String, (String) -> Unit) -> Unit = { t, i, ok -> dialogoTexto = DialogoTexto(t, i, ok) }
    val pedirImagem: () -> Unit = {
        val idx = (lista.firstVisibleItemIndex).coerceIn(0, (paginas.size - 1).coerceAtLeast(0))
        pendenteImg = Pair(idx, null)
        escolherImagem.launch("image/*")
    }
    val pedirImagemEm: (Int, PontoN) -> Unit = { idx, p -> pendenteImg = Pair(idx, p); escolherImagem.launch("image/*") }

    fun buscar() {
        if (textoBusca.isBlank()) return
        escopo.launch {
            buscando = true
            val saida = ArrayList<ResBusca>()
            val cache = HashMap<Fonte, List<Resultado>>()
            for ((pi, pg) in proj.estado.paginas.withIndex()) {
                val f = pg.fonte ?: continue
                if (!f.permiteBusca) continue
                val rs = cache.getOrPut(f) { try { f.buscar(textoBusca.trim()) } catch (e: Exception) { emptyList() } }
                for (r in rs) if (r.pagina == pg.indice) saida.add(ResBusca(pi, r))
            }
            resultados = saida
            atual = if (saida.isEmpty()) -1 else 0
            buscando = false
            if (atual >= 0) lista.animateScrollToItem(saida[0].pagina)
        }
    }

    fun mover(d: Int) {
        if (resultados.isEmpty()) return
        atual = (atual + d + resultados.size) % resultados.size
        escopo.launch { lista.animateScrollToItem(resultados[atual].pagina) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ---------------- barra superior ----------------
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onVoltar) { Text("‹") }
                Text(proj.nome, maxLines = 1, fontSize = 14.sp, modifier = Modifier.widthIn(max = 150.dp), color = MaterialTheme.colorScheme.onSurface)
                if (!proj.somenteLeitura) {
                    BotaoFerr("Editar", ed.ativo) { ed.ativo = !ed.ativo; modoPaginas = false; if (!ed.ativo) ed.escolher(Ferr.MAO) }
                } else {
                    Text("🔒", modifier = Modifier.padding(horizontal = 6.dp))
                }
                BotaoFerr("Páginas", modoPaginas) { modoPaginas = !modoPaginas }
                BotaoFerr("Buscar", buscaAberta) { buscaAberta = !buscaAberta }
                Box {
                    TextButton(onClick = { menu = true }) { Text("⋮") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Exportar / converter...") }, onClick = { menu = false; exportarSel = emptyList() })
                        DropdownMenuItem(text = { Text((if (proj.somenteLeitura) "✓ " else "") + "Somente leitura") },
                            onClick = { menu = false; proj.somenteLeitura = !proj.somenteLeitura; if (proj.somenteLeitura) { ed.ativo = false; ed.escolher(Ferr.MAO) } })
                        DropdownMenuItem(text = { Text((if (telaCheia) "✓ " else "") + "Tela cheia (sem hora e bateria)") },
                            onClick = { menu = false; telaCheia = !telaCheia; cfg.telaCheia = telaCheia })
                        DropdownMenuItem(text = { Text("Captura de tela") }, onClick = { menu = false; modoPaginas = false; capturando = true })
                        DropdownMenuItem(text = { Text("Ir para a página...") }, onClick = { menu = false; irPara = true })
                        DropdownMenuItem(text = { Text(if (mostrarMini) "Ocultar miniaturas" else "Mostrar miniaturas") }, onClick = { menu = false; mostrarMini = !mostrarMini })
                        DropdownMenuItem(text = { Text("Zoom 100%") }, onClick = { menu = false; zoom = 1f })
                        DropdownMenuItem(text = { Text("Descartar edições...") }, onClick = { menu = false; dDescartar = true })
                    }
                }
            }
        }
        if (buscaAberta && !modoPaginas) {
            Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = textoBusca, onValueChange = { textoBusca = it }, singleLine = true,
                            modifier = Modifier.weight(1f), placeholder = { Text("Buscar no documento") })
                        TextButton(onClick = { buscar() }) { Text("OK") }
                        TextButton(onClick = { mover(-1) }) { Text("◀") }
                        TextButton(onClick = { mover(1) }) { Text("▶") }
                    }
                    val msg = when {
                        buscando -> "Buscando..."
                        resultados.isNotEmpty() -> "${atual + 1} de ${resultados.size}"
                        textoBusca.isNotBlank() && atual == -1 -> "Sem resultados (a busca só vale para PDF e EPUB)."
                        else -> ""
                    }
                    if (msg.isNotEmpty()) Text(msg, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (ed.ativo && !proj.somenteLeitura && !modoPaginas) {
            BarraEdicao(proj, ed, onImagem = pedirImagem, onCarimbos = { carimbosAberto = true })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (modoPaginas) {
                GradePaginas(
                    proj = proj,
                    onAbrir = { i -> modoPaginas = false; escopo.launch { lista.scrollToItem(i) } },
                    onFechar = { modoPaginas = false },
                    onInserirArquivo = { escolherArquivo.launch(arrayOf("*/*")) },
                    onExportar = { exportarSel = it }
                )
            } else {
                Row(Modifier.fillMaxSize()) {
                    if (mostrarMini) {
                        PainelMiniaturas(
                            proj = proj, aberto = miniAberto, atual = paginaAtual,
                            onAlternar = { miniAberto = !miniAberto; cfg.miniAberto = miniAberto },
                            onIr = { i -> escopo.launch { lista.animateScrollToItem(i) } },
                            modifier = Modifier.fillMaxHeight()
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val larguraBase = constraints.maxWidth
                            val dens = LocalDensity.current
                            val larguraPx = (larguraBase * zoom).toInt()
                            val rolagemH = rememberScrollState()
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            do {
                                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                                if (ev.changes.size >= 2) {
                                                    val z = ev.calculateZoom()
                                                    if (z != 1f) {
                                                        zoom = (zoom * z).coerceIn(1f, 5f)
                                                        ev.changes.forEach { it.consume() }
                                                    }
                                                }
                                            } while (ev.changes.any { it.pressed })
                                        }
                                    }
                                    .pointerInput(ed.ferr, ed.ativo) {
                                        if (!ed.ativo || ed.ferr == Ferr.MAO) detectTapGestures(onDoubleTap = { zoom = if (zoom > 1.4f) 1f else 2.5f })
                                    }
                                    .horizontalScroll(rolagemH)
                            ) {
                                LazyColumn(state = lista, modifier = Modifier.width(with(dens) { larguraPx.toDp() }).fillMaxHeight()) {
                                    items(count = paginas.size, key = { paginas[it].id }) { i ->
                                        PaginaView(
                                            proj = proj, idx = i, pagina = paginas[i], larguraPx = larguraPx, ed = ed,
                                            achados = porPagina[i]?.map { it.r } ?: emptyList(),
                                            atual = resultados.getOrNull(atual)?.takeIf { it.pagina == i }?.r,
                                            onTexto = pedirTexto, onImagemEm = { p -> pedirImagemEm(i, p) }
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            shape = MaterialTheme.shapes.large, tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        ) {
                            Text("$paginaAtual / ${paginas.size}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (capturando) {
                CapturaSelecao(
                    onCancelar = { capturando = false },
                    onPronto = { origem, tam ->
                        capturando = false
                        escopo.launch {
                            delay(200)
                            try {
                                val cheio = view.drawToBitmap(Bitmap.Config.ARGB_8888)
                                val x = origem.x.toInt().coerceIn(0, cheio.width - 2)
                                val y = origem.y.toInt().coerceIn(0, cheio.height - 2)
                                val w = tam.width.toInt().coerceIn(1, cheio.width - x)
                                val h = tam.height.toInt().coerceIn(1, cheio.height - y)
                                capturaBmp = Bitmap.createBitmap(cheio, x, y, w, h)
                            } catch (e: Throwable) {
                                Toast.makeText(ctx, "Não consegui capturar: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // ---------------- diálogos ----------------
    dialogoTexto?.let { d ->
        var t by remember(d) { mutableStateOf(d.inicial) }
        AlertDialog(
            onDismissRequest = { dialogoTexto = null }, title = { Text(d.titulo) },
            text = { OutlinedTextField(value = t, onValueChange = { t = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { d.ok(t); dialogoTexto = null }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { dialogoTexto = null }) { Text("Cancelar") } }
        )
    }
    if (irPara) {
        var entrada by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { irPara = false }, title = { Text("Ir para a página") },
            text = {
                OutlinedTextField(value = entrada, onValueChange = { entrada = it.filter { c -> c.isDigit() } }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("1 a ${paginas.size}") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = entrada.toIntOrNull()
                    irPara = false
                    if (n != null) escopo.launch { lista.animateScrollToItem((n - 1).coerceIn(0, paginas.size - 1)) }
                }) { Text("Ir") }
            },
            dismissButton = { TextButton(onClick = { irPara = false }) { Text("Cancelar") } }
        )
    }
    if (carimbosAberto) {
        DialogoCarimbos(cfg, onEscolher = { c -> ed.carimbo = c; ed.escolher(Ferr.CARIMBO); ed.carimbo = c; carimbosAberto = false }, onFechar = { carimbosAberto = false })
    }
    exportarSel?.let { sel -> DialogoExportar(proj, cfg, sel, onFechar = { exportarSel = null }) }
    capturaBmp?.let { b -> ResultadoCaptura(b, onFechar = { capturaBmp = null }) }
    insercao?.let { (fonte, nome) ->
        var faixa by remember(fonte) { mutableStateOf("") }
        var apos by remember(fonte) { mutableStateOf(paginaAtual.toString()) }
        AlertDialog(
            onDismissRequest = { fonte.fechar(); insercao = null }, title = { Text("Inserir páginas de $nome") },
            text = {
                Column {
                    Text("O arquivo tem ${fonte.paginas} página(s).", fontSize = 13.sp)
                    OutlinedTextField(value = faixa, onValueChange = { faixa = it }, singleLine = true,
                        label = { Text("Quais páginas (vazio = todas; ex.: 1-5,8)") })
                    OutlinedTextField(value = apos, onValueChange = { apos = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("Depois da página nº (0 = no início)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val indices = parseFaixa(faixa, fonte.paginas)
                    if (indices == null || indices.isEmpty()) {
                        Toast.makeText(ctx, "Intervalo inválido.", Toast.LENGTH_SHORT).show()
                    } else {
                        val novas = indices.map { i ->
                            val (w, h) = fonte.tamanho(i)
                            Pagina(proj.novoId(), fonte, i, 0, w, h, emptyList())
                        }
                        proj.fontes.add(fonte)
                        proj.inserir(novas, (apos.toIntOrNull() ?: paginas.size).coerceIn(0, paginas.size))
                        Som.concluir(ctx)
                        insercao = null
                    }
                }) { Text("Inserir") }
            },
            dismissButton = { TextButton(onClick = { fonte.fechar(); insercao = null }) { Text("Cancelar") } }
        )
    }
    if (dDescartar) {
        AlertDialog(
            onDismissRequest = { dDescartar = false }, title = { Text("Descartar edições") },
            text = { Text("Voltar ao arquivo original, apagando anotações e mudanças de páginas feitas no app?") },
            confirmButton = { TextButton(onClick = { dDescartar = false; onDescartar() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { dDescartar = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PaginaView(
    proj: Projeto, idx: Int, pagina: Pagina, larguraPx: Int, ed: EstadoEdicao,
    achados: List<Resultado>, atual: Resultado?,
    onTexto: (String, String, (String) -> Unit) -> Unit, onImagemEm: (PontoN) -> Unit
) {
    val dens = LocalDensity.current
    var base by remember(pagina.id, pagina.rot) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pagina.id, pagina.rot, larguraPx) {
        delay(80) // se o zoom ainda estiver mudando, esta tarefa é cancelada e só a última desenha
        val f = pagina.fonte
        if (f != null) {
            try {
                val girada = pagina.rot % 180 != 0
                val lb = if (girada) (larguraPx * pagina.largura / pagina.altura).toInt().coerceAtLeast(32) else larguraPx
                val b = f.renderizar(pagina.indice, lb)
                base = if (pagina.rot == 0) b else withContext(Dispatchers.Default) {
                    val m = Matrix()
                    m.postRotate(pagina.rot.toFloat())
                    Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { }
        }
    }
    val alturaPx = larguraPx * pagina.razao
    val sel = ed.selecionado
    val selItem = if (sel != null && sel.first == pagina.id) pagina.itens.firstOrNull { it.id == sel.second } else null
    val poly = if (ed.polyPagina == pagina.id) ed.polyPts.toList() else emptyList()
    val cursor = ed.cursor?.takeIf { it.first == pagina.id }?.second
    val editavel = ed.ativo && !proj.somenteLeitura

    Column {
        Box(
            Modifier
                .width(with(dens) { larguraPx.toDp() })
                .height(with(dens) { alturaPx.toDp() })
                .background(Color.White)
                .pointerInput(pagina.id, idx, ed.ferr, ed.formaTipo, editavel) {
                    if (editavel && ed.ferr != Ferr.MAO) tratarGestos(proj, idx, pagina.id, ed, onTexto, onImagemEm)
                }
        ) {
            base?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    Desenho.desenharItens(n, pagina.itens, w)
                    if (selItem != null && editavel) Desenho.selecao(n, selItem, w)
                }
                for (r in achados) {
                    drawRect(
                        color = if (r === atual) Color(0x99FF7A00) else Color(0x66FFD400),
                        topLeft = Offset(r.x0 * w, r.y0 * size.height),
                        size = Size((r.x1 - r.x0) * w, (r.y1 - r.y0) * size.height)
                    )
                }
                if (poly.isNotEmpty()) {
                    for (k in 0 until poly.size - 1) {
                        drawLine(Color(0xFF3B82F6), Offset(poly[k].x * w, poly[k].y * w), Offset(poly[k + 1].x * w, poly[k + 1].y * w), strokeWidth = 5f)
                    }
                    for (q in poly) drawCircle(Color(0xFF3B82F6), 9f, Offset(q.x * w, q.y * w))
                }
                if (cursor != null) {
                    drawCircle(Color(0x66FF0000), ed.cursorRaio, cursor)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun PainelMiniaturas(
    proj: Projeto, aberto: Boolean, atual: Int,
    onAlternar: () -> Unit, onIr: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val paginas = proj.estado.paginas
    val largura = if (aberto) 112.dp else 46.dp
    val listaMini: LazyListState = rememberLazyListState()
    LaunchedEffect(atual) { listaMini.animateScrollToItem((atual - 1).coerceAtLeast(0)) }
    Column(modifier.width(largura).background(MaterialTheme.colorScheme.surface)) {
        TextButton(onClick = onAlternar) { Text(if (aberto) "◀" else "▶") }
        LazyColumn(Modifier.fillMaxSize(), state = listaMini) {
            items(count = paginas.size, key = { paginas[it].id }) { i ->
                val destaque = (i + 1) == atual
                if (aberto) {
                    MiniaturaItem(paginas[i], i, destaque) { onIr(i) }
                } else {
                    Box(
                        Modifier.fillMaxWidth().clickable { onIr(i) }
                            .background(if (destaque) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${i + 1}", fontSize = 13.sp, color = if (destaque) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniaturaItem(p: Pagina, i: Int, destaque: Boolean, onClick: () -> Unit) {
    var bmp by remember(p) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p) {
        bmp = try { Exportador.renderFinal(p, 192) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height((88 * p.razao.coerceIn(0.5f, 2f)).dp).background(Color.White)
                .border(if (destaque) 3.dp else 1.dp, if (destaque) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        Text("${i + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
    }
}
