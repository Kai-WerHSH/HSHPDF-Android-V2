// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ACENTOS = listOf(0xFFE84A4A, 0xFF3B82F6, 0xFF22A55B, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899).map { it.toInt() }

@Composable
fun InicioTela(
    cfg: Config, tema: String, carregando: Boolean, erro: String?, recentes: List<Recente>,
    onAbrir: () -> Unit, onImagens: () -> Unit, onNovo: () -> Unit, onLote: () -> Unit,
    onRecente: (Recente) -> Unit, onRemover: (Recente) -> Unit,
    onTema: (String) -> Unit, onAcento: (Int) -> Unit, onFecharErro: () -> Unit
) {
    var ajustes by remember { mutableStateOf(false) }
    var som by remember { mutableStateOf(cfg.som) }
    var hq by remember { mutableStateOf(cfg.leituraHq) }
    var cheia by remember { mutableStateOf(cfg.telaCheia) }
    val escuro = temaEscuro(tema)
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(if (escuro) R.drawable.logo_escuro else R.drawable.logo_claro),
                contentDescription = "HSHPDF", modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.size(12.dp))
            Text("HSHPDF", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            TextButton(onClick = { ajustes = true }) { Text("Ajustes") }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAbrir, enabled = !carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (carregando) "Abrindo..." else "Abrir PDF, EPUB, CBZ, CBR ou imagem", modifier = Modifier.padding(6.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onImagens, enabled = !carregando, modifier = Modifier.weight(1f)) { Text("Imagens → PDF", fontSize = 12.sp) }
            OutlinedButton(onClick = onNovo, enabled = !carregando, modifier = Modifier.weight(1f)) { Text("Novo em branco", fontSize = 12.sp) }
            OutlinedButton(onClick = onLote, enabled = !carregando, modifier = Modifier.weight(1f)) { Text("Lote", fontSize = 12.sp) }
        }
        Spacer(Modifier.height(20.dp))
        Text("Recentes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        if (recentes.isEmpty()) Text("Nenhum arquivo recente.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(recentes, key = { it.uri }) { r ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onRecente(r) },
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp
                ) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.nome, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                            Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(r.quando)),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { onRemover(r) }) { Text("✕") }
                    }
                }
            }
        }
        Text("Criador da fonte: Henrique de Souza Henrique\nCoautor e assistente de IA: Claude (Anthropic)",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }

    if (erro != null) {
        AlertDialog(onDismissRequest = onFecharErro, title = { Text("Não consegui abrir") }, text = { Text(erro) },
            confirmButton = { TextButton(onClick = onFecharErro) { Text("OK") } })
    }
    if (ajustes) {
        AlertDialog(
            onDismissRequest = { ajustes = false }, title = { Text("Ajustes") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Tema", fontWeight = FontWeight.Bold)
                    for ((rot, v) in listOf("Escuro" to "escuro", "Claro" to "claro", "Igual ao sistema" to "sistema")) {
                        Row(Modifier.fillMaxWidth().clickable { onTema(v) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tema == v, onClick = { onTema(v) }); Text(rot)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Cor de destaque", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in ACENTOS) Box(Modifier.size(34.dp).background(Color(c), MaterialTheme.shapes.small).clickable { onAcento(c) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Som de conclusão", modifier = Modifier.weight(1f))
                        Switch(checked = som, onCheckedChange = { som = it; cfg.som = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tela cheia ao ler (esconde hora e bateria)", modifier = Modifier.weight(1f))
                        Switch(checked = cheia, onCheckedChange = { cheia = it; cfg.telaCheia = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Quadrinhos e EPUB em somente leitura", modifier = Modifier.weight(1f))
                        Switch(checked = hq, onCheckedChange = { hq = it; cfg.leituraHq = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { ajustes = false }) { Text("Fechar") } }
        )
    }
}
