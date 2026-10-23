package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Chan hai loi co the quay lai im lang khi co nguoi sua bo chan.
 *
 * Ca hai thu duoi day da tung la loi that trong app nay, khong phai lo ly
 * thuyet:
 *
 * 1. `onAccessibilityEvent` mo dau bang `val s = schedule ?: return`. May chua
 *    co noi quy thi dong do nghi het: bo chan khong doc gi nua, nen bam "gio
 *    hoc tap trung" cung khong chay. Phien hoc nam o document KHAC
 *    (`pairings/{id}`, khong phai `schedules/default_sched`) nen buoc phai
 *    duoc xet ca khi `schedule == null`.
 * 2. Bo chan tung chi lang nghe `schedules/default_sched`, khong he biet gi
 *    den cac cot `studyUntil` / `studyByName`. Viet len do ma khong doc ra thi
 *    nut van sang, van bam duoc, van bao "da gui" — may con van chay binh
 *    vong. Do la kieu hong ma chi test tren may moi phat hien duoc.
 */
class StudySessionRegressionTest {

    /** Bo comment truoc khi doan: doan vao chu giai cua chinh minh la bẫy da ngat ban 46.0. */
    private fun code(src: String): String = src
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//[^\\n]*"), "")

    private fun source(relative: String): String {
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    private val blocker = code(
        source("src/main/java/com/example/service/blocker/AppBlockerService.kt")
    )

    @Test
    fun khongDuocNghiBoChanKhiChuaCoNoiQuy() {
        assertTrue(
            "Phai cho phep tinh phien hoc khi schedule = null",
            !blocker.contains("val s = schedule ?: return")
        )
    }

    @Test
    fun boChanPhaiDocCotPhienHocTuDocumentGhepNoi() {
        assertTrue("Bo chan khong doc cot studyUntil", blocker.contains("\"studyUntil\""))
        assertTrue("Bo chan khong doc cot studyByName", blocker.contains("\"studyByName\""))
        assertTrue(
            "Phien hoc phai duoc truyen vao BlockPolicy",
            blocker.contains("study = study")
        )
    }

    @Test
    fun phienHocPhaiDuocXetTruocCanhBaoCuaNoiQuy() {
        val policy = code(
            source("src/main/java/com/example/service/blocker/BlockPolicy.kt")
        )
        val idxStudy = policy.indexOf("studyActive")
        val idxGate = policy.indexOf("schedule.isReminderOnly")
        assertTrue("BlockPolicy khong con nhanh xet phien hoc", idxStudy >= 0)
        assertTrue("BlockPolicy khong con canh bao noi quy", idxGate >= 0)
        assertTrue(
            "Phien hoc phai duoc xet TRUOC khi noi quy kieu `chi nhac nho` cho qua",
            idxStudy < idxGate
        )
    }
}
