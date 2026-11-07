// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Ponto normalizado: x e y em unidades da LARGURA da página (assim tudo escala junto com o zoom). */
data class PontoN(val x: Float, val y: Float)

sealed class Item {
    abstract val id: Long
    abstract val cor: Int
    abstract val opac: Float
}

data class Traco(
    override val id: Long, val tracos: List<List<PontoN>>, override val cor: Int,
    val largura: Float, override val opac: Float, val marca: Boolean = false
) : Item()

/** tipo: ret, elipse, circulo, linha, seta, curva, poligono, polilinha. preench = 0 significa sem preenchimento. */
data class Forma(
    override val id: Long, val tipo: String, val pts: List<PontoN>, override val cor: Int,
    val preench: Int, val largura: Float, override val opac: Float
) : Item()

data class Nota(
    override val id: Long, val x: Float, val y: Float, val w: Float, val h: Float,
    val texto: String, val fs: Float, override val cor: Int, override val opac: Float
) : Item()

data class Texto(
    override val id: Long, val x: Float, val y: Float, val texto: String, val fs: Float,
    override val cor: Int, override val opac: Float
) : Item()

/** Carimbo de texto (arquivo = null) ou de imagem (arquivo = caminho da imagem). */
data class Carimbo(
    override val id: Long, val x: Float, val y: Float, val w: Float, val h: Float, val texto: String,
    val seta: Boolean, val arquivo: String?, override val cor: Int, override val opac: Float
) : Item()

data class Imagem(
    override val id: Long, val x: Float, val y: Float, val w: Float, val h: Float, val arquivo: String,
    override val opac: Float, override val cor: Int = 0
) : Item()

data class Realce(
    override val id: Long, val x: Float, val y: Float, val w: Float, val h: Float,
    override val cor: Int, override val opac: Float
) : Item()

/** Uma página do projeto: de uma fonte (PDF, CBZ...) ou em branco (fonte = null), com giro e anotações. */
data class Pagina(
    val id: Long, val fonte: Fonte?, val indice: Int, val rot: Int,
    val largura: Float, val altura: Float, val itens: List<Item>
) {
    private val girada: Boolean get() = rot % 180 != 0
    val larguraEf: Float get() = if (girada) altura else largura
    val alturaEf: Float get() = if (girada) largura else altura
    /** altura / largura da página já girada. */
    val razao: Float get() = alturaEf / larguraEf
}

data class Estado(val paginas: List<Pagina>)

/** Documento em edição. Todo o histórico (desfazer/refazer) é feito por "fotos" do estado: simples e à prova de erro. */
class Projeto(val nome: String, val chave: String?) {
    var estado by mutableStateOf(Estado(emptyList()))
    var somenteLeitura by mutableStateOf(false)
    var sujo by mutableStateOf(false)
    val fontes = ArrayList<Fonte>()
    private var contador = System.nanoTime()
    private val pilhaDesfazer = ArrayList<Estado>()
    private val pilhaRefazer = ArrayList<Estado>()
    private var base: Estado? = null
    var versaoHistorico by mutableStateOf(0)
        private set

    fun novoId(): Long = ++contador
    val podeDesfazer: Boolean get() = versaoHistorico >= 0 && pilhaDesfazer.isNotEmpty()
    val podeRefazer: Boolean get() = versaoHistorico >= 0 && pilhaRefazer.isNotEmpty()

    fun confirmar(novo: Estado) {
        pilhaDesfazer.add(estado)
        if (pilhaDesfazer.size > 120) pilhaDesfazer.removeAt(0)
        pilhaRefazer.clear()
        estado = novo
        sujo = true
        versaoHistorico++
    }

    // gesto em andamento (arrastar): muda o estado sem criar vários passos de desfazer
    fun comecarGesto() { base = estado }
    fun aplicarGesto(novo: Estado) { estado = novo }
    fun cancelarGesto() {
        base?.let { estado = it }
        base = null
    }

    fun terminarGesto() {
        val b = base
        base = null
        if (b != null && b !== estado) {
            pilhaDesfazer.add(b)
            if (pilhaDesfazer.size > 120) pilhaDesfazer.removeAt(0)
            pilhaRefazer.clear()
            sujo = true
            versaoHistorico++
        }
    }

