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

        // Questions containing any of these phrases likely need live web data
        private val SEARCH_TRIGGERS = listOf(
            "who is ", "who are ", "who was ", "who's ", "who won ", "who holds ",
            "who leads ", "who currently ", "who did ",
            "current ", "currently", "right now", "as of now", "as of today",
            "today", "this week", "this month", "this year",
            "latest ", "most recent", "recent ", "just released", "just announced",
            "2024", "2025", "2026",
            "champion", "world record", "number one", "top ranked",
            "president", "prime minister", "ceo", "cfo",
            "price of", "cost of", "how much is", "exchange rate", "stock price",
            "weather", "forecast", "temperature",
            "score", "standings", "ranking", "leaderboard",
            "news", "announced", "launched", "released",
            "happened", "latest update"
        )

        fun needsSearch(question: String): Boolean {
            val q = question.lowercase()
            return SEARCH_TRIGGERS.any { q.contains(it) }
        }
    }

    suspend fun runDebate(
        question: String,
        attachments: List<Attachment> = emptyList(),
        conversationHistory: String = "",
        onStatusUpdate: (String) -> Unit = {}
    ): AgoraDebateResult {
        val turns = mutableListOf<DebateTurn>()
        var consensusReached = false

        // For short/ambiguous follow-ups (e.g. "check again", "why?"), resolve
        // the search query using context from the thread's last user question
        val searchQuery = if (question.split(" ").size <= 4 && conversationHistory.isNotEmpty()) {
            // Extract last user question from history as the search topic
            val lastUserLine = conversationHistory.lines()
                .lastOrNull { it.startsWith("User: ") }
                ?.removePrefix("User: ")?.trim()
            if (lastUserLine != null) "$lastUserLine — $question" else question
        } else question

        // Keyword-based detection — instant, no LLM call needed
        val searchContext: String = if (needsSearch(searchQuery)) {
            onStatusUpdate("Searching the web...")
            val results = withContext(Dispatchers.IO) { WebSearchService.search(searchQuery) }
            if (results.isNotEmpty())
                results.joinToString("\n\n") { "• ${it.title}: ${it.snippet}" }
            else
                ""
        } else {
            ""
        }

        for (round in 1..maxRounds) {
            // --- Socrates turn ---
            onStatusUpdate("Socrates is thinking...")
            val socratesResponse = if (round == 1) {
                // Attachments only passed on the first turn — they ground the initial position
                llm.generate(PromptTemplates.socratesInitial(question, searchContext, conversationHistory), attachments)
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
