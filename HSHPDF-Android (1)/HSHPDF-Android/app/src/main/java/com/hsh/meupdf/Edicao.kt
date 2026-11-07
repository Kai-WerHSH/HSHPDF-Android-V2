// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

enum class Ferr { MAO, SELECIONAR, LAPIS, MARCA, FORMA, TEXTO, NOTA, CARIMBO, IMAGEM, BORRACHA }

val PALETA: List<Int> = listOf(0xFFFFDD33, 0xFF409EFF, 0xFFFF9E1A, 0xFFFF6B66, 0xFF33D91A, 0xFF000000).map { it.toInt() }
val CORES_NOTA: List<Int> = listOf(0xFFFFF3A0, 0xFFFFD1DC, 0xFFCFE8FF, 0xFFD4F5D0, 0xFFFFE0B3).map { it.toInt() }

val FORMAS: List<Pair<String, String>> = listOf(
    "Retângulo" to "ret", "Elipse" to "elipse", "Círculo" to "circulo", "Linha" to "linha",
    "Seta" to "seta", "Seta curva" to "curva", "Polígono" to "poligono", "Polilinha" to "polilinha"
)

/** Estado das ferramentas de edição (qual ferramenta, cor, espessura, item selecionado...). */
@Stable
class EstadoEdicao {
    var ativo by mutableStateOf(false)
    var ferr by mutableStateOf(Ferr.MAO)
    var formaTipo by mutableStateOf("ret")
    var cor by mutableIntStateOf(PALETA[3])
    var preench by mutableIntStateOf(0)
    var larg by mutableFloatStateOf(0.004f)
    var opac by mutableFloatStateOf(1f)
    var selecionado by mutableStateOf<Pair<Long, Long>?>(null)
    var carimbo by mutableStateOf<CarimboSalvo?>(null)
    var polyPagina by mutableLongStateOf(-1L)
    val polyPts = mutableStateListOf<PontoN>()
    var cursor by mutableStateOf<Pair<Long, Offset>?>(null)
    var cursorRaio by mutableFloatStateOf(0f)

    fun escolher(f: Ferr) {
        ferr = f
        polyPts.clear(); polyPagina = -1L
        if (f != Ferr.SELECIONAR) selecionado = null
        if (f == Ferr.MARCA) { cor = PALETA[0]; opac = 0.4f; larg = 0.02f }
        if (f == Ferr.LAPIS) { opac = 1f; larg = 0.004f }
    }
}

/** Tudo o que um gesto precisa saber. */
class GestoCtx(
    val proj: Projeto, val idx: Int, val pid: Long, val ed: EstadoEdicao, val w: Float, val down: PointerInputChange,
    val onTexto: (String, String, (String) -> Unit) -> Unit, val onImagemEm: (PontoN) -> Unit
) {
    fun pn(o: Offset) = PontoN(o.x / w, o.y / w)
}

/** Ponto de entrada dos gestos de uma página. */
suspend fun PointerInputScope.tratarGestos(
    proj: Projeto, idx: Int, pid: Long, ed: EstadoEdicao,
    onTexto: (String, String, (String) -> Unit) -> Unit, onImagemEm: (PontoN) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (idx >= proj.estado.paginas.size) return@awaitEachGesture
        val g = GestoCtx(proj, idx, pid, ed, size.width.toFloat(), down, onTexto, onImagemEm)
        when (ed.ferr) {
            Ferr.LAPIS -> livre(g, false)
            Ferr.MARCA -> livre(g, true)
            Ferr.BORRACHA -> borracha(g)
            Ferr.FORMA -> forma(g)
            Ferr.SELECIONAR -> selecionar(g)
            Ferr.TEXTO, Ferr.NOTA, Ferr.CARIMBO, Ferr.IMAGEM -> toque(g)
            Ferr.MAO -> { }
        }
    }
}

