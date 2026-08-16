package com.juhao.murexide.ui.components.litehtml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteHtmlCssBoundaryTest {
    @Test
    fun `render limit is measured in UTF-8 bytes`() {
        assertTrue(HtmlRenderPolicy.canRender("a".repeat(HtmlRenderPolicy.MAX_HTML_BYTES)))
        assertFalse(HtmlRenderPolicy.canRender("好".repeat(HtmlRenderPolicy.MAX_HTML_BYTES / 3 + 1)))
    }

    @Test
    fun `image policy accepts only network and data image URLs`() {
        assertTrue(HtmlRenderPolicy.isAllowedImageUrl("HTTPS://example.com/image.png"))
        assertTrue(HtmlRenderPolicy.isAllowedImageUrl("data:image/svg+xml;base64,AAAA"))
        assertFalse(HtmlRenderPolicy.isAllowedImageUrl("data:text/html;base64,AAAA"))
        assertFalse(HtmlRenderPolicy.isAllowedImageUrl("file:///private/image.png"))
        assertFalse(HtmlRenderPolicy.isAllowedImageUrl("content://private/image"))
        assertFalse(HtmlRenderPolicy.isAllowedImageUrl("javascript:alert(1)"))
    }

    @Test
    fun `internal deep links require the YunHu scheme`() {
        assertTrue(HtmlRenderPolicy.isInternalDeepLink("yunhu://post-detail?id=1"))
        assertFalse(HtmlRenderPolicy.isInternalDeepLink("YUNHU://post-detail?id=1"))
        assertFalse(HtmlRenderPolicy.isInternalDeepLink("https://example.com"))
        assertFalse(HtmlRenderPolicy.isInternalDeepLink("intent://other-app"))
    }
}