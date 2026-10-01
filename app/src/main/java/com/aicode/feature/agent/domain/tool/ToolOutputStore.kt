package com.aicode.feature.agent.domain.tool

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.ContainerInstaller
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class StoredToolOutput(
    val preview: String,
    val truncated: Boolean,
    val totalChars: Long,
    val outputPath: String? = null,
    val storageError: String? = null
)

/**
 * 工具输出的纯文本处理逻辑：与文件系统无关，便于单元测试直接调用。
 */
internal object ToolOutputPreview {
    /** 命中即视为「关键行」的样式：错误/异常/警告关键词、Java/Kotlin 堆栈行、kotlinc 的 e:/w: 前缀。 */
    private val KEY_LINE_PATTERNS = listOf(
        Regex("(?i)\\b(error|exception|failed|failure|fatal|panic|warning|warn|caused by|traceback)\\b"),
        Regex("^\\s+at\\s+[\\w.$]+\\("),
        Regex("^[ew]:\\s"),
        Regex("^\\s*Caused by:")
    )

    /**
     * 从被丢弃的中间区域 `[startIndex, endIndex)` 中抢救关键行，每命中一行连带前后各 1 行上下文。
     * 按原始顺序拼接，同一内容只保留一次，累计超过 [budgetChars] 即停止；无任何命中时返回空串。
     */
    fun extractKeyLines(text: String, startIndex: Int, endIndex: Int, budgetChars: Int): String {
        if (budgetChars <= 0) return ""
        val start = startIndex.coerceIn(0, text.length)
        val end = endIndex.coerceIn(start, text.length)
        if (start >= end) return ""
        val lines = text.substring(start, end).split('\n')

        // 先按原始下标收集「关键行 + 前后各 1 行」，sortedSet 保证遍历顺序即原始顺序。
        val keep = sortedSetOf<Int>()
        lines.forEachIndexed { index, line ->
            if (KEY_LINE_PATTERNS.any { it.containsMatchIn(line) }) {
                if (index > 0) keep.add(index - 1)
                keep.add(index)
                if (index < lines.lastIndex) keep.add(index + 1)
            }
        }
        if (keep.isEmpty()) return ""

        val seen = HashSet<String>()
        val result = StringBuilder()
        for (index in keep) {
            val line = lines[index]
            if (!seen.add(line)) continue
            val separator = if (result.isEmpty()) 0 else 1
            if (result.length + separator + line.length > budgetChars) break
            if (separator == 1) result.append('\n')
            result.append(line)
        }
        return result.toString()
    }

    /**
     * 组装超长输出的 preview：头 [headChars] + 省略提示 + 尾部 [tailChars]。
     * 被省略的中段若抢救出关键行，则额外插入一段「关键行」，否则退回原「头 + 省略提示 + 尾」结构。
     * [text] 未超过 `headChars + tailChars` 时原样返回（与 [ToolOutputStore.boundText] 的阈值一致）。
     */
    fun buildPreview(
        text: String,
        outputPath: String?,
        headChars: Int,
        tailChars: Int,
        rescueBudgetChars: Int
    ): String {
        if (text.length <= headChars + tailChars) return text
        val omitted = text.length - headChars - tailChars
        val storageHint = if (outputPath != null) {
            "完整内容已保存到 $outputPath"
        } else {
            "完整内容保存失败"
        }
        val keyLines = extractKeyLines(text, headChars, text.length - tailChars, rescueBudgetChars)
        return buildString {
            append(text.take(headChars))
            if (keyLines.isNotEmpty()) {
                append("\n\n...[输出过长，已省略中间 ")
                append(omitted)
                append(" 个字符；以下是其中的关键行]...\n\n")
                append(keyLines)
                append("\n\n...[")
                append(storageHint)
                append("]...\n\n")
            } else {
                append("\n\n...[输出过长，已省略中间 ")
                append(omitted)
                append(" 个字符；")
                append(storageHint)
                append("]...\n\n")
            }
            append(text.takeLast(tailChars))
        }
    }
}