private suspend fun AwaitPointerEventScope.livre(g: GestoCtx, marca: Boolean) {
    val proj = g.proj; val ed = g.ed
    val pts = ArrayList<PontoN>()
    pts.add(g.pn(g.down.position))
    val id = proj.novoId()
    val larg = if (marca) max(ed.larg, 0.018f) else ed.larg
    val op = if (marca) minOf(ed.opac, 0.5f) else ed.opac
    fun item(p: List<PontoN>) = Traco(id, listOf(p), ed.cor, larg, op, marca)
    proj.comecarGesto()
    proj.adicionarItemGesto(g.idx, item(pts.toList()))
    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.size > 1) { proj.cancelarGesto(); return }
        val ch = ev.changes[0]
        if (!ch.pressed) { ch.consume(); break }
        pts.add(g.pn(ch.position))
        proj.trocarItemGesto(g.idx, item(pts.toList()))
        ch.consume()
    }
    proj.trocarItemGesto(g.idx, item(Desenho.suavizar(pts)))
    proj.terminarGesto()
}

private suspend fun AwaitPointerEventScope.borracha(g: GestoCtx) {
    val proj = g.proj; val ed = g.ed
    val raio = max(0.004f, ed.larg * 1.6f)
    fun apagar(p: PontoN) {
        val pg = proj.estado.paginas[g.idx]
        var mudou = false
        val novos = ArrayList<Item>()
        for (x in pg.itens) {
            when {
                x is Traco -> {
                    val r = Desenho.apagarEm(x, p, raio)
                    if (r !== x) mudou = true
                    if (r != null) novos.add(r)
                }
                Desenho.acerta(x, p, raio * 0.8f) -> mudou = true
                else -> novos.add(x)
            }
        }
        if (mudou) proj.definirItensGesto(g.idx, novos)
    }
    proj.comecarGesto()
    apagar(g.pn(g.down.position))
    ed.cursor = Pair(g.pid, g.down.position)
    ed.cursorRaio = raio * g.w
    while (true) {
        val ev = awaitPointerEvent()
        val ch = ev.changes[0]
        if (ev.changes.size > 1 || !ch.pressed) { ch.consume(); break }
        apagar(g.pn(ch.position))
        ed.cursor = Pair(g.pid, ch.position)
        ch.consume()
    }
    ed.cursor = null
    proj.terminarGesto()
}

private suspend fun AwaitPointerEventScope.forma(g: GestoCtx) {
    val proj = g.proj; val ed = g.ed
    val tipo = ed.formaTipo
    if (tipo == "poligono" || tipo == "polilinha") {
        val up = waitForUpOrCancellation() ?: return
        if (ed.polyPagina != g.pid) { ed.polyPts.clear(); ed.polyPagina = g.pid }
        ed.polyPts.add(g.pn(up.position))
        up.consume()
        return
    }
    val a = g.pn(g.down.position)
    val id = proj.novoId()
    fun fazer(b: PontoN): Forma {
        val pts = when (tipo) {
            "curva" -> listOf(a, b, PontoN((a.x + b.x) / 2 - (b.y - a.y) * 0.3f, (a.y + b.y) / 2 + (b.x - a.x) * 0.3f))
            "circulo" -> {
                val lado = max(abs(b.x - a.x), abs(b.y - a.y))
                listOf(a, PontoN(a.x + (if (b.x >= a.x) lado else -lado), a.y + (if (b.y >= a.y) lado else -lado)))
            }
            else -> listOf(a, b)
        }
        val pre = if (tipo in listOf("ret", "elipse", "circulo")) ed.preench else 0
        return Forma(id, tipo, pts, ed.cor, pre, ed.larg, ed.opac)
    }
    proj.comecarGesto()
    var fim = a
    var criou = false
    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.size > 1) { proj.cancelarGesto(); return }
        val ch = ev.changes[0]
        if (!ch.pressed) { ch.consume(); break }
        fim = g.pn(ch.position)
        if (hypot(fim.x - a.x, fim.y - a.y) > 0.01f) {
            if (!criou) { proj.adicionarItemGesto(g.idx, fazer(fim)); criou = true }
            else proj.trocarItemGesto(g.idx, fazer(fim))
        }
        ch.consume()
    }
    if (criou) {
        proj.terminarGesto()
        ed.selecionado = Pair(g.pid, id)  // a forma continua editável (alças) enquanto estiver selecionada
    } else proj.cancelarGesto()
}

