// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

object ImagensCache {
    private val cache = LruCache<String, Bitmap>(24)

    fun obter(caminho: String): Bitmap? {
        cache.get(caminho)?.let { return it }
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(caminho, o)
            var s = 1
            while (max(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            BitmapFactory.decodeFile(caminho, o2)?.also { cache.put(caminho, it) }
        } catch (e: Exception) {
            null
        }
    }
}

/** Desenha e mede as anotações. Tudo em coordenadas normalizadas (unidades da largura da página). */
object Desenho {
    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun comOpac(c: Int, opac: Float): Int =
        Color.argb((Color.alpha(c) * opac).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    private fun escurece(c: Int, f: Float): Int =
        Color.argb(255, (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    // ---------------- desenho ----------------
    fun desenharItens(c: Canvas, lista: List<Item>, w: Float) {
        for (x in lista) desenharItem(c, x, w)
    }

    fun desenharItem(c: Canvas, item: Item, w: Float) {
        when (item) {
            is Traco -> desenharTraco(c, item, w)
            is Forma -> desenharForma(c, item, w)
            is Realce -> {
                pincel.reset(); pincel.isAntiAlias = true
                pincel.style = Paint.Style.FILL; pincel.color = comOpac(item.cor, item.opac)
                c.drawRect(item.x * w, item.y * w, (item.x + item.w) * w, (item.y + item.h) * w, pincel)
            }
            is Nota -> desenharNota(c, item, w)
            is Texto -> {
                val l = layout(item.texto, item.fs * w, comOpac(item.cor, item.opac), false, max(10, ((1f - item.x) * w - 4).toInt()))
                c.save(); c.translate(item.x * w, item.y * w); l.draw(c); c.restore()
            }
            is Carimbo -> desenharCarimbo(c, item, w)
            is Imagem -> {
                val bmp = ImagensCache.obter(item.arquivo) ?: return
                pincel.reset(); pincel.isFilterBitmap = true; pincel.alpha = (item.opac * 255).toInt().coerceIn(0, 255)
                c.drawBitmap(bmp, null, RectF(item.x * w, item.y * w, (item.x + item.w) * w, (item.y + item.h) * w), pincel)
            }
        }
    }

    private fun traco(p: Path, pts: List<PontoN>, w: Float) {
        if (pts.isEmpty()) return
        p.moveTo(pts[0].x * w, pts[0].y * w)
        if (pts.size == 1) { p.lineTo(pts[0].x * w + 0.01f, pts[0].y * w); return }
        for (i in 1 until pts.size - 1) {
            val mx = (pts[i].x + pts[i + 1].x) / 2 * w
            val my = (pts[i].y + pts[i + 1].y) / 2 * w
            p.quadTo(pts[i].x * w, pts[i].y * w, mx, my)
        }
        p.lineTo(pts.last().x * w, pts.last().y * w)
    }

    private fun desenharTraco(c: Canvas, t: Traco, w: Float) {
        pincel.reset(); pincel.isAntiAlias = true
        pincel.style = Paint.Style.STROKE; pincel.strokeCap = Paint.Cap.ROUND; pincel.strokeJoin = Paint.Join.ROUND
        pincel.strokeWidth = max(1f, t.largura * w); pincel.color = comOpac(t.cor, t.opac)
        for (tr in t.tracos) {
            val p = Path(); traco(p, tr, w); c.drawPath(p, pincel)
        }
    }

    private fun cabeca(p: Path, ax: Float, ay: Float, bx: Float, by: Float, comp: Float) {
        val ang = atan2(by - ay, bx - ax)
        p.moveTo(bx - comp * cos(ang - 0.45f), by - comp * sin(ang - 0.45f))
        p.lineTo(bx, by)
        p.lineTo(bx - comp * cos(ang + 0.45f), by - comp * sin(ang + 0.45f))
    }

    fun pontosCurva(f: Forma): List<PontoN> {
        val a = f.pts[0]; val b = f.pts[1]; val cc = f.pts[2]
        return List(31) { i ->
            val t = i / 30f; val u = 1 - t
            PontoN(u * u * a.x + 2 * u * t * cc.x + t * t * b.x, u * u * a.y + 2 * u * t * cc.y + t * t * b.y)
        }
    }

    private fun desenharForma(c: Canvas, f: Forma, w: Float) {
        val larg = max(1f, f.largura * w)
        pincel.reset(); pincel.isAntiAlias = true; pincel.strokeCap = Paint.Cap.ROUND; pincel.strokeJoin = Paint.Join.ROUND
        pincel.strokeWidth = larg
        val tracoCor = comOpac(f.cor, f.opac)
        val preencheCor = comOpac(f.preench, f.opac)
        when (f.tipo) {
            "ret", "elipse", "circulo" -> {
                val r = RectF(min(f.pts[0].x, f.pts[1].x) * w, min(f.pts[0].y, f.pts[1].y) * w,
                    max(f.pts[0].x, f.pts[1].x) * w, max(f.pts[0].y, f.pts[1].y) * w)
                if (f.preench != 0) {
                    pincel.style = Paint.Style.FILL; pincel.color = preencheCor
                    if (f.tipo == "ret") c.drawRect(r, pincel) else c.drawOval(r, pincel)
                }
                pincel.style = Paint.Style.STROKE; pincel.color = tracoCor
                if (f.tipo == "ret") c.drawRect(r, pincel) else c.drawOval(r, pincel)
            }
            "linha", "seta" -> {
                pincel.style = Paint.Style.STROKE; pincel.color = tracoCor
                val a = f.pts[0]; val b = f.pts[1]
                c.drawLine(a.x * w, a.y * w, b.x * w, b.y * w, pincel)
                if (f.tipo == "seta") {
                    val p = Path(); cabeca(p, a.x * w, a.y * w, b.x * w, b.y * w, max(12f, larg * 4)); c.drawPath(p, pincel)
                }
            }
            "curva" -> {
                pincel.style = Paint.Style.STROKE; pincel.color = tracoCor
                val p = Path(); traco(p, pontosCurva(f), w); c.drawPath(p, pincel)
                val cc = f.pts[2]; val b = f.pts[1]
                val h = Path(); cabeca(h, cc.x * w, cc.y * w, b.x * w, b.y * w, max(12f, larg * 4)); c.drawPath(h, pincel)
            }
            else -> { // poligono, polilinha
                val p = Path()
                f.pts.forEachIndexed { i, q -> if (i == 0) p.moveTo(q.x * w, q.y * w) else p.lineTo(q.x * w, q.y * w) }
                if (f.tipo == "poligono") {
                    p.close()
                    if (f.preench != 0) { pincel.style = Paint.Style.FILL; pincel.color = preencheCor; c.drawPath(p, pincel) }
                }
                pincel.style = Paint.Style.STROKE; pincel.color = tracoCor; c.drawPath(p, pincel)
            }
        }
    }

    private fun layout(texto: String, tam: Float, cor: Int, negrito: Boolean, larguraPx: Int): StaticLayout {
        txt.reset(); txt.isAntiAlias = true
        txt.textSize = max(4f, tam); txt.color = cor
        txt.typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return StaticLayout.Builder.obtain(texto, 0, texto.length, TextPaint(txt), max(10, larguraPx))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    }

    private fun desenharNota(c: Canvas, n: Nota, w: Float) {
        val r = RectF(n.x * w, n.y * w, (n.x + n.w) * w, (n.y + n.h) * w)
        pincel.reset(); pincel.isAntiAlias = true
        pincel.style = Paint.Style.FILL; pincel.color = comOpac(n.cor, n.opac)
        c.drawRoundRect(r, w * 0.006f, w * 0.006f, pincel)
        pincel.style = Paint.Style.STROKE; pincel.strokeWidth = max(1f, w * 0.0015f); pincel.color = comOpac(escurece(n.cor, 0.7f), n.opac)
        c.drawRoundRect(r, w * 0.006f, w * 0.006f, pincel)
        val pad = w * 0.008f
        val l = layout(n.texto, n.fs * w, Color.argb(255, 34, 34, 34), false, ((n.w * w) - 2 * pad).toInt())
        c.save(); c.clipRect(r); c.translate(r.left + pad, r.top + pad); l.draw(c); c.restore()
    }

    /** Tamanho (normalizado) de um carimbo de texto. */
    fun medirCarimbo(texto: String, fs: Float, seta: Boolean): Pair<Float, Float> {
        val p = TextPaint().apply { textSize = 1000f * fs; typeface = Typeface.DEFAULT_BOLD }
        val pad = fs * 0.55f
        val h = fs * 1.2f + 2 * pad
        return Pair(p.measureText(texto) / 1000f + 2 * pad + (if (seta) h / 2 else 0f), h)
    }

    private fun desenharCarimbo(c: Canvas, k: Carimbo, w: Float) {
        val r = RectF(k.x * w, k.y * w, (k.x + k.w) * w, (k.y + k.h) * w)
        if (k.arquivo != null) {
            val bmp = ImagensCache.obter(k.arquivo) ?: return
            pincel.reset(); pincel.isFilterBitmap = true; pincel.alpha = (k.opac * 255).toInt().coerceIn(0, 255)
            c.drawBitmap(bmp, null, r, pincel)
            return
        }
        val cor = comOpac(k.cor, k.opac)
        pincel.reset(); pincel.isAntiAlias = true; pincel.style = Paint.Style.STROKE
        pincel.strokeWidth = max(2f, r.height() * 0.07f); pincel.color = cor; pincel.strokeJoin = Paint.Join.ROUND
        var esq = r.left
        if (k.seta) {
            val ponta = r.height() / 2
            val p = Path()
            p.moveTo(r.left, r.centerY()); p.lineTo(r.left + ponta, r.top); p.lineTo(r.right, r.top)
            p.lineTo(r.right, r.bottom); p.lineTo(r.left + ponta, r.bottom); p.close()
            c.drawPath(p, pincel)
            esq = r.left + ponta
        } else {
            c.drawRoundRect(r, r.height() * 0.12f, r.height() * 0.12f, pincel)
        }
        txt.reset(); txt.isAntiAlias = true; txt.typeface = Typeface.DEFAULT_BOLD; txt.color = cor
        txt.textSize = r.height() / 1.9f; txt.textAlign = Paint.Align.CENTER
        val base = r.centerY() - (txt.descent() + txt.ascent()) / 2
        c.drawText(k.texto, (esq + r.right) / 2, base, txt)
    }

    fun selecao(c: Canvas, item: Item, w: Float) {
        val b = caixa(item)
        pincel.reset(); pincel.isAntiAlias = true; pincel.style = Paint.Style.STROKE
        pincel.strokeWidth = max(2f, w * 0.003f); pincel.color = Color.argb(255, 59, 130, 246)
        pincel.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        c.drawRect(b.left * w, b.top * w, b.right * w, b.bottom * w, pincel)
        pincel.pathEffect = null
        val raio = max(9f, w * 0.014f)
        for (a in alcas(item)) {
            pincel.style = Paint.Style.FILL; pincel.color = Color.WHITE
            c.drawCircle(a.x * w, a.y * w, raio, pincel)
            pincel.style = Paint.Style.STROKE; pincel.color = Color.argb(255, 59, 130, 246)
            c.drawCircle(a.x * w, a.y * w, raio, pincel)
        }
    }

    // ---------------- geometria ----------------
    fun caixa(item: Item): RectF = when (item) {
        is Traco -> limites(item.tracos.flatten(), item.largura / 2)
        is Forma -> limites(if (item.tipo == "curva") pontosCurva(item) + item.pts else item.pts, item.largura / 2)
        is Nota -> RectF(item.x, item.y, item.x + item.w, item.y + item.h)
        is Realce -> RectF(item.x, item.y, item.x + item.w, item.y + item.h)
        is Carimbo -> RectF(item.x, item.y, item.x + item.w, item.y + item.h)
        is Imagem -> RectF(item.x, item.y, item.x + item.w, item.y + item.h)
        is Texto -> {
            val linhas = item.texto.split("\n")
            RectF(item.x, item.y, item.x + max(0.05f, (linhas.maxOf { it.length }) * item.fs * 0.55f), item.y + linhas.size * item.fs * 1.25f)
        }
    }

    private fun limites(pts: List<PontoN>, folga: Float): RectF {
        if (pts.isEmpty()) return RectF()
        return RectF(pts.minOf { it.x } - folga, pts.minOf { it.y } - folga, pts.maxOf { it.x } + folga, pts.maxOf { it.y } + folga)
    }

    private fun deslocar(l: List<PontoN>, dx: Float, dy: Float) = l.map { PontoN(it.x + dx, it.y + dy) }

    fun mover(item: Item, dx: Float, dy: Float): Item = when (item) {
        is Traco -> item.copy(tracos = item.tracos.map { deslocar(it, dx, dy) })
        is Forma -> item.copy(pts = deslocar(item.pts, dx, dy))
        is Nota -> item.copy(x = item.x + dx, y = item.y + dy)
        is Texto -> item.copy(x = item.x + dx, y = item.y + dy)
        is Carimbo -> item.copy(x = item.x + dx, y = item.y + dy)
        is Imagem -> item.copy(x = item.x + dx, y = item.y + dy)
        is Realce -> item.copy(x = item.x + dx, y = item.y + dy)
    }

    private fun cantos(r: RectF) = listOf(PontoN(r.left, r.top), PontoN(r.right, r.top), PontoN(r.right, r.bottom), PontoN(r.left, r.bottom))

    fun alcas(item: Item): List<PontoN> = when (item) {
        is Nota, is Realce, is Carimbo, is Imagem -> cantos(caixa(item))
        is Forma -> if (item.tipo in listOf("ret", "elipse", "circulo")) {
            cantos(RectF(min(item.pts[0].x, item.pts[1].x), min(item.pts[0].y, item.pts[1].y),
                max(item.pts[0].x, item.pts[1].x), max(item.pts[0].y, item.pts[1].y)))
        } else item.pts
        else -> emptyList()
    }

    fun comAlca(item: Item, i: Int, p: PontoN): Item {
        fun caixaNova(op: PontoN): RectF {
            val l = min(op.x, p.x); val t = min(op.y, p.y)
            return RectF(l, t, max(l + 0.02f, max(op.x, p.x)), max(t + 0.02f, max(op.y, p.y)))
        }
        return when (item) {
            is Nota -> { val r = caixaNova(alcas(item)[(i + 2) % 4]); item.copy(x = r.left, y = r.top, w = r.width(), h = r.height()) }
            is Realce -> { val r = caixaNova(alcas(item)[(i + 2) % 4]); item.copy(x = r.left, y = r.top, w = r.width(), h = r.height()) }
            is Carimbo -> { val r = caixaNova(alcas(item)[(i + 2) % 4]); item.copy(x = r.left, y = r.top, w = r.width(), h = r.height()) }
            is Imagem -> { val r = caixaNova(alcas(item)[(i + 2) % 4]); item.copy(x = r.left, y = r.top, w = r.width(), h = r.height()) }
            is Forma -> if (item.tipo in listOf("ret", "elipse", "circulo")) {
                val op = alcas(item)[(i + 2) % 4]
                var q = p
                if (item.tipo == "circulo") {
                    val lado = max(abs(p.x - op.x), abs(p.y - op.y))
                    q = PontoN(op.x + (if (p.x >= op.x) 1 else -1) * lado, op.y + (if (p.y >= op.y) 1 else -1) * lado)
                }
                item.copy(pts = listOf(op, q))
            } else item.copy(pts = item.pts.mapIndexed { k, o -> if (k == i) p else o })
            else -> item
        }
    }

    private fun dist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        if (dx == 0f && dy == 0f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun pertoDeLinha(pts: List<PontoN>, p: PontoN, tol: Float, fechar: Boolean): Boolean {
        val l = if (fechar && pts.size > 2) pts + pts[0] else pts
        for (i in 0 until l.size - 1) if (dist(p.x, p.y, l[i].x, l[i].y, l[i + 1].x, l[i + 1].y) <= tol) return true
        return false
    }

    fun acerta(item: Item, p: PontoN, tol: Float): Boolean {
        when (item) {
            is Traco -> return item.tracos.any { pertoDeLinha(it, p, tol + item.largura / 2, false) }
            is Forma -> {
                val folga = tol + item.largura / 2
                return when (item.tipo) {
                    "ret", "elipse", "circulo" -> {
                        val r = caixa(item.copy(largura = 0f))
                        val fora = RectF(r.left - folga, r.top - folga, r.right + folga, r.bottom + folga)
                        if (!fora.contains(p.x, p.y)) false
                        else if (item.preench != 0) true
                        else {
                            val dentro = RectF(r.left + folga, r.top + folga, r.right - folga, r.bottom - folga)
                            dentro.isEmpty || !dentro.contains(p.x, p.y)
                        }
                    }
                    "curva" -> pertoDeLinha(pontosCurva(item), p, folga, false)
                    "poligono" -> pertoDeLinha(item.pts, p, folga, true) || (item.preench != 0 && caixa(item).contains(p.x, p.y))
                    else -> pertoDeLinha(item.pts, p, folga, false)
                }
            }
            else -> {
                val r = caixa(item)
                return RectF(r.left - tol, r.top - tol, r.right + tol, r.bottom + tol).contains(p.x, p.y)
            }
        }
    }

    // ---------------- borracha precisa ----------------
    private fun densifica(pts: List<PontoN>, passo: Float): List<PontoN> {
        if (pts.size < 2) return pts
        val out = ArrayList<PontoN>()
        out.add(pts[0])
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val n = max(1, ceil(hypot(b.x - a.x, b.y - a.y) / passo).toInt())
            for (k in 1 until n) out.add(PontoN(a.x + (b.x - a.x) * k / n, a.y + (b.y - a.y) * k / n))
            out.add(b)
        }
        return out
    }

    /** Corta o traço onde a borracha passou. Devolve o mesmo objeto se nada foi tocado; null se sobrou nada. */
    fun apagarEm(t: Traco, p: PontoN, raio: Float): Traco? {
        var mexeu = false
        val novos = ArrayList<List<PontoN>>()
        for (tr in t.tracos) {
            val pts = densifica(tr, max(0.0004f, raio / 2.5f))
            val cortar = pts.map { hypot(it.x - p.x, it.y - p.y) <= raio }
            if (cortar.any { it }) mexeu = true
            var parte = ArrayList<PontoN>()
            for (i in pts.indices) {
                if (cortar[i]) {
                    if (parte.size > 1) novos.add(parte)
                    parte = ArrayList()
                } else parte.add(pts[i])
            }
            if (parte.size > 1) novos.add(parte)
        }
        if (!mexeu) return t
        return if (novos.isEmpty()) null else t.copy(tracos = novos)
    }

    // ---------------- lápis suave ----------------
    private fun rdp(pts: List<PontoN>, eps: Float): List<PontoN> {
        if (pts.size < 3) return pts
        val manter = BooleanArray(pts.size)
        manter[0] = true; manter[pts.size - 1] = true
        val pilha = ArrayList<IntArray>()
        pilha.add(intArrayOf(0, pts.size - 1))
        while (pilha.isNotEmpty()) {
            val (a, b) = pilha.removeAt(pilha.size - 1).let { Pair(it[0], it[1]) }
            var dmax = 0f; var idx = -1
            for (i in a + 1 until b) {
                val d = dist(pts[i].x, pts[i].y, pts[a].x, pts[a].y, pts[b].x, pts[b].y)
                if (d > dmax) { dmax = d; idx = i }
            }
            if (idx >= 0 && dmax > eps) {
                manter[idx] = true
                pilha.add(intArrayOf(a, idx)); pilha.add(intArrayOf(idx, b))
            }
        }
        return pts.filterIndexed { i, _ -> manter[i] }
    }

    fun suavizar(pts: List<PontoN>, eps: Float = 0.0012f): List<PontoN> {
        var l = rdp(pts, eps)
        repeat(2) {
            if (l.size >= 3) {
                val n = ArrayList<PontoN>()
                n.add(l[0])
                for (i in 0 until l.size - 1) {
                    val a = l[i]; val b = l[i + 1]
                    n.add(PontoN(0.75f * a.x + 0.25f * b.x, 0.75f * a.y + 0.25f * b.y))
                    n.add(PontoN(0.25f * a.x + 0.75f * b.x, 0.25f * a.y + 0.75f * b.y))
                }
                n.add(l.last())
                l = n
            }
        }
        return l
    }

    // ---------------- estilo ----------------
    fun comCor(i: Item, c: Int): Item = when (i) {
        is Traco -> i.copy(cor = c)
        is Forma -> i.copy(cor = c)
        is Nota -> i.copy(cor = c)
        is Texto -> i.copy(cor = c)
        is Carimbo -> if (i.arquivo == null) i.copy(cor = c) else i
        is Realce -> i.copy(cor = c)
        is Imagem -> i
    }

    fun comOpacidade(i: Item, o: Float): Item = when (i) {
        is Traco -> i.copy(opac = o)
        is Forma -> i.copy(opac = o)
        is Nota -> i.copy(opac = o)
        is Texto -> i.copy(opac = o)
        is Carimbo -> i.copy(opac = o)
        is Realce -> i.copy(opac = o)
        is Imagem -> i.copy(opac = o)
    }

    fun comLargura(i: Item, l: Float): Item = when (i) {
        is Traco -> i.copy(largura = l)
        is Forma -> i.copy(largura = l)
        is Texto -> i.copy(fs = l * 8f)
        is Nota -> i.copy(fs = l * 7f)
        else -> i
    }

    fun comPreenchimento(i: Item, c: Int): Item = if (i is Forma) i.copy(preench = c) else i

    fun textoDe(i: Item): String? = when (i) {
        is Nota -> i.texto
        is Texto -> i.texto
        else -> null
    }

    fun comTexto(i: Item, t: String): Item = when (i) {
        is Nota -> i.copy(texto = t)
        is Texto -> i.copy(texto = t)
        else -> i
    }
}
