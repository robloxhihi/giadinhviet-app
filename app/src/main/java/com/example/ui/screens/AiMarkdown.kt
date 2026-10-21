package com.example.ui.screens

/**
 * Markdown bac thap cho cau tra loi cua tro ly AI.
 *
 * Gemini tra ve `**in dam**` va gach dau dong `-` du da yeu cau khong dung
 * markdown trong system prompt — do la hanh vi cua model, khong phai loi du
 * lieu. Neu nem thang chuoi vao `Text` thi nguoi dung thay `**Đánh giá:**`
 * ngay tren man hinh (loc that tren BlueStacks ban 22.0). tach noi dung
 * thanh tung doan co/khong in dam de man hinh van dung font cua no, con
 * phan dinh dang thi giu nguyen.
 */
data class MdSegment(val text: String, val bold: Boolean)

object AiMarkdown {

    private val BULLET = Regex("^([ \\t]*)[-*][ \\t]+")
    private val HEADING = Regex("^[ \\t]*#{1,6}[ \\t]+")

    /** Goi dau dong ve `• ` (giu tu le), bo `##` neu model lan ra heading. */
    fun normalize(text: String): String =
        text.split("\n").joinToString("\n") { line ->
            BULLET.replace(HEADING.replace(line, "")) { m -> m.groupValues[1] + "• " }
        }

    /** Cat chuoi thanh cac doan in dam / thuong. `**` le thi giu nguyen van. */
    fun segments(text: String): List<MdSegment> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<MdSegment>()
        var i = 0
        while (i < text.length) {
            val start = text.indexOf("**", i)
            if (start < 0) {
                out.add(MdSegment(text.substring(i), false))
                break
            }
            if (start > i) out.add(MdSegment(text.substring(i, start), false))
            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                out.add(MdSegment(text.substring(start), false))
                break
            }
            out.add(MdSegment(text.substring(start + 2, end), true))
            i = end + 2
        }
        return out
    }

    /** Chuoi sach dau markdown — dung cho test va cho thong bao day ra. */
    fun plain(text: String): String = segments(normalize(text)).joinToString("") { it.text }
}
