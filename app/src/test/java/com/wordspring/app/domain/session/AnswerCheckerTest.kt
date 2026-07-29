package com.wordspring.app.domain.session

import com.google.common.truth.Truth.assertThat
import com.wordspring.app.domain.model.Grade
import org.junit.Test

/**
 * Pure unit tests for the M05 answer checker (spelling, choice, self-assessment).
 */
class AnswerCheckerTest {

    @Test
    fun checkSpelling_exactMatch_isCorrect() {
        val r = AnswerChecker.checkSpelling("apple", "apple")
        assertThat(r.isCorrect).isTrue()
    }

    @Test
    fun checkSpelling_caseInsensitive_isCorrect() {
        val r = AnswerChecker.checkSpelling("APPLE", "apple")
        assertThat(r.isCorrect).isTrue()
    }

    @Test
    fun checkSpelling_surroundingWhitespace_isCorrect() {
        val r = AnswerChecker.checkSpelling("  apple  ", "apple")
        assertThat(r.isCorrect).isTrue()
    }

    @Test
    fun checkSpelling_extraLetter_isIncorrectWithHint() {
        val r = AnswerChecker.checkSpelling("apples", "apple")
        assertThat(r.isCorrect).isFalse()
        assertThat(r.hint).contains("多")
    }

    @Test
    fun checkSpelling_missingLetter_isIncorrectWithHint() {
        val r = AnswerChecker.checkSpelling("appl", "apple")
        assertThat(r.isCorrect).isFalse()
        assertThat(r.hint).contains("少")
    }

    @Test
    fun checkSpelling_sameLengthDifferentChars_hintAboutContent() {
        val r = AnswerChecker.checkSpelling("applf", "apple")
        assertThat(r.isCorrect).isFalse()
        assertThat(r.hint).contains("字符")
    }

    @Test
    fun checkSpelling_veryDifferentLengths_noHint() {
        val r = AnswerChecker.checkSpelling("ab", "apple")
        assertThat(r.isCorrect).isFalse()
        assertThat(r.hint).isNull()
    }

    @Test
    fun checkSpelling_interiorSpaceDifference_isIncorrect() {
        // Spec: 不忽略短语内部空格
        val r = AnswerChecker.checkSpelling("icecream", "ice cream")
        assertThat(r.isCorrect).isFalse()
    }

    @Test
    fun checkSpelling_preservesUserAnswerAndCorrectAnswer() {
        val r = AnswerChecker.checkSpelling("  Apple  ", "apple")
        assertThat(r.userAnswer).isEqualTo("Apple")
        assertThat(r.correctAnswer).isEqualTo("apple")
    }

    @Test
    fun gradeFromChoice_correct_returnsGood() {
        assertThat(AnswerChecker.gradeFromChoice(true)).isEqualTo(Grade.GOOD)
    }

    @Test
    fun gradeFromChoice_wrong_returnsAgain() {
        assertThat(AnswerChecker.gradeFromChoice(false)).isEqualTo(Grade.AGAIN)
    }

    @Test
    fun gradeFromSpelling_correct_returnsGood() {
        val r = AnswerChecker.checkSpelling("apple", "apple")
        assertThat(AnswerChecker.gradeFromSpelling(r)).isEqualTo(Grade.GOOD)
    }

    @Test
    fun gradeFromSpelling_wrong_returnsAgain() {
        val r = AnswerChecker.checkSpelling("applf", "apple")
        assertThat(AnswerChecker.gradeFromSpelling(r)).isEqualTo(Grade.AGAIN)
    }
}