    fun desfazer() {
        if (pilhaDesfazer.isEmpty()) return
        pilhaRefazer.add(estado)
        estado = pilhaDesfazer.removeAt(pilhaDesfazer.size - 1)
        versaoHistorico++
    }

    fun refazer() {
        if (pilhaRefazer.isEmpty()) return
        pilhaDesfazer.add(estado)
        estado = pilhaRefazer.removeAt(pilhaRefazer.size - 1)
        versaoHistorico++
    }

    // ---------- itens ----------
    private fun mapearPagina(idx: Int, f: (Pagina) -> Pagina): Estado =
        Estado(estado.paginas.mapIndexed { i, p -> if (i == idx) f(p) else p })

    fun adicionarItem(idx: Int, item: Item) = confirmar(mapearPagina(idx) { it.copy(itens = it.itens + item) })
    fun trocarItem(idx: Int, item: Item) =
        confirmar(mapearPagina(idx) { p -> p.copy(itens = p.itens.map { if (it.id == item.id) item else it }) })
    fun removerItem(idx: Int, id: Long) = confirmar(mapearPagina(idx) { p -> p.copy(itens = p.itens.filter { it.id != id }) })
    fun trocarItemGesto(idx: Int, item: Item) =
        aplicarGesto(mapearPagina(idx) { p -> p.copy(itens = p.itens.map { if (it.id == item.id) item else it }) })
    fun adicionarItemGesto(idx: Int, item: Item) = aplicarGesto(mapearPagina(idx) { it.copy(itens = it.itens + item) })
    fun definirItensGesto(idx: Int, itens: List<Item>) = aplicarGesto(mapearPagina(idx) { it.copy(itens = itens) })

    // ---------- páginas ----------
    fun excluir(indices: Set<Int>) {
        if (indices.size >= estado.paginas.size) return
        confirmar(Estado(estado.paginas.filterIndexed { i, _ -> i !in indices }))
    }

    fun girar(indices: Set<Int>, graus: Int) =
        confirmar(Estado(estado.paginas.mapIndexed { i, p -> if (i in indices) p.copy(rot = ((p.rot + graus) % 360 + 360) % 360) else p }))

    fun duplicar(indices: Set<Int>) {
        val novas = ArrayList<Pagina>()
        estado.paginas.forEachIndexed { i, p ->
            novas.add(p)
            if (i in indices) novas.add(p.copy(id = novoId()))
        }
        confirmar(Estado(novas))
    }

    /** Move as páginas escolhidas para antes da página `destino` (0..n; n = fim), mantendo a ordem entre elas. */
    fun mover(indices: Set<Int>, destino: Int) {
        val ps = estado.paginas
        val mov = ps.filterIndexed { i, _ -> i in indices }
        val resto = ps.filterIndexed { i, _ -> i !in indices }
        val k = (0 until destino.coerceIn(0, ps.size)).count { it !in indices }
        confirmar(Estado(resto.take(k) + mov + resto.drop(k)))
    }

    fun inserir(paginas: List<Pagina>, posicao: Int) {
        val ps = estado.paginas
        val k = posicao.coerceIn(0, ps.size)
        confirmar(Estado(ps.take(k) + paginas + ps.drop(k)))
    }

    fun branca(posicao: Int, quantidade: Int, modelo: Pagina?) {
        val l = modelo?.larguraEf ?: 595f
        val a = modelo?.alturaEf ?: 842f
        inserir(List(quantidade) { Pagina(novoId(), null, 0, 0, l, a, emptyList()) }, posicao)
    }

    fun fechar() {
        fontes.forEach { it.fechar() }
        fontes.clear()
    }

    companion object {
        /** Projeto com todas as páginas de uma fonte. */
        fun deFonte(nome: String, chave: String?, fonte: Fonte): Projeto {
            val p = Projeto(nome, chave)
            p.fontes.add(fonte)
            p.estado = Estado(List(fonte.paginas) { i ->
                val (w, h) = fonte.tamanho(i)
                Pagina(p.novoId(), fonte, i, 0, w, h, emptyList())
            })
            return p
        }
    }
}
