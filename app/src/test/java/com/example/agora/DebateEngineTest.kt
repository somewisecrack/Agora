package com.example.agora

import com.example.agora.debate.AgoraDebateEngine
import com.example.agora.llm.FakeLocalLlm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebateEngineTest {

    @Test
    fun `debate stops when Plato emits CONSENSUS`() = runBlocking {
        val engine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = 1))
        val result = engine.runDebate("Test question")
        assertTrue("Expected consensus to be reached", result.consensusReached)
        assertEquals("Expected exactly 1 turn", 1, result.turns.size)
    }

    @Test
    fun `debate continues until Plato emits CONSENSUS on round 3`() = runBlocking {
        val engine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = 3))
        val result = engine.runDebate("Test question")
        assertTrue("Expected consensus to be reached", result.consensusReached)
        assertEquals("Expected exactly 3 turns", 3, result.turns.size)
    }

    @Test
    fun `debate hits max round cap when Plato never reaches consensus`() = runBlocking {
        val engine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = Int.MAX_VALUE), maxRounds = 3)
        val result = engine.runDebate("Test question")
        assertFalse("Expected consensus NOT reached", result.consensusReached)
        assertEquals("Expected exactly max rounds turns", 3, result.turns.size)
    }

    @Test
    fun `transcript includes both Socrates and Plato labels`() = runBlocking {
        val engine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = 1))
        val result = engine.runDebate("What should I do?")
        val allText = result.turns.joinToString { "Round ${it.round} S:${it.socrates} P:${it.plato}" }
        assertTrue(result.turns.all { it.socrates.isNotBlank() })
        assertTrue(result.turns.all { it.plato != null && it.plato!!.isNotBlank() })
    }

    @Test
    fun `advisory is generated regardless of consensus status`() = runBlocking {
        val consensusEngine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = 1))
        val noConsensusEngine = AgoraDebateEngine(FakeLocalLlm(consensusOnRound = Int.MAX_VALUE), maxRounds = 2)

        val consensusResult = consensusEngine.runDebate("Q")
        val noConsensusResult = noConsensusEngine.runDebate("Q")

        assertTrue(consensusResult.advisory.isNotBlank())
        assertTrue(noConsensusResult.advisory.isNotBlank())
    }
}
