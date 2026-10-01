package com.aicode.feature.agent.domain.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolOutputStoreTest {

    // 与 ToolOutputStore 的常量对齐（其 companion 为 private，测试侧用字面量）。
    private val headChars = 20_000
    private val tailChars = 20_000
    private val rescueBudgetChars = 8_000
    private val outputPath = "/tmp/tool-output.log"

    private fun preview(text: String): String =
        ToolOutputPreview.buildPreview(text, outputPath, headChars, tailChars, rescueBudgetChars)

    /** 构造一段总长超过 headChars + tailChars 的文本：头 + 中段 + 尾。 */
    private fun longText(middle: String): String =
        "H".repeat(headChars) + "\n" + middle + "\n" + "T".repeat(tailChars)

    @Test
    fun shortText_isNotTruncated() {
        val text = "short output line\n".repeat(100)
        assertTrue(text.length < headChars + tailChars)

        assertEquals(text, preview(text))
    }

    @Test
    fun longTextWithoutKeyLines_keepsHeadAndTail() {
        val text = longText("m".repeat(5_000))

        val result = preview(text)

        assertTrue(result.startsWith(text.take(headChars)))
        assertTrue(result.endsWith(text.takeLast(tailChars)))
        assertTrue(result.contains("输出过长，已省略中间"))
        assertTrue(result.contains("完整内容已保存到 $outputPath"))
        assertFalse(result.contains("以下是其中的关键行"))
    }

    @Test
    fun longTextWithErrorLine_includesItInPreview() {
        val middle = "filler\n".repeat(200) +
            "e: file.kt:123:5 error: unresolved reference: foo\n" +
            "more filler\n".repeat(200)
        val text = longText(middle)
        assertTrue(text.length > headChars + tailChars)

        val result = preview(text)

        assertTrue(result.contains("error: unresolved reference: foo"))
        assertTrue(result.contains("以下是其中的关键行"))
        assertTrue(result.contains("完整内容已保存到 $outputPath"))
    }

    @Test
    fun longTextWithoutKeyLines_fallsBackToOriginalLayout() {
        val text = longText("noise line\n".repeat(500))
        val omitted = text.length - headChars - tailChars

        val result = preview(text)

        val expected = text.take(headChars) +
            "\n\n...[输出过长，已省略中间 $omitted 个字符；完整内容已保存到 $outputPath]...\n\n" +
            text.takeLast(tailChars)
        assertEquals(expected, result)
        assertFalse(result.contains("以下是其中的关键行"))
    }
}
