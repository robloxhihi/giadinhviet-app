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

    // ── Khoi noi dung ────────────────────────────────────────────────────────

    /**
     * Tao doan van cua tro ly. `**in dam**` duoc tach thanh `label` rieng de
     * man hinh co nhan/dep, khong phai nem ca bai van mot mau trong mot bong
     * bong rong 320dp (do nguoi dung phan nan o ban 26.0: "noi dung duoc nhung
     * bo cuc khong doc noi").
     */
    enum class Kind { STEP, BULLET, PARAGRAPH }

    data class Block(
        val kind: Kind,
        /** "1", "2"… voi STEP; rong voi loai khac. */
        val marker: String,
        /** Doan dam dau dong, da bo `**` va dau `:` — rong neu khong co. */
        val label: String,
        /** Phan con lai, GIU NGUYEN `**` de to inline neu model dam giua cau. */
        val body: String
    )

    private val STEP_LINE = Regex("^[ \\t]*([0-9]{1,2})[.)][ \\t]+(.*)$")
    private val BULLET_LINE = Regex("^[ \\t]*[•\\-*][ \\t]+(.*)$")
    private val LEAD_BOLD = Regex("^[ \\t]*\\*\\*(.+?)\\*\\*[ \\t]*[:\\-–—]?[ \\t]*(.*)$")

    /**
     * Model khong bao gio dam dong nhu minh hen. Khi do, dau `:` dau tien cua
     * mot buoc/gach chinh la ngan giua nhan va noi dung — ap dung RIENG cho
     * STEP/BULLET de doan van thuong khong bi cat doi.
     */
    private val COLON_LEAD = Regex("^([^:.!?]{3,48}):[ \\t]+(.*)$")

    /** Cat cau tra loi thanh tung khoai doc duoc: buoc danh so, gach dau dong, doan van. */
    fun blocks(text: String): List<Block> {
        val out = ArrayList<Block>()
        for (raw in normalize(text).split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val stepMatch = STEP_LINE.matchEntire(line)
            val bulletMatch = if (stepMatch == null) BULLET_LINE.matchEntire(line) else null
            val listed = stepMatch != null || bulletMatch != null
            val rest = (stepMatch?.groupValues?.get(2)
                ?: bulletMatch?.groupValues?.get(1)
                ?: line)
            val lead = LEAD_BOLD.matchEntire(rest)
            val colon = if (lead == null && listed) COLON_LEAD.matchEntire(rest) else null
            out.add(
                Block(
                    kind = when {
                        stepMatch != null -> Kind.STEP
                        bulletMatch != null -> Kind.BULLET
                        else -> Kind.PARAGRAPH
                    },
                    marker = stepMatch?.groupValues?.get(1).orEmpty(),
                    label = (lead?.groupValues?.get(1) ?: colon?.groupValues?.get(1))
                        ?.trim()?.removeSuffix(":")?.trim().orEmpty(),
                    body = (lead?.groupValues?.get(2) ?: colon?.groupValues?.get(2) ?: rest).trim()
                )
            )
        }
        return out
    }
}
