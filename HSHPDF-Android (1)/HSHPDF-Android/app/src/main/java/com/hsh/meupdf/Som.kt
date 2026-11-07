// Criador da fonte: Henrique de Souza Henrique
// Coautor e assistente de IA: Claude (Anthropic)
package com.hsh.meupdf

import android.content.Context
import android.media.MediaPlayer

/** Som de conclusão (ao salvar, exportar, alterar páginas...). Não repete em menos de 2,5 s. */
object Som {
    private var ultimo = 0L

    fun concluir(ctx: Context) {
        if (!Config(ctx).som) return
        val agora = System.currentTimeMillis()
        if (agora - ultimo < 2500) return
        ultimo = agora
        try {
            MediaPlayer.create(ctx, R.raw.conclusao)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) { }
    }
}