private suspend fun AwaitPointerEventScope.toque(g: GestoCtx) {
    val up = waitForUpOrCancellation() ?: return
    up.consume()
    val proj = g.proj; val ed = g.ed
    val p = g.pn(g.down.position)
    when (ed.ferr) {
        Ferr.TEXTO -> g.onTexto("Texto", "") { t ->
            if (t.isNotBlank()) {
                val id = proj.novoId()
                proj.adicionarItem(g.idx, Texto(id, p.x, p.y, t, 0.035f, ed.cor, ed.opac))
                ed.selecionado = Pair(g.pid, id)
            }
        }
        Ferr.NOTA -> {
            val id = proj.novoId()
            val nota = Nota(id, p.x, p.y, 0.45f, 0.30f, "", 0.026f, CORES_NOTA[0], 1f)
            proj.adicionarItem(g.idx, nota)
            ed.selecionado = Pair(g.pid, id)
            g.onTexto("Nota", "") { t -> proj.trocarItem(g.idx, nota.copy(texto = t)) }
        }
        Ferr.CARIMBO -> {
            val c = ed.carimbo ?: return
            val id = proj.novoId()
            if (c.arquivo == null) {
                val (w, h) = Desenho.medirCarimbo(c.texto, 0.04f, c.seta)
                proj.adicionarItem(g.idx, Carimbo(id, p.x - w / 2, p.y - h / 2, w, h, c.texto, c.seta, null, c.cor, ed.opac))
            } else {
                val bmp = ImagensCache.obter(c.arquivo) ?: return
                val w = 0.32f
                val h = w * bmp.height / bmp.width
                proj.adicionarItem(g.idx, Carimbo(id, p.x - w / 2, p.y - h / 2, w, h, "", false, c.arquivo, 0, ed.opac))
            }
            ed.selecionado = Pair(g.pid, id)
        }
        Ferr.IMAGEM -> g.onImagemEm(p)
        else -> { }
    }
}

private suspend fun AwaitPointerEventScope.selecionar(g: GestoCtx) {
    val proj = g.proj; val ed = g.ed
    val pg = proj.estado.paginas[g.idx]
    val p = g.pn(g.down.position)
    val tol = max(0.015f, 22f / g.w)
    val selAtual = ed.selecionado?.takeIf { it.first == g.pid }?.let { s -> pg.itens.firstOrNull { it.id == s.second } }
    if (selAtual != null) {
        val k = Desenho.alcas(selAtual).indexOfFirst { hypot(it.x - p.x, it.y - p.y) <= tol * 1.4f }
        if (k >= 0) {
            proj.comecarGesto()
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.size > 1) { proj.cancelarGesto(); return }
                val ch = ev.changes[0]
                if (!ch.pressed) { ch.consume(); break }
                val atual = proj.estado.paginas[g.idx].itens.firstOrNull { it.id == selAtual.id } ?: break
                proj.trocarItemGesto(g.idx, Desenho.comAlca(atual, k, g.pn(ch.position)))
                ch.consume()
            }
            proj.terminarGesto()
            return
        }
    }
    val alvo = pg.itens.lastOrNull { Desenho.acerta(it, p, tol) }
    if (alvo == null) {
        val up = waitForUpOrCancellation()
        if (up != null) ed.selecionado = null
        return
    }
    val jaEstava = selAtual?.id == alvo.id
    ed.selecionado = Pair(g.pid, alvo.id)
    proj.comecarGesto()
    var ult = p
    var moveu = false
    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.size > 1) { proj.cancelarGesto(); return }
        val ch = ev.changes[0]
        if (!ch.pressed) { ch.consume(); break }
        val q = g.pn(ch.position)
        if (!moveu && hypot(q.x - p.x, q.y - p.y) < tol * 0.6f) { ch.consume(); continue }
        moveu = true
        val atual = proj.estado.paginas[g.idx].itens.firstOrNull { it.id == alvo.id } ?: break
        proj.trocarItemGesto(g.idx, Desenho.mover(atual, q.x - ult.x, q.y - ult.y))
        ult = q
        ch.consume()
    }
    proj.terminarGesto()
    if (!moveu && jaEstava) {
        val atual = proj.estado.paginas[g.idx].itens.firstOrNull { it.id == alvo.id }
        val t = atual?.let { Desenho.textoDe(it) }
        if (atual != null && t != null) g.onTexto("Editar texto", t) { novo -> proj.trocarItem(g.idx, Desenho.comTexto(atual, novo)) }
    }
}

