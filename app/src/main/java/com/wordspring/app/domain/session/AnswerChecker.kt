package com.wordspring.app.domain.session

import com.wordspring.app.domain.model.Grade

/**
 * Pure answer-checking helpers (M05). No Android, no DB.
 *
 * Spelling rule (spec):
 *  - 仅忽略前后空白和英文大小写
 *  - 不忽略字母增减、词形和短语内部空格
 *  - 错误保留用户输入，显示标准拼写
 *  - 可简单提示少了或多了一个字符，复杂情况仅显示正确答案
 */
object AnswerChecker {

    data class SpellingResult(
        val isCorrect: Boolean,
        val userAnswer: String,
        val correctAnswer: String,
        val hint: String? = null
    )

    /**
     * Compare user input to the correct spelling.
     */
    fun checkSpelling(userInput: String, correctSpelling: String): SpellingResult {
        val trimmedUser = userInput.trim()
        val trimmedCorrect = correctSpelling.trim()
        val user = trimmedUser.lowercase()
        val correct = trimmedCorrect.lowercase()
        val isCorrect = user == correct
        val hint = if (isCorrect) null else lengthHint(user, correct)
        return SpellingResult(
            isCorrect = isCorrect,
            userAnswer = trimmedUser,
            correctAnswer = trimmedCorrect,
            hint = hint
        )
    }

    private fun lengthHint(user: String, correct: String): String? {
        return when {
            user.length == correct.length - 1 -> "少了 1 个字符"
            user.length == correct.length + 1 -> "多了 1 个字符"
            user.length == correct.length -> "字符顺序或内容不对"
            else -> null
        }
    }

    /**
     * Map a self-grade from RECALL or a CHOICE selection to a Grade.
     */
    fun gradeFromSelfAssessment(selfGrade: Grade): Grade = selfGrade

    /**
     * Map a CHOICE answer to a grade.
     */
    fun gradeFromChoice(isCorrectChoice: Boolean): Grade =
        if (isCorrectChoice) Grade.GOOD else Grade.AGAIN

    /**
     * Map a SPELLING answer to a grade.
     */
    fun gradeFromSpelling(result: SpellingResult): Grade =
        if (result.isCorrect) Grade.GOOD else Grade.AGAIN
}
