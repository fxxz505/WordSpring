package com.wordspring.app.ui.nav

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetailScreenContentPaddingTest {

    @Test
    fun `detail screens retain the outer bottom navigation inset`() {
        val outerScaffoldPadding = PaddingValues(bottom = 80.dp)

        assertThat(detailScreenContentPadding(outerScaffoldPadding) === outerScaffoldPadding)
            .isTrue()
    }
}