// ======================= interface das ferramentas =======================

/** Item selecionado (com o índice da página). */
fun acharSelecionado(proj: Projeto, ed: EstadoEdicao): Triple<Int, Pagina, Item>? {
    val s = ed.selecionado ?: return null
    val idx = proj.estado.paginas.indexOfFirst { it.id == s.first }
    if (idx < 0) return null
    val pg = proj.estado.paginas[idx]
    val item = pg.itens.firstOrNull { it.id == s.second } ?: return null
    return Triple(idx, pg, item)
}

@Composable
fun BotaoFerr(rotulo: String, ativo: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 3.dp).clickable(enabled = habilitado) { onClick() }
    ) {
        Text(
            rotulo, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = if (ativo) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (habilitado) 1f else 0.4f)
        )
    }
}

@Composable
fun BarraEdicao(proj: Projeto, ed: EstadoEdicao, onImagem: () -> Unit, onCarimbos: () -> Unit) {
    var menuFormas by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BotaoFerr("Selecionar", ed.ferr == Ferr.SELECIONAR) { ed.escolher(Ferr.SELECIONAR) }
                BotaoFerr("Lápis", ed.ferr == Ferr.LAPIS) { ed.escolher(Ferr.LAPIS) }
                BotaoFerr("Marca-texto", ed.ferr == Ferr.MARCA) { ed.escolher(Ferr.MARCA) }
                Box {
                    BotaoFerr("Formas ▾", ed.ferr == Ferr.FORMA) { menuFormas = true }
                    DropdownMenu(expanded = menuFormas, onDismissRequest = { menuFormas = false }) {
                        for ((rot, tipo) in FORMAS) {
                            DropdownMenuItem(text = { Text(rot) }, onClick = {
                                menuFormas = false
                                ed.escolher(Ferr.FORMA); ed.formaTipo = tipo
                            })
                        }
                    }
                }
                BotaoFerr("Texto", ed.ferr == Ferr.TEXTO) { ed.escolher(Ferr.TEXTO) }
                BotaoFerr("Nota", ed.ferr == Ferr.NOTA) { ed.escolher(Ferr.NOTA) }
                BotaoFerr("Carimbo ▾", ed.ferr == Ferr.CARIMBO) { onCarimbos() }
                BotaoFerr("Imagem", ed.ferr == Ferr.IMAGEM) { ed.escolher(Ferr.IMAGEM); onImagem() }
                BotaoFerr("Borracha", ed.ferr == Ferr.BORRACHA) { ed.escolher(Ferr.BORRACHA) }
                BotaoFerr("↶", false, proj.podeDesfazer) { proj.desfazer(); ed.selecionado = null }
                BotaoFerr("↷", false, proj.podeRefazer) { proj.refazer(); ed.selecionado = null }
            }
            PropsEdicao(proj, ed)
        }
    }
}

