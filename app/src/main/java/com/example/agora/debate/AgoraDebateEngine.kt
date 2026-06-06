package com.example.agora.debate

import com.example.agora.data.AgoraDebateResult
import com.example.agora.data.DebateTurn
import com.example.agora.llm.LocalLlm

class AgoraDebateEngine(
    private val llm: LocalLlm,
    private val maxRounds: Int = MAX_ROUNDS
) {

    companion object {
        const val MAX_ROUNDS = 10
    }

    suspend fun runDebate(
        question: String,
        onStatusUpdate: (String) -> Unit = {}
    ): AgoraDebateResult {
        val turns = mutableListOf<DebateTurn>()
        var consensusReached = false

        for (round in 1..maxRounds) {
            // --- Socrates turn ---
            onStatusUpdate("Socrates is thinking...")
            val socratesResponse = if (round == 1) {
                llm.generate(PromptTemplates.socratesInitial(question))
            } else {
                val transcript = TranscriptFormatter.formatTurns(turns)
                llm.generate(PromptTemplates.socratesRevision(question, transcript))
            }

            // --- Plato turn ---
            onStatusUpdate("Plato is challenging...")
            val transcriptSoFar = buildString {
                append(TranscriptFormatter.formatTurns(turns))
                if (turns.isNotEmpty()) append("\n\n")
                append("Round $round\n\uD83D\uDFE1 Socrates: $socratesResponse")
            }
            val platoResponse = llm.generate(
                PromptTemplates.platoCritique(question, transcriptSoFar)
            )

            turns.add(DebateTurn(round, socratesResponse, platoResponse))

            if (platoResponse.contains("[CONSENSUS]", ignoreCase = true)) {
                consensusReached = true
                break
            }
        }

        // --- Final advisory ---
        onStatusUpdate("Generating advisory...")
        val finalTranscript = TranscriptFormatter.formatTurns(turns)
        val advisory = llm.generate(PromptTemplates.finalAdvisory(question, finalTranscript))

        return AgoraDebateResult(
            question = question,
            turns = turns,
            advisory = advisory,
            consensusReached = consensusReached
        )
    }
}
