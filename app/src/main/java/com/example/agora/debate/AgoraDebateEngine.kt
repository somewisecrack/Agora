package com.example.agora.debate

import com.example.agora.data.AgoraDebateResult
import com.example.agora.data.Attachment
import com.example.agora.data.DebateTurn
import com.example.agora.llm.LocalLlm
import com.example.agora.llm.WebSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgoraDebateEngine(
    private val llm: LocalLlm,
    private val maxRounds: Int = MAX_ROUNDS
) {

    companion object {
        const val MAX_ROUNDS = 10
    }

    suspend fun runDebate(
        question: String,
        attachments: List<Attachment> = emptyList(),
        onStatusUpdate: (String) -> Unit = {}
    ): AgoraDebateResult {
        val turns = mutableListOf<DebateTurn>()
        var consensusReached = false

        // Decide if web search is needed, then fetch results silently
        onStatusUpdate("Thinking...")
        val searchCheck = llm.generate(PromptTemplates.needsWebSearch(question)).trim()
        val searchContext: String = if (searchCheck.uppercase().startsWith("YES")) {
            onStatusUpdate("Searching the web...")
            val results = withContext(Dispatchers.IO) { WebSearchService.search(question) }
            results.joinToString("\n\n") { "• ${it.title}: ${it.snippet}" }
        } else {
            ""
        }

        for (round in 1..maxRounds) {
            // --- Socrates turn ---
            onStatusUpdate("Socrates is thinking...")
            val socratesResponse = if (round == 1) {
                // Attachments only passed on the first turn — they ground the initial position
                llm.generate(PromptTemplates.socratesInitial(question, searchContext), attachments)
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
