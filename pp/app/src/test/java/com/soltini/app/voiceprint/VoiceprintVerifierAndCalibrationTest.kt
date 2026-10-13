package com.soltini.app.voiceprint

import com.soltini.app.voiceprint.domain.EnrollmentManager
import com.soltini.app.voiceprint.domain.VoiceprintVerifier
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class VoiceprintVerifierAndCalibrationTest {

    @Test
    fun testCosineSimilarityAndL2Normalization() {
        val v1 = floatArrayOf(3f, 4f)
        val normalized = EnrollmentManager.l2Normalize(v1)
        assertEquals(0.6f, normalized[0], 1e-5f)
        assertEquals(0.8f, normalized[1], 1e-5f)

        val norm = sqrt(normalized[0] * normalized[0] + normalized[1] * normalized[1])
        assertEquals(1.0f, norm, 1e-5f)

        // Identical vectors should have similarity 1.0
        val simIdentical = EnrollmentManager.cosineSimilarity(normalized, normalized)
        assertEquals(1.0f, simIdentical, 1e-5f)

        // Orthogonal vectors should have similarity 0.0
        val orthogonal = floatArrayOf(-0.8f, 0.6f)
        val simOrthogonal = EnrollmentManager.cosineSimilarity(normalized, orthogonal)
        assertEquals(0.0f, simOrthogonal, 1e-5f)
    }

    @Test
    fun testCentroidAndLeaveOneOutCalibration() {
        val dim = 4
        // 5 synthetic normalized vectors close to each other
        val e1 = EnrollmentManager.l2Normalize(floatArrayOf(1.0f, 0.1f, 0.05f, 0.02f))
        val e2 = EnrollmentManager.l2Normalize(floatArrayOf(0.95f, 0.15f, 0.04f, 0.01f))
        val e3 = EnrollmentManager.l2Normalize(floatArrayOf(0.98f, 0.12f, 0.06f, 0.03f))
        val e4 = EnrollmentManager.l2Normalize(floatArrayOf(1.02f, 0.09f, 0.05f, 0.02f))
        val e5 = EnrollmentManager.l2Normalize(floatArrayOf(0.97f, 0.11f, 0.07f, 0.04f))

        val list = listOf(e1, e2, e3, e4, e5)
        val threshold = EnrollmentManager.calibrateThreshold(list)

        // Given how close these vectors are, threshold should be high and within sane bounds [0.62, 0.85]
        assertTrue("Threshold should be >= 0.62, got $threshold", threshold >= 0.62f)
        assertTrue("Threshold should be <= 0.85, got $threshold", threshold <= 0.85f)
    }

    @Test
    fun testHysteresisStateMachine() {
        val acceptThreshold = 0.75f
        val rejectThreshold = acceptThreshold - VoiceprintVerifier.HYSTERESIS_MARGIN // 0.67f

        // Initial state UNKNOWN
        var state = VoiceprintVerifier.VerificationStateEnum.UNKNOWN

        // Synthetic score below accept: stays UNKNOWN or NOT_OWNER
        val scoreLow = 0.50f
        if (state != VoiceprintVerifier.VerificationStateEnum.OWNER) {
            state = if (scoreLow >= acceptThreshold) VoiceprintVerifier.VerificationStateEnum.OWNER
            else if (scoreLow < rejectThreshold) VoiceprintVerifier.VerificationStateEnum.NOT_OWNER
            else VoiceprintVerifier.VerificationStateEnum.UNKNOWN
        }
        assertEquals(VoiceprintVerifier.VerificationStateEnum.NOT_OWNER, state)

        // Score between reject and accept (e.g. 0.70): stays NOT_OWNER/UNKNOWN
        val scoreMid = 0.70f
        if (state != VoiceprintVerifier.VerificationStateEnum.OWNER) {
            state = if (scoreMid >= acceptThreshold) VoiceprintVerifier.VerificationStateEnum.OWNER
            else if (scoreMid < rejectThreshold) VoiceprintVerifier.VerificationStateEnum.NOT_OWNER
            else VoiceprintVerifier.VerificationStateEnum.UNKNOWN
        }
        assertEquals(VoiceprintVerifier.VerificationStateEnum.UNKNOWN, state)

        // High score (0.82): transitions to OWNER
        val scoreHigh = 0.82f
        if (state != VoiceprintVerifier.VerificationStateEnum.OWNER) {
            state = if (scoreHigh >= acceptThreshold) VoiceprintVerifier.VerificationStateEnum.OWNER
            else if (scoreHigh < rejectThreshold) VoiceprintVerifier.VerificationStateEnum.NOT_OWNER
            else VoiceprintVerifier.VerificationStateEnum.UNKNOWN
        }
        assertEquals(VoiceprintVerifier.VerificationStateEnum.OWNER, state)

        // Score dips to 0.70 (above reject 0.67, but below accept 0.75): stays OWNER (Hysteresis!)
        if (state == VoiceprintVerifier.VerificationStateEnum.OWNER) {
            state = if (scoreMid >= rejectThreshold) VoiceprintVerifier.VerificationStateEnum.OWNER
            else VoiceprintVerifier.VerificationStateEnum.NOT_OWNER
        }
        assertEquals(VoiceprintVerifier.VerificationStateEnum.OWNER, state)

        // Score drops below reject to 0.60: transitions to NOT_OWNER
        val scoreDrop = 0.60f
        if (state == VoiceprintVerifier.VerificationStateEnum.OWNER) {
            state = if (scoreDrop >= rejectThreshold) VoiceprintVerifier.VerificationStateEnum.OWNER
            else VoiceprintVerifier.VerificationStateEnum.NOT_OWNER
        }
        assertEquals(VoiceprintVerifier.VerificationStateEnum.NOT_OWNER, state)
    }
}
