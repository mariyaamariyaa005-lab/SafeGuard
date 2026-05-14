package com.safeguard.app.classifier

data class EmergencyResult(
    val shouldTrigger: Boolean,
    val severity: Severity,
    val score: Int,
    val category: String,
    val matchedPhrases: List<String>,
    val confidence: Float,
    val emotion: String
)

enum class Severity { SAFE, LOW, MEDIUM, HIGH, CRITICAL }

class EmergencyClassifier {

    private val weightedPhrases = mapOf(
        // English - CRITICAL
        "help me" to 10, "save me" to 10, "help help" to 10,
        "somebody help" to 10, "anyone help" to 10,
        "he's going to kill me" to 10, "going to rape me" to 10,
        "kidnapping me" to 10, "abducting me" to 10,

        // English - HIGH
        "don't touch me" to 8, "leave me alone" to 8,
        "let me go" to 8, "get away from me" to 8,
        "stop touching me" to 8, "i'm being attacked" to 8,
        "someone is following me" to 7, "he won't let me go" to 8,
        "i'm scared" to 6, "i am scared" to 6,
        "i'm in danger" to 7, "i am in danger" to 7,
        "call police" to 7, "call the police" to 7,
        "murder" to 8, "rape" to 10, "kidnap" to 9,
        "knife" to 7, "gun" to 7, "weapon" to 7,
        "attack" to 7, "attacking me" to 8,

        // English - MEDIUM
        "i need help" to 5, "please help" to 5,
        "call 112" to 6, "danger" to 5,
        "emergency" to 5, "threatening me" to 6,
        "following me" to 5, "i'm lost" to 3,
        "uncomfortable" to 3, "unsafe" to 4,

        // Tamil - CRITICAL
        "உதவி" to 10, "காப்பாற்று" to 10,
        "உதவுங்கள்" to 10, "யாராவது உதவுங்கள்" to 10,
        "என்னை காப்பாற்று" to 10, "ஆபத்து" to 9,

        // Tamil - HIGH
        "விடு என்னை" to 8, "என்னை விடு" to 8,
        "தொடாதே" to 8, "போ இங்கிருந்து" to 8,
        "கத்துகிறேன்" to 7, "பயமாக இருக்கு" to 7,
        "யாரோ பின்தொடர்கிறார்கள்" to 8,
        "என்னை அடிக்கிறான்" to 10, "கொலை" to 9,
        "கத்தி" to 8, "துப்பாக்கி" to 8,

        // Tamil - MEDIUM
        "உதவி வேண்டும்" to 5, "காவல்துறை" to 6,
        "அவசரம்" to 5, "வலிக்குது" to 4,
        "பயம்" to 4, "தப்பிக்க வேண்டும்" to 7,
        // Tamil transliterated (for Vosk offline detection)
        "udhavi" to 10, "udhavungal" to 10,
        "kaaparu" to 10, "kaapaatre" to 10,
        "en னை kaapaatre" to 10,
        "aabathu" to 9, "aapathu" to 9,
        "vittu vida" to 8, "ennai vidu" to 8,
        "thodaadhe" to 8, "po ingirundhu" to 8,
        "bayamaa iruku" to 7, "bayam" to 5,
        "udavi vendum" to 5, "kavalai" to 4,
        "adi" to 6, "thalli" to 6,
        "thappikka vendum" to 7,
        "kolai" to 9, "kathi" to 8,
        "kaval" to 6, "police" to 6
    )

    private val categories = mapOf(
        "PHYSICAL_ATTACK" to listOf("attack", "attacking", "hit", "beat", "punch",
            "அடிக்கிறான்", "தள்ளுகிறான்"),
        "STALKING" to listOf("following", "stalking", "watching me",
            "பின்தொடர்கிறார்கள்"),
        "SEXUAL_THREAT" to listOf("rape", "touch", "touching", "molest",
            "தொடாதே"),
        "KIDNAPPING" to listOf("kidnap", "abduct", "let me go", "won't let me go",
            "என்னை விடு"),
        "GENERAL_DISTRESS" to listOf("help", "save", "danger", "scared", "fear",
            "உதவி", "காப்பாற்று", "ஆபத்து", "பயம்")
    )

    private val emotions = mapOf(
        "PANIC" to listOf("help help", "anybody", "someone", "உதவி உதவி"),
        "FEAR" to listOf("scared", "afraid", "fear", "பயம்", "பயமாக"),
        "ANGER" to listOf("stop", "don't", "leave", "விடு", "போ"),
        "DISTRESS" to listOf("please", "need", "வேண்டும்", "உதவுங்கள்")
    )

    fun classify(text: String): EmergencyResult {
        val lower = text.lowercase().trim()
        var score = 0
        val matched = mutableListOf<String>()

        weightedPhrases.forEach { (phrase, weight) ->
            if (lower.contains(phrase)) {
                score += weight
                matched.add(phrase)
            }
        }

        val severity = when {
            score >= 10 -> Severity.CRITICAL
            score >= 7 -> Severity.HIGH
            score >= 5 -> Severity.MEDIUM
            score >= 2 -> Severity.LOW
            else -> Severity.SAFE
        }

        val category = categories.entries.firstOrNull { (_, keywords) ->
            keywords.any { lower.contains(it) }
        }?.key ?: "GENERAL_DISTRESS"

        val emotion = emotions.entries.firstOrNull { (_, keywords) ->
            keywords.any { lower.contains(it) }
        }?.key ?: "DISTRESS"

        val confidence = (score.toFloat() / 20f).coerceIn(0f, 1f)

        return EmergencyResult(
            shouldTrigger = severity == Severity.CRITICAL || severity == Severity.HIGH,
            severity = severity,
            score = score,
            category = category,
            matchedPhrases = matched,
            confidence = confidence,
            emotion = emotion
        )
    }

    fun isUrgent(text: String): Boolean {
        val lower = text.lowercase()
        val urgentPhrases = listOf(
            "help me", "save me", "help help", "bachao",
            "உதவி", "காப்பாற்று", "என்னை காப்பாற்று",
            "rape", "murder", "kidnap", "knife", "gun",
            "கொலை", "கத்தி"
        )
        return urgentPhrases.any { lower.contains(it) }
    }
}