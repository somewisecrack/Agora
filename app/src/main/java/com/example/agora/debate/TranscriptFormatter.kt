package com.example.agora.debate

import com.example.agora.data.AgoraDebateResult
import com.example.agora.data.DebateTurn

object TranscriptFormatter {

    fun formatTurns(turns: List<DebateTurn>): String {
        if (turns.isEmpty()) return ""
        return turns.joinToString("\n\n") { turn ->
            buildString {
                append("Round ${turn.round}\n")
                append("\uD83D\uDFE1 Socrates: ${turn.socrates}")
                if (turn.plato != null) {
                    append("\n\uD83D\uDFE3 Plato: ${turn.plato}")
                }
            }
        }
    }

    fun formatFullResult(result: AgoraDebateResult): String {
        return buildString {
            append("\uD83C\uDFDB\uFE0F AGORA EXCHANGE\n\n")
            append(formatTurns(result.turns))
            append("\n\n\uD83C\uDFDB\uFE0F AGORA ADVISORY\n\n")
            append(result.advisory)
            if (!result.consensusReached) {
                append("\n\nConsensus status: unresolved after max rounds.")
            }
        }
    }
}