@Composable
fun PropsEdicao(proj: Projeto, ed: EstadoEdicao) {
    val sel = acharSelecionado(proj, ed)
    val item = sel?.third
    var emSlider by remember { mutableStateOf(false) }

    fun aplicar(f: (Item) -> Item, gesto: Boolean) {
        if (sel == null) return
        val novo = f(sel.third)
        if (gesto) proj.trocarItemGesto(sel.first, novo) else proj.trocarItem(sel.first, novo)
    }
    val corAtual = if (item != null) item.cor else ed.cor
    val opacAtual = item?.opac ?: ed.opac
    val largAtual = when (item) { is Traco -> item.largura; is Forma -> item.largura; else -> ed.larg }
    val ehForma = (item is Forma) || (item == null && ed.ferr == Ferr.FORMA)

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Text("Cor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            for (c in PALETA) {
                Box(Modifier.padding(3.dp).size(28.dp).background(Color(c), RoundedCornerShape(6.dp))
                    .clickable { ed.cor = c; aplicar({ Desenho.comCor(it, c) }, false) })
            }
            if (item is Nota) {
                Spacer(Modifier.width(8.dp)); Text("Papel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (c in CORES_NOTA) {
                    Box(Modifier.padding(3.dp).size(28.dp).background(Color(c), RoundedCornerShape(6.dp)).clickable { aplicar({ Desenho.comCor(it, c) }, false) })
                }
            }
            if (ehForma) {
                Spacer(Modifier.width(10.dp)); Text("Preencher", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.padding(3.dp).size(28.dp).background(Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { ed.preench = 0; aplicar({ Desenho.comPreenchimento(it, 0) }, false) }, contentAlignment = Alignment.Center) {
                    Text("∅", color = MaterialTheme.colorScheme.onSurface)
                }
                for (c in PALETA) {
                    Box(Modifier.padding(3.dp).size(28.dp).background(Color(c), RoundedCornerShape(6.dp))
                        .clickable { ed.preench = c; aplicar({ Desenho.comPreenchimento(it, c) }, false) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Opac.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = opacAtual, valueRange = 0.1f..1f, modifier = Modifier.weight(1f),
                onValueChange = { v ->
                    ed.opac = v
                    if (sel != null) { if (!emSlider) { proj.comecarGesto(); emSlider = true }; aplicar({ Desenho.comOpacidade(it, v) }, true) }
                },
                onValueChangeFinished = { if (emSlider) { proj.terminarGesto(); emSlider = false } }
            )
            Text("Espess.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = largAtual, valueRange = 0.001f..0.03f, modifier = Modifier.weight(1f),
                onValueChange = { v ->
                    ed.larg = v
                    if (sel != null) { if (!emSlider) { proj.comecarGesto(); emSlider = true }; aplicar({ Desenho.comLargura(it, v) }, true) }
                },
                onValueChangeFinished = { if (emSlider) { proj.terminarGesto(); emSlider = false } }
            )
        }
        if (item != null || ed.polyPts.size >= 2) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                if (ed.polyPts.size >= 2) {
                    TextButton(onClick = {
                        val idx = proj.estado.paginas.indexOfFirst { it.id == ed.polyPagina }
                        if (idx >= 0) {
                            val id = proj.novoId()
                            val pre = if (ed.formaTipo == "poligono") ed.preench else 0
                            proj.adicionarItem(idx, Forma(id, ed.formaTipo, ed.polyPts.toList(), ed.cor, pre, ed.larg, ed.opac))
                            ed.selecionado = Pair(ed.polyPagina, id)
                        }
                        ed.polyPts.clear(); ed.polyPagina = -1L
                    }) { Text("Concluir ${if (ed.formaTipo == "poligono") "polígono" else "polilinha"}") }
                    TextButton(onClick = { ed.polyPts.clear(); ed.polyPagina = -1L }) { Text("Cancelar") }
                }
                if (item != null && sel != null) {
                    TextButton(onClick = {
                        val copia = Desenho.mover(item, 0.03f, 0.03f)
                        val novo = when (copia) {
                            is Traco -> copia.copy(id = proj.novoId()); is Forma -> copia.copy(id = proj.novoId())
                            is Nota -> copia.copy(id = proj.novoId()); is Texto -> copia.copy(id = proj.novoId())
                            is Carimbo -> copia.copy(id = proj.novoId()); is Imagem -> copia.copy(id = proj.novoId())
                            is Realce -> copia.copy(id = proj.novoId())
                        }
                        proj.adicionarItem(sel.first, novo)
                        ed.selecionado = Pair(sel.second.id, novo.id)
                    }) { Text("Duplicar") }
                    TextButton(onClick = { proj.removerItem(sel.first, item.id); ed.selecionado = null }) { Text("Excluir item") }
                }
            }
        }
    }
}
