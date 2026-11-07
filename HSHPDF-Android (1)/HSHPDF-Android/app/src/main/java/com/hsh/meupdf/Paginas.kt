// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.coroutines.cancellation.CancellationException

/** Modo "Páginas": grade de miniaturas com seleção múltipla e as operações de página do app do computador. */
@Composable
fun GradePaginas(
    proj: Projeto, onAbrir: (Int) -> Unit, onFechar: () -> Unit,
    onInserirArquivo: () -> Unit, onExportar: (List<Int>) -> Unit
) {
    val paginas = proj.estado.paginas
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var dMover by remember { mutableStateOf(false) }
    var dBranca by remember { mutableStateOf(false) }
    var dExcluir by remember { mutableStateOf(false) }
    val somenteLeitura = proj.somenteLeitura

    fun exigeSel(f: (Set<Int>) -> Unit): () -> Unit = { if (sel.isNotEmpty()) f(sel) }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onFechar) { Text("‹ Leitura") }
                    Text("${sel.size} de ${paginas.size} selecionada(s)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
                    BotaoFerr("Todas", false) { sel = paginas.indices.toSet() }
                    BotaoFerr("Limpar", false) { sel = emptySet() }
                    BotaoFerr("⟲", false, !somenteLeitura && sel.isNotEmpty(), exigeSel { proj.girar(it, -90) })
                    BotaoFerr("⟳", false, !somenteLeitura && sel.isNotEmpty(), exigeSel { proj.girar(it, 90) })
                    BotaoFerr("Duplicar", false, !somenteLeitura && sel.isNotEmpty(), exigeSel { proj.duplicar(it); sel = emptySet() })
                    BotaoFerr("Mover...", false, !somenteLeitura && sel.isNotEmpty()) { dMover = true }
                    BotaoFerr("Excluir", false, !somenteLeitura && sel.isNotEmpty()) { dExcluir = true }
                    BotaoFerr("Extrair", false, sel.isNotEmpty()) { onExportar(sel.sorted()) }
                    BotaoFerr("Em branco...", false, !somenteLeitura) { dBranca = true }
                    BotaoFerr("Inserir de arquivo...", false, !somenteLeitura) { onInserirArquivo() }
                    BotaoFerr("↶", false, proj.podeDesfazer && !somenteLeitura) { proj.desfazer(); sel = emptySet() }
                    BotaoFerr("↷", false, proj.podeRefazer && !somenteLeitura) { proj.refazer(); sel = emptySet() }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = paginas.size, key = { paginas[it].id }) { i ->
                val p = paginas[i]
                var bmp by remember(p) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(p) {
                    bmp = try { Exportador.renderFinal(p, 220) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                }
                val marcada = i in sel
                Column(
                    Modifier.pointerInput(i, sel.isNotEmpty()) {
                        detectTapGestures(
                            onTap = { if (sel.isNotEmpty()) sel = if (marcada) sel - i else sel + i else onAbrir(i) },
                            onLongPress = { sel = if (marcada) sel - i else sel + i }
                        )
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f / p.razao).background(Color.White)
                            .border(if (marcada) 4.dp else 1.dp, if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        bmp?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
                    }
                    Text("${i + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }

    if (dExcluir) {
        AlertDialog(
            onDismissRequest = { dExcluir = false }, title = { Text("Excluir páginas") },
            text = { Text("Excluir ${sel.size} página(s)? Dá para desfazer em ↶.") },
            confirmButton = { TextButton(onClick = { proj.excluir(sel); sel = emptySet(); dExcluir = false }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { dExcluir = false }) { Text("Cancelar") } }
        )
    }
    if (dMover) {
        var txt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dMover = false }, title = { Text("Mover páginas") },
            text = {
                Column {
                    Text("Mover as páginas selecionadas para ANTES da página nº (1 a ${paginas.size + 1}; ${paginas.size + 1} = fim):", fontSize = 13.sp)
                    OutlinedTextField(value = txt, onValueChange = { txt = it.filter { c -> c.isDigit() } }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = txt.toIntOrNull()
                    if (n != null) { proj.mover(sel, (n - 1).coerceIn(0, paginas.size)); sel = emptySet() }
                    dMover = false
                }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { dMover = false }) { Text("Cancelar") } }
        )
    }
    if (dBranca) {
        var qtd by remember { mutableStateOf("1") }
        var apos by remember { mutableStateOf((sel.maxOrNull()?.plus(1) ?: paginas.size).toString()) }
        AlertDialog(
            onDismissRequest = { dBranca = false }, title = { Text("Páginas em branco") },
            text = {
                Column {
                    OutlinedTextField(value = qtd, onValueChange = { qtd = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("Quantidade") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = apos, onValueChange = { apos = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("Depois da página nº (0 = no início)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = (qtd.toIntOrNull() ?: 1).coerceIn(1, 200)
                    val a = (apos.toIntOrNull() ?: paginas.size).coerceIn(0, paginas.size)
                    proj.branca(a, q, paginas.getOrNull((a - 1).coerceAtLeast(0)))
                    dBranca = false
                }) { Text("Inserir") }
            },
            dismissButton = { TextButton(onClick = { dBranca = false }) { Text("Cancelar") } }
        )
    }
}
