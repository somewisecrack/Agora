package com.example.agora.llm

import com.example.agora.data.Attachment

/**
 * Fake LLM for UI and debate loop development/testing.
 * Replace with GemmaLocalLlm for real on-device inference.
 *
 * [consensusOnRound] controls which Plato call emits [CONSENSUS] (1-based Plato call index).
 * Set to Int.MAX_VALUE to never reach consensus (triggers max-round cap).
 */
class FakeLocalLlm(private val consensusOnRound: Int = 2) : LocalLlm {

    private var platoCallCount = 0

    override suspend fun generate(prompt: String, attachments: List<Attachment>): String {
        return when {
            prompt.contains("You are Plato") -> generatePlatoResponse()
            prompt.contains("State your initial position") -> generateSocratesInitial()
            prompt.contains("Revise, defend") -> generateSocratesRevision()
            prompt.contains("Write the final Agora advisory") -> generateAdvisory()
            else -> "I have considered the matter carefully and offer this perspective."
        }
    }

    private fun generatePlatoResponse(): String {
        platoCallCount++
        val base = "Socrates presents a reasonable starting point, but overlooks the practical " +
            "constraints that would determine feasibility. The argument assumes idealized conditions " +
            "that rarely hold in reality. Furthermore, the causal mechanism linking premise to " +
            "conclusion requires more rigorous justification. Without addressing these gaps, the " +
            "position remains philosophically interesting but operationally incomplete."
        return if (platoCallCount >= consensusOnRound) {
            "Socrates has adequately addressed the core objections. The revised position accounts " +
                "for the constraints I raised and the causal chain is now sufficiently grounded. " +
                "[CONSENSUS]"
        } else {
            base
        }
    }

    private fun generateSocratesInitial(): String =
        "The question at hand requires us to examine both immediate consequences and underlying " +
            "principles. My initial position is that the most defensible approach balances " +
            "pragmatic necessity with ethical clarity. We must resist oversimplification while " +
            "remaining actionable."

    private fun generateSocratesRevision(): String =
        "Plato raises a fair point about practical constraints. I refine my position: the approach " +
            "must explicitly account for resource limitations and contextual variables. The core " +
            "recommendation stands, but implementation must be adaptive rather than rigid."

    private fun generateAdvisory(): String =
        "Conclusion:\nProceed with the approach while building in explicit checkpoints for " +
            "reassessment. Prioritize reversible actions where possible.\n\n" +
            "Reasoning:\n- The debate converged on a pragmatically grounded position.\n" +
            "- Plato's objections strengthened rather than invalidated the core argument.\n" +
            "- Remaining uncertainty is manageable with iterative review.\n\n" +
            "Plato noted:\nThe strongest objection concerned feasibility under real-world " +
            "constraints. This was resolved by conditioning the recommendation on adaptive " +
            "implementation.\n\nConfidence:\nMedium — the reasoning is sound but outcome " +
            "depends on execution context."
}
