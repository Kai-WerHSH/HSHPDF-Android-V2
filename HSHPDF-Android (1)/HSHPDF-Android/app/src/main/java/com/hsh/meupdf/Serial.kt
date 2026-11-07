// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Salvamento automático das edições (para não perder nada se o Android fechar o app). */
object Serial {
    private fun arquivo(ctx: Context, chave: String): File {
        val dir = File(ctx.filesDir, "projetos").apply { mkdirs() }
        return File(dir, "p" + chave.hashCode().toUInt().toString(16) + ".json")
    }

    private fun pts(l: List<PontoN>): JSONArray = JSONArray().also { a -> l.forEach { a.put(JSONArray().put(it.x.toDouble()).put(it.y.toDouble())) } }
    private fun lerPts(a: JSONArray): List<PontoN> = List(a.length()) { PontoN(a.getJSONArray(it).getDouble(0).toFloat(), a.getJSONArray(it).getDouble(1).toFloat()) }

    fun itemParaJson(i: Item): JSONObject {
        val o = JSONObject().put("id", i.id).put("cor", i.cor).put("opac", i.opac.toDouble())
        when (i) {
            is Traco -> o.put("t", "traco").put("larg", i.largura.toDouble()).put("marca", i.marca)
                .put("tracos", JSONArray().also { a -> i.tracos.forEach { a.put(pts(it)) } })
            is Forma -> o.put("t", "forma").put("tipo", i.tipo).put("pts", pts(i.pts)).put("preench", i.preench).put("larg", i.largura.toDouble())
            is Nota -> o.put("t", "nota").put("x", i.x.toDouble()).put("y", i.y.toDouble()).put("w", i.w.toDouble()).put("h", i.h.toDouble())
                .put("texto", i.texto).put("fs", i.fs.toDouble())
            is Texto -> o.put("t", "texto").put("x", i.x.toDouble()).put("y", i.y.toDouble()).put("texto", i.texto).put("fs", i.fs.toDouble())
            is Carimbo -> o.put("t", "carimbo").put("x", i.x.toDouble()).put("y", i.y.toDouble()).put("w", i.w.toDouble()).put("h", i.h.toDouble())
                .put("texto", i.texto).put("seta", i.seta).put("arq", i.arquivo ?: "")
            is Imagem -> o.put("t", "imagem").put("x", i.x.toDouble()).put("y", i.y.toDouble()).put("w", i.w.toDouble()).put("h", i.h.toDouble()).put("arq", i.arquivo)
            is Realce -> o.put("t", "realce").put("x", i.x.toDouble()).put("y", i.y.toDouble()).put("w", i.w.toDouble()).put("h", i.h.toDouble())
        }
        return o
    }

    fun itemDeJson(o: JSONObject): Item {
        val id = o.getLong("id"); val cor = o.getInt("cor"); val op = o.getDouble("opac").toFloat()
        fun f(k: String) = o.getDouble(k).toFloat()
        return when (o.getString("t")) {
            "traco" -> Traco(id, o.getJSONArray("tracos").let { a -> List(a.length()) { lerPts(a.getJSONArray(it)) } }, cor, f("larg"), op, o.optBoolean("marca"))
            "forma" -> Forma(id, o.getString("tipo"), lerPts(o.getJSONArray("pts")), cor, o.getInt("preench"), f("larg"), op)
            "nota" -> Nota(id, f("x"), f("y"), f("w"), f("h"), o.getString("texto"), f("fs"), cor, op)
            "texto" -> Texto(id, f("x"), f("y"), o.getString("texto"), f("fs"), cor, op)
            "carimbo" -> Carimbo(id, f("x"), f("y"), f("w"), f("h"), o.getString("texto"), o.getBoolean("seta"), o.getString("arq").ifEmpty { null }, cor, op)
            "imagem" -> Imagem(id, f("x"), f("y"), f("w"), f("h"), o.getString("arq"), op, cor)
            else -> Realce(id, f("x"), f("y"), f("w"), f("h"), cor, op)
        }
    }

    fun salvar(ctx: Context, p: Projeto) {
        val chave = p.chave ?: return
        try {
            val o = JSONObject().put("nome", p.nome)
            o.put("fontes", JSONArray().also { a -> p.fontes.forEach { a.put(it.descricao) } })
            o.put("paginas", JSONArray().also { a ->
                p.estado.paginas.forEach { pg ->
                    a.put(JSONObject().put("id", pg.id).put("fonte", p.fontes.indexOf(pg.fonte)).put("indice", pg.indice)
                        .put("rot", pg.rot).put("w", pg.largura.toDouble()).put("h", pg.altura.toDouble())
                        .put("itens", JSONArray().also { it2 -> pg.itens.forEach { x -> it2.put(itemParaJson(x)) } }))
                }
            })
            arquivo(ctx, chave).writeText(o.toString())
        } catch (e: Exception) { }
    }

    fun existe(ctx: Context, chave: String) = arquivo(ctx, chave).exists()
    fun apagar(ctx: Context, chave: String) { arquivo(ctx, chave).delete() }

    /** Reconstrói o projeto (páginas, ordem, giros e anotações) a partir do salvamento automático. */
    suspend fun restaurar(ctx: Context, chave: String): Projeto? {
        val f = arquivo(ctx, chave)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val p = Projeto(o.getString("nome"), chave)
            val fs = o.getJSONArray("fontes")
            for (i in 0 until fs.length()) p.fontes.add(Abertura.reabrir(fs.getString(i)))
            val ps = o.getJSONArray("paginas")
            val paginas = ArrayList<Pagina>()
            for (i in 0 until ps.length()) {
                val q = ps.getJSONObject(i)
                val fi = q.getInt("fonte")
                val it2 = q.getJSONArray("itens")
                paginas.add(Pagina(q.getLong("id"), if (fi >= 0) p.fontes[fi] else null, q.getInt("indice"), q.getInt("rot"),
                    q.getDouble("w").toFloat(), q.getDouble("h").toFloat(), List(it2.length()) { itemDeJson(it2.getJSONObject(it)) }))
            }
            p.estado = Estado(paginas)
            p
        } catch (e: Exception) {
            null
        }
    }
}
