package com.wordspring.app.ui.nav

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BottomNavigationTest {

    @Test
    fun `home tab pops a shortcut page back to the existing home destination`() {
        assertThat(bottomNavigationAction(currentRoute = Routes.BOOKS, targetRoute = Routes.HOME))
            .isEqualTo(BottomNavigationAction.ReturnToHome)
    }

    @Test
    fun `other bottom tabs navigate to their target`() {
        assertThat(bottomNavigationAction(currentRoute = Routes.HOME, targetRoute = Routes.BOOKS))
            .isEqualTo(BottomNavigationAction.Navigate)
    }
}