@Singleton
class ToolOutputStore @Inject constructor(
    private val containerInstaller: ContainerInstaller
) {
    private companion object {
        const val TAG = "ToolOutputStore"
        const val AICODE_ROOT = "/root/.aicode"
        const val OUTPUT_DIR = "tool-output"
        const val HEAD_CHARS = 20_000
        const val TAIL_CHARS = 20_000
        const val MAX_INLINE_CHARS = HEAD_CHARS + TAIL_CHARS
        const val RESCUE_BUDGET_CHARS = 8_000
        val LARGE_TEXT_FIELDS = listOf("output", "content", "text", "stdout", "stderr", "body", "result")
        val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }

    private val json = Json { encodeDefaults = true }

    /** 存档目录（宿主路径）。对外只用于占用统计与清理，写入仍走本类。 */
    val outputDir: File get() = File(containerInstaller.aicodeDir, OUTPUT_DIR)

    fun process(toolName: String, callId: String, result: ToolResult): ToolResult {
        return when (result) {
            is ToolResult.Success -> ToolResult.Success(processElement(toolName, callId, result.data))
            is ToolResult.Partial -> ToolResult.Partial(processElement(toolName, callId, result.data), result.message)
            is ToolResult.Error -> result
        }
    }

    fun boundText(toolName: String, callId: String, text: String): StoredToolOutput {
        if (text.length <= MAX_INLINE_CHARS) {
            return StoredToolOutput(
                preview = text,
                truncated = false,
                totalChars = text.length.toLong()
            )
        }

        val writeResult = writeFullOutput(toolName, callId, text)
        val preview = buildPreview(text, writeResult.outputPath)
        return StoredToolOutput(
            preview = preview,
            truncated = true,
            totalChars = text.length.toLong(),
            outputPath = writeResult.outputPath,
            storageError = writeResult.storageError
        )
    }

    private fun processElement(toolName: String, callId: String, element: JsonElement): JsonElement {
        val primitive = element as? JsonPrimitive
        if (primitive?.isString == true) {
            val stored = boundText(toolName, callId, primitive.content)
            return if (stored.truncated) stored.toJsonObject("output") else element
        }

        val obj = element as? JsonObject
        if (obj != null) {
            val largeField = LARGE_TEXT_FIELDS
                .mapNotNull { key ->
                    val field = obj[key] as? JsonPrimitive
                    val value = if (field?.isString == true) field.content else null
                    if (value != null && value.length > MAX_INLINE_CHARS) key to value else null
                }
                .maxByOrNull { it.second.length }

            if (largeField != null) {
                val stored = boundText(toolName, callId, largeField.second)
                val updated = obj.toMutableMap()
                updated[largeField.first] = JsonPrimitive(stored.preview)
                addMetadata(updated, stored)
                return JsonObject(updated)
            }
        }

        val serialized = json.encodeToString(element)
        if (serialized.length <= MAX_INLINE_CHARS) return element

        val stored = boundText(toolName, callId, serialized)
        return stored.toJsonObject("content")
    }

    private fun addMetadata(target: MutableMap<String, JsonElement>, stored: StoredToolOutput) {
        target["output_truncated"] = JsonPrimitive(stored.truncated)
        target["output_total_chars"] = JsonPrimitive(stored.totalChars)
        stored.outputPath?.let { target["output_path"] = JsonPrimitive(it) }
        stored.storageError?.let { target["output_storage_error"] = JsonPrimitive(it) }
    }

    private fun StoredToolOutput.toJsonObject(primaryField: String): JsonObject {
        val data = mutableMapOf<String, JsonElement>(
            primaryField to JsonPrimitive(preview),
            "output_truncated" to JsonPrimitive(truncated),
            "output_total_chars" to JsonPrimitive(totalChars)
        )
        outputPath?.let { data["output_path"] = JsonPrimitive(it) }
        storageError?.let { data["output_storage_error"] = JsonPrimitive(it) }
        return JsonObject(data)
    }

    private fun buildPreview(text: String, outputPath: String?): String =
        ToolOutputPreview.buildPreview(text, outputPath, HEAD_CHARS, TAIL_CHARS, RESCUE_BUDGET_CHARS)

    private fun writeFullOutput(toolName: String, callId: String, text: String): StoredPathResult {
        return try {
            val dir = outputDir.apply { mkdirs() }
            val file = uniqueOutputFile(dir, toolName, callId)
            file.writeText(text, Charsets.UTF_8)
            val path = "$AICODE_ROOT/$OUTPUT_DIR/${file.name}"
            FileLogger.i(TAG, "工具输出已保存: $path (${text.length} chars)")
            StoredPathResult(outputPath = path)
        } catch (e: Exception) {
            FileLogger.w(TAG, "保存工具输出失败: ${e.message}", e)
            StoredPathResult(storageError = e.message ?: "保存工具输出失败")
        }
    }

    private fun uniqueOutputFile(dir: File, toolName: String, callId: String): File {
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
        val baseName = buildString {
            append(timestamp)
            append('-')
            append(sanitize(toolName).ifBlank { "tool" })
            val id = sanitize(callId).take(12)
            if (id.isNotBlank()) {
                append('-')
                append(id)
            }
        }

        var candidate = File(dir, "$baseName.log")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName-$index.log")
            index++
        }
        return candidate
    }

    private fun sanitize(value: String): String {
        return value.map { ch ->
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '-'
        }.joinToString("").trim('-')
    }

    private data class StoredPathResult(
        val outputPath: String? = null,
        val storageError: String? = null
    )
}
