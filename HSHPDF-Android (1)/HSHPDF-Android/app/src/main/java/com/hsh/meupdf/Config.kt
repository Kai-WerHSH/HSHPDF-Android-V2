// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Carimbo guardado: de texto (arquivo = null) ou de imagem. */
data class CarimboSalvo(val texto: String, val cor: Int, val seta: Boolean, val arquivo: String?)

data class Recente(val uri: String, val nome: String, val quando: Long)

/** Preferências do app (tema, cor de destaque, arquivos recentes). */
class Config(ctx: Context) {
    private val sp = ctx.getSharedPreferences("hshpdf", Context.MODE_PRIVATE)

    var tema: String
        get() = sp.getString("tema", "sistema") ?: "sistema"
        set(v) { sp.edit().putString("tema", v).apply() }

    var acento: Int
        get() = sp.getInt("acento", 0xFFE84A4A.toInt())
        set(v) { sp.edit().putInt("acento", v).apply() }

    var miniAberto: Boolean
        get() = sp.getBoolean("mini_aberto", true)
        set(v) { sp.edit().putBoolean("mini_aberto", v).apply() }

    var som: Boolean
        get() = sp.getBoolean("som", true)
        set(v) { sp.edit().putBoolean("som", v).apply() }

    var telaCheia: Boolean
        get() = sp.getBoolean("tela_cheia", true)
        set(v) { sp.edit().putBoolean("tela_cheia", v).apply() }

    /** Qualidade JPEG (30-100) e maior lado em pixels (0 = sem limite) usados ao exportar. */
    var qualidade: Int
        get() = sp.getInt("qualidade", 85)
        set(v) { sp.edit().putInt("qualidade", v).apply() }

    var ladoMax: Int
        get() = sp.getInt("lado_max", 2400)
        set(v) { sp.edit().putInt("lado_max", v).apply() }

    var leituraHq: Boolean
        get() = sp.getBoolean("leitura_hq", false)
        set(v) { sp.edit().putBoolean("leitura_hq", v).apply() }

    fun carimbos(): List<CarimboSalvo> {
        return try {
            val arr = JSONArray(sp.getString("carimbos", "[]") ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                CarimboSalvo(o.getString("texto"), o.getInt("cor"), o.getBoolean("seta"), o.optString("arquivo", "").ifEmpty { null })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun gravarCarimbos(l: List<CarimboSalvo>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("texto", it.texto).put("cor", it.cor).put("seta", it.seta).put("arquivo", it.arquivo ?: "")) }
        sp.edit().putString("carimbos", arr.toString()).apply()
    }

    fun recentes(): List<Recente> {
        return try {
            val arr = JSONArray(sp.getString("recentes", "[]") ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Recente(o.getString("uri"), o.getString("nome"), o.optLong("quando"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun gravarRecentes(lista: List<Recente>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(JSONObject().put("uri", it.uri).put("nome", it.nome).put("quando", it.quando))
        }
        sp.edit().putString("recentes", arr.toString()).apply()
    }

    fun adicionarRecente(uri: Uri, nome: String) {
        val resto = recentes().filter { it.uri != uri.toString() }
        gravarRecentes((listOf(Recente(uri.toString(), nome, System.currentTimeMillis())) + resto).take(15))
    }

    fun removerRecente(r: Recente) {
        gravarRecentes(recentes().filter { it.uri != r.uri })
    }
}
