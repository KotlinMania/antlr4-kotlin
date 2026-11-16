package io.github.kotlinmania.antlr4

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun platformNameIsBoundInCommonMain() {
        val platformName = Platform().name

        assertTrue(platformName.isNotBlank())
        assertEquals(platformName, currentPlatformName())
    }
}
