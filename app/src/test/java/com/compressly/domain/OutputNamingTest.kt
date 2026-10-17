package com.compressly.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNamingTest {

    @Test
    fun `plain name gets the mp4 extension`() {
        assertEquals("Holiday clip.mp4", OutputNaming.sanitize("Holiday clip", "IMG_1.MP4"))
    }

    @Test
    fun `existing extension is replaced not appended`() {
        assertEquals("clip.mp4", OutputNaming.sanitize("clip.mkv", "IMG_1.MP4"))
        assertEquals("clip.mp4", OutputNaming.sanitize("clip.mov", "IMG_1.MP4"))
    }

    @Test
    fun `interior dots are preserved`() {
        assertEquals("keep.dot.name.mp4", OutputNaming.sanitize("keep.dot.name", "a.mp4"))
    }

    @Test
    fun `surrounding and repeated whitespace is collapsed`() {
        assertEquals("spaced out.mp4", OutputNaming.sanitize("  spaced   out  ", "a.mp4"))
    }

    @Test
    fun `path separators and illegal characters are stripped`() {
        assertEquals("bad name here.mp4", OutputNaming.sanitize("bad/name:here*?.mkv", "a.mp4"))
        assertEquals("etc passwd.mp4", OutputNaming.sanitize("../../etc/passwd", "a.mp4"))
    }

    @Test
    fun `trailing dots and spaces are trimmed`() {
        assertEquals("trailing.mp4", OutputNaming.sanitize("trailing.  ", "a.mp4"))
    }

    @Test
    fun `blank input falls back to the source name`() {
        assertEquals("IMG_0042-compressed.mp4", OutputNaming.sanitize("", "IMG_0042.MOV"))
        assertEquals("IMG_0042-compressed.mp4", OutputNaming.sanitize("   ", "IMG_0042.MOV"))
        assertEquals("IMG_0042-compressed.mp4", OutputNaming.sanitize("...", "IMG_0042.MOV"))
    }

    @Test
    fun `long names are truncated but keep the extension`() {
        val result = OutputNaming.sanitize("a".repeat(300), "a.mp4")
        assertEquals(120, result.length)
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun `a name made only of dots still yields a usable stem`() {
        assertEquals("IMG_9-compressed.mp4", OutputNaming.sanitize(".mp4", "IMG_9.MP4"))
        assertEquals("IMG_9-compressed.mp4", OutputNaming.sanitize("mp4", "IMG_9.MP4"))
    }

    @Test
    fun `every recognised container is replaced`() {
        for (ext in listOf("mov", "mkv", "avi", "webm", "m4v", "3gp", "mpeg", "mpg", "wmv", "flv", "ts")) {
            assertEquals("clip.mp4", OutputNaming.sanitize("clip.$ext", "a.mp4"))
        }
    }

    @Test
    fun `default name drops the source extension`() {
        assertEquals("IMG_0001", OutputNaming.defaultFor("IMG_0001.MP4"))
        assertEquals("video", OutputNaming.defaultFor(""))
    }
}