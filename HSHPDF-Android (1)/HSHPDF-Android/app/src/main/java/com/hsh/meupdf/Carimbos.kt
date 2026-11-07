// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

val COR_VERDE = 0xFF1E9E4A.toInt()
val COR_AZUL = 0xFF2563EB.toInt()
val COR_VERMELHO = 0xFFD92D2D.toInt()
val COR_LARANJA = 0xFFF08A0C.toInt()
val COR_ROXO = 0xFF7C3AED.toInt()
val COR_CINZA = 0xFF444444.toInt()
val CORES_CARIMBO = listOf(COR_VERDE, COR_AZUL, COR_VERMELHO, COR_LARANJA, COR_ROXO, COR_CINZA)

val CARIMBOS_PRONTOS: List<Pair<String, List<CarimboSalvo>>> = listOf(
    "Aprovação" to listOf(
        CarimboSalvo("APROVADO", COR_VERDE, false, null), CarimboSalvo("REVISADO", COR_AZUL, false, null),
        CarimboSalvo("ASSINADO", COR_VERDE, false, null), CarimboSalvo("PAGO", COR_VERDE, false, null),
        CarimboSalvo("REPROVADO", COR_VERMELHO, false, null), CarimboSalvo("CANCELADO", COR_VERMELHO, false, null)
    ),
    "Status" to listOf(
        CarimboSalvo("RASCUNHO", COR_LARANJA, false, null), CarimboSalvo("CONFIDENCIAL", COR_VERMELHO, false, null),
        CarimboSalvo("URGENTE", COR_VERMELHO, false, null), CarimboSalvo("CÓPIA", COR_CINZA, false, null),
        CarimboSalvo("ORIGINAL", COR_AZUL, false, null), CarimboSalvo("EM ANÁLISE", COR_ROXO, false, null)
    ),
    "Setas" to listOf(
        CarimboSalvo("AQUI", COR_VERMELHO, true, null), CarimboSalvo("ASSINE AQUI", COR_AZUL, true, null),
        CarimboSalvo("CONFERIR", COR_LARANJA, true, null), CarimboSalvo("VER", COR_VERDE, true, null)
    )
)

/** Copia uma imagem escolhida para a pasta interna do app (anotações precisam do arquivo depois). */
fun copiarParaItens(ctx: android.content.Context, uri: Uri): File {
    val dir = File(ctx.filesDir, "itens").apply { mkdirs() }
    val f = File(dir, "${System.currentTimeMillis()}.img")
    val entrada = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Não consegui ler a imagem.")
    entrada.use { e -> f.outputStream().use { s -> e.copyTo(s) } }
    return f
}

@Composable
private fun CartaoCarimbo(c: CarimboSalvo, onClick: () -> Unit, onRemover: (() -> Unit)? = null) {
    val cor = Color(c.cor)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).border(2.dp, cor, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text((if (c.seta) "◀ " else "") + c.texto, color = cor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onRemover != null) TextButton(onClick = onRemover) { Text("✕") }
    }
}

/** Escolha de carimbo: prontos, os seus (texto ou imagem) e criação de novos. */
@Composable
fun DialogoCarimbos(cfg: Config, onEscolher: (CarimboSalvo) -> Unit, onFechar: () -> Unit) {
    val ctx = LocalContext.current
    var meus by remember { mutableStateOf(cfg.carimbos()) }
    var criando by remember { mutableStateOf(false) }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val f = copiarParaItens(ctx, uri)
                meus = meus + CarimboSalvo("imagem", 0, false, f.absolutePath)
                cfg.gravarCarimbos(meus)
            } catch (e: Exception) { }
        }
    }
    AlertDialog(
        onDismissRequest = onFechar, title = { Text("Carimbos") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((grupo, lista) in CARIMBOS_PRONTOS) {
                    Text(grupo, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    for (c in lista) CartaoCarimbo(c, { onEscolher(c) })
                }
                Text("Meus carimbos", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
                if (meus.isEmpty()) Text("Nenhum ainda.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                meus.forEachIndexed { i, c ->
                    val remover = { meus = meus.filterIndexed { k, _ -> k != i }; cfg.gravarCarimbos(meus) }
                    if (c.arquivo == null) CartaoCarimbo(c, { onEscolher(c) }, remover)
                    else Row(Modifier.fillMaxWidth().clickable { onEscolher(c) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🖼 Imagem ${i + 1}", modifier = Modifier.weight(1f))
                        TextButton(onClick = remover) { Text("✕") }
                    }
                }
                Row {
                    TextButton(onClick = { criando = true }) { Text("＋ Criar de texto") }
                    TextButton(onClick = { importar.launch("image/*") }) { Text("＋ Importar imagem") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
    if (criando) {
        var txt by remember { mutableStateOf("MEU CARIMBO") }
        var cor by remember { mutableIntStateOf(COR_VERDE) }
        var seta by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { criando = false }, title = { Text("Novo carimbo") },
            text = {
                Column {
                    OutlinedTextField(value = txt, onValueChange = { txt = it }, singleLine = true, label = { Text("Texto") })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in CORES_CARIMBO) {
                            Box(Modifier.size(30.dp).background(Color(c), RoundedCornerShape(6.dp))
                                .border(if (cor == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)).clickable { cor = c })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = seta, onCheckedChange = { seta = it }); Text("Formato de seta")
                    }
                    CartaoCarimbo(CarimboSalvo(txt.ifBlank { " " }, cor, seta, null), { })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (txt.isNotBlank()) { meus = meus + CarimboSalvo(txt.trim(), cor, seta, null); cfg.gravarCarimbos(meus) }
                    criando = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { criando = false }) { Text("Cancelar") } }
        )
    }
}
