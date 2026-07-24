package com.juhao.murexide.ui.components

import java.lang.reflect.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenImageVideoFragmentTest {
    @Test
    fun `video fragment is publicly recreatable by FragmentStateAdapter`() {
        val fragmentClass = MurexideVideoPlayerFragment::class.java

        assertTrue(Modifier.isPublic(fragmentClass.modifiers))
        assertFalse(fragmentClass.isMemberClass)
        assertNotNull(fragmentClass.getConstructor())
    }

}
