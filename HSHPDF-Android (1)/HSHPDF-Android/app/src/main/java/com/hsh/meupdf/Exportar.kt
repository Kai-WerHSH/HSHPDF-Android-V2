// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/** qualidade: JPEG 30-100. ladoMax: maior lado em pixels (0 = sem limite). Controlam o tamanho do arquivo. */
class OpcoesExport(val qualidade: Int = 85, val ladoMax: Int = 2400)

/** Escritor de PDF próprio: cada página vira uma imagem JPEG (DCT) — o arquivo não incha. */
class PdfEscritor(private val saida: OutputStream, private val n: Int) {
    private var pos = 0L
    private val offs = LongArray(3 + 3 * n)

    private fun w(s: String) { val b = s.toByteArray(Charsets.ISO_8859_1); saida.write(b); pos += b.size }
    private fun w(b: ByteArray) { saida.write(b); pos += b.size }
    private fun f(v: Float) = String.format(Locale.US, "%.2f", v)

    init {
        w("%PDF-1.4\n")
        offs[1] = pos
        w("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
        val kids = (0 until n).joinToString(" ") { "${5 + 3 * it} 0 R" }
        offs[2] = pos
        w("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $n >>\nendobj\n")
    }

    fun pagina(k: Int, jpeg: ByteArray, pxW: Int, pxH: Int, ptW: Float, ptH: Float) {
        val img = 3 + 3 * k
        val cont = 4 + 3 * k
        val pg = 5 + 3 * k
        offs[img] = pos
        w("$img 0 obj\n<< /Type /XObject /Subtype /Image /Width $pxW /Height $pxH /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
        w(jpeg)
        w("\nendstream\nendobj\n")
        val c = "q ${f(ptW)} 0 0 ${f(ptH)} 0 0 cm /Im0 Do Q"
        offs[cont] = pos
        w("$cont 0 obj\n<< /Length ${c.length} >>\nstream\n$c\nendstream\nendobj\n")
        offs[pg] = pos
        w("$pg 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${f(ptW)} ${f(ptH)}] /Resources << /XObject << /Im0 $img 0 R >> >> /Contents $cont 0 R >>\nendobj\n")
    }

    fun fechar() {
        val xref = pos
        val total = 3 + 3 * n
        w("xref\n0 $total\n0000000000 65535 f \n")
        for (i in 1 until total) w(String.format(Locale.US, "%010d 00000 n \n", offs[i]))
        w("trailer\n<< /Size $total /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        saida.flush()
    }
}

object Exportador {
    /** Página final (com giro e anotações) na largura pedida. */
    suspend fun renderFinal(p: Pagina, larguraAlvo: Int): Bitmap {
        val w = larguraAlvo.coerceAtLeast(64)
        val h = (w * p.razao).toInt().coerceAtLeast(64)
        val saida = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(saida)
        c.drawColor(Color.WHITE)
        val fonte = p.fonte
        if (fonte != null) {
            val girada = p.rot % 180 != 0
            val larguraBase = if (girada) (w * p.largura / p.altura).toInt().coerceAtLeast(32) else w
            val base = fonte.renderizar(p.indice, larguraBase)
            if (p.rot == 0) {
                c.drawBitmap(base, null, Rect(0, 0, w, h), null)
            } else {
                val m = Matrix()
                m.postRotate(p.rot.toFloat())
                val r = Bitmap.createBitmap(base, 0, 0, base.width, base.height, m, true)
                c.drawBitmap(r, null, Rect(0, 0, w, h), null)
                r.recycle()
            }
        }
        Desenho.desenharItens(c, p.itens, w.toFloat())
        return saida
    }

    private fun larguraAlvo(p: Pagina, op: OpcoesExport): Int {
        val fator = if (p.fonte == null || p.fonte is FontePdf) 2f else 1f
        var w = p.larguraEf * fator
        val h = p.alturaEf * fator
        val lado = max(w, h)
        if (op.ladoMax > 0 && lado > op.ladoMax) w *= op.ladoMax / lado
        return w.toInt().coerceAtLeast(64)
    }

    private fun jpeg(b: Bitmap, q: Int): ByteArray {
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, q.coerceIn(30, 100), o)
        return o.toByteArray()
    }

    suspend fun paraPdf(proj: Projeto, indices: List<Int>, saida: OutputStream, op: OpcoesExport, prog: (Int, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val pags = indices.map { proj.estado.paginas[it] }
            val esc = PdfEscritor(BufferedOutputStream(saida, 1 shl 16), pags.size)
            pags.forEachIndexed { k, p ->
                ensureActive()
                val bmp = renderFinal(p, larguraAlvo(p, op))
                val dados = jpeg(bmp, op.qualidade)
                val ptW = if (p.fonte == null || p.fonte is FontePdf) p.larguraEf else 595f
                esc.pagina(k, dados, bmp.width, bmp.height, ptW, ptW * p.razao)
                bmp.recycle()
                prog(k + 1, pags.size)
            }
            esc.fechar()
        }

    /** CBZ (e ZIP de imagens): uma imagem JPEG por página, numeradas. */
    suspend fun paraCbz(proj: Projeto, indices: List<Int>, saida: OutputStream, op: OpcoesExport, prog: (Int, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val z = ZipOutputStream(BufferedOutputStream(saida, 1 shl 16))
            indices.forEachIndexed { k, i ->
                ensureActive()
                val p = proj.estado.paginas[i]
                val bmp = renderFinal(p, larguraAlvo(p, op))
                z.putNextEntry(ZipEntry(String.format(Locale.US, "%04d.jpg", k + 1)))
                z.write(jpeg(bmp, op.qualidade))
                z.closeEntry()
                bmp.recycle()
                prog(k + 1, indices.size)
            }
            z.finish()
            z.flush()
        }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** EPUB de layout fixo: cada página é uma imagem (fiel ao original). */
    suspend fun paraEpub(proj: Projeto, indices: List<Int>, titulo: String, saida: OutputStream, op: OpcoesExport, prog: (Int, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            val z = ZipOutputStream(BufferedOutputStream(saida, 1 shl 16))
            val mt = "application/epub+zip".toByteArray()
            val e0 = ZipEntry("mimetype")
            e0.method = ZipEntry.STORED
            e0.size = mt.size.toLong(); e0.compressedSize = mt.size.toLong()
            e0.crc = CRC32().also { it.update(mt) }.value
            z.putNextEntry(e0); z.write(mt); z.closeEntry()
            fun texto(nome: String, conteudo: String) {
                z.putNextEntry(ZipEntry(nome)); z.write(conteudo.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            texto("META-INF/container.xml", "<?xml version=\"1.0\"?>\n<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
            texto("OEBPS/style.css", "html,body{margin:0;padding:0}.pg{text-align:center}img{max-width:100%;height:auto}")
            val cab = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!DOCTYPE html>\n"
            val ns = "xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\""
            val manifest = StringBuilder("<item id=\"css\" href=\"style.css\" media-type=\"text/css\"/>")
            val spine = StringBuilder(); val navLi = StringBuilder(); val ncx = StringBuilder()
            indices.forEachIndexed { k, i ->
                ensureActive()
                val num = k + 1
                val p = proj.estado.paginas[i]
                val bmp = renderFinal(p, larguraAlvo(p, op))
                val pid = String.format(Locale.US, "p%04d", num)
                z.putNextEntry(ZipEntry("OEBPS/images/$pid.jpg")); z.write(jpeg(bmp, op.qualidade)); z.closeEntry()
                val capa = if (num == 1) " properties=\"cover-image\"" else ""
                manifest.append("<item id=\"img$pid\" href=\"images/$pid.jpg\" media-type=\"image/jpeg\"$capa/>")
                texto("OEBPS/$pid.xhtml", "$cab<html $ns><head><meta charset=\"utf-8\"/><title>Página $num</title><meta name=\"viewport\" content=\"width=${bmp.width}, height=${bmp.height}\"/><link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\"/></head><body><div class=\"pg\"><img src=\"images/$pid.jpg\" alt=\"Página $num\" width=\"${bmp.width}\" height=\"${bmp.height}\"/></div></body></html>")
                manifest.append("<item id=\"$pid\" href=\"$pid.xhtml\" media-type=\"application/xhtml+xml\"/>")
                spine.append("<itemref idref=\"$pid\"/>")
                navLi.append("<li><a href=\"$pid.xhtml\">Página $num</a></li>")
                ncx.append("<navPoint id=\"n$num\" playOrder=\"$num\"><navLabel><text>Página $num</text></navLabel><content src=\"$pid.xhtml\"/></navPoint>")
                bmp.recycle()
                prog(num, indices.size)
            }
            val uid = "urn:uuid:" + UUID.randomUUID()
            val agora = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also { it.timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())
            texto("OEBPS/nav.xhtml", "$cab<html $ns><head><meta charset=\"utf-8\"/><title>${esc(titulo)}</title></head><body><nav epub:type=\"toc\" id=\"toc\"><h1>${esc(titulo)}</h1><ol>$navLi</ol></nav></body></html>")
            texto("OEBPS/toc.ncx", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\"><head><meta name=\"dtb:uid\" content=\"$uid\"/></head><docTitle><text>${esc(titulo)}</text></docTitle><navMap>$ncx</navMap></ncx>")
            texto("OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"uid\">$uid</dc:identifier><dc:title>${esc(titulo)}</dc:title><dc:language>pt-BR</dc:language><meta property=\"dcterms:modified\">$agora</meta><meta property=\"rendition:layout\">pre-paginated</meta><meta property=\"rendition:spread\">auto</meta></metadata><manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>$manifest</manifest><spine toc=\"ncx\">$spine</spine></package>")
            z.finish()
            z.flush()
        }
}
