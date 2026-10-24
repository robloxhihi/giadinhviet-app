package com.example.service.blocker

import com.example.data.model.RequestStatus
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole
import com.example.data.model.windowCode
import com.example.data.model.withWindowCode
import com.example.data.model.windowDisplay

/**
 * Nội quy gia đình: phụ huynh đặt luật và luật có hiệu lực NGAY khi lưu.
 *
 * Từ bản 40.0 không còn bước "bên kia phải duyệt". Máy chặn app là chức năng
 * quản của người lớn; phiếu PENDING + nút Đồng ý/Từ chối cũ chỉ khiến phụ
 * huynh tưởng mình đã chặn trong khi máy con vẫn chạy bình thường, và con thì
 * bị hỏi ý kiến về việc bị chặn.
 *
 * File này thuần Kotlin, không chạm Firestore/Android, nên chạy được bằng
 * unit test trên máy tính (xem ConsentPolicyTest.kt).
 *
 * Giới hạn cần nói thẳng, đừng nói quá: đây là khoá mềm. Tài khoản nào đăng
 * nhập được vẫn có thể ghi thẳng vào Firestore nếu security rules còn mở —
 * phần đó nằm trong `firestore.rules`, không nằm ở đây.
 */
object ConsentPolicy {

    /** Tên gọi trong giao diện, ngắn để vừa banner. */
    fun label(role: UserRole?): String = when (role) {
        UserRole.PARENT -> "Phụ huynh"
        UserRole.CHILD -> "Con"
        null -> "Gia đình"
    }

    // ── Áp dụng ───────────────────────────────────────────────────────────

    /**
     * Ghi một bản nội quy mới đè lên luật đang chạy.
     *
     * @return `NO_CHANGE` khi bản gửi tới trùng luật đang chạy, khỏi ghi
     *         Firestore; ngược lại `APPLIED` và luật trong `schedule` có hiệu
     *         lực ngay khi xuống máy chủ.
     */
    fun propose(
        base: UsageSchedule,
        byRole: UserRole,
        blockedPackages: List<String>,
        allowedApps: List<String> = base.allowedApps,
        reminderOnly: Boolean = base.isReminderOnly,
        dailyTargetMinutes: Int = base.dailyTargetMinutes,
        /** Khung gio ma ban nay muon may chay: `""` = khong chan theo gio. */
        windowCode: String = base.windowCode(),
        now: Long = System.currentTimeMillis()
    ): ProposeResult {
        val blocked = blockedPackages.distinct()
        val allowed = allowedApps.distinct()
        val clean = normalizePending(base)

        if (matchesActive(clean, blocked, allowed, reminderOnly, dailyTargetMinutes, windowCode)) {
            return ProposeResult(ProposeOutcome.NO_CHANGE, clean)
        }

        return ProposeResult(
            ProposeOutcome.APPLIED,
            clean
                .withWindowCode(windowCode)
                .copy(
                    blockedPackages = blocked,
                    allowedApps = allowed,
                    isReminderOnly = reminderOnly,
                    dailyTargetMinutes = dailyTargetMinutes,
                    isActive = true,
                    proposedByRole = byRole,
                    changeStatus = RequestStatus.APPROVED,
                    respondedAt = now
                )
        )
    }

    /**
     * Dọn các bản ghi còn sót từ chế độ phiếu cũ: xoá hết phần "xin đổi", đặt
     * lại trạng thái về APPROVED. Máy nào đang treo một phiếu PENDING thì sau
     * lần đọc này luật đang chạy được dùng thẳng, không cần migration phía
     * server.
     */
    fun normalizePending(s: UsageSchedule): UsageSchedule {
        val stale = s.changeStatus != RequestStatus.APPROVED ||
            s.pendingBlockedPackages.isNotEmpty() ||
            s.pendingAllowedApps.isNotEmpty() ||
            s.pendingIsReminderOnly != null ||
            s.pendingDailyTargetMinutes != null ||
            s.pendingAllowedWindow.isNotEmpty()
        if (!stale) return s
        return s.copy(
            changeStatus = RequestStatus.APPROVED,
            pendingBlockedPackages = emptyList(),
            pendingAllowedApps = emptyList(),
            pendingIsReminderOnly = null,
            pendingDailyTargetMinutes = null,
            pendingAllowedWindow = ""
        )
    }

    /**
     * Đọc lại hai khoá Boolean của nội quy theo **cả hai kiểu tên** từng tồn
     * tại trên máy chủ.
     *
     * Vì sao cần: `val isReminderOnly` sinh getter `isReminderOnly()`, và bộ
     * ánh xạ của Firestore cắt chữ "is" nên tai lieu cu duoc ghi voi khoa
     * `reminderOnly` / `active` — con model tu ban 58.0 khai bao ten day du
     * `isReminderOnly` / `isActive`. Neu chi tin vao `toObject` thi tai lieu
     * cua gia dinh luu tu truoc van con kiem cu, doc ra se bi mac dinh lap
     * day (`isActive = false`, `isReminderOnly = true`), tuc la **may con
     * ngung chan app im lang** trong khi ba me tuong luat dang chay. Do chinh
     * la loi nguy hiem nhat cua app nen buoc doc nay khong duoc dua vao may
     * moi: khoa moi ton tai thi dung no, khong moi lay khoa cu.
     *
     * @param raw toa do nguyen ven cua document (`snapshot.data`), null khi
     *            tai lieu khong ton tai.
     */
    fun healLegacyKeys(s: UsageSchedule, raw: Map<String, Any?>?): UsageSchedule {
        if (raw.isNullOrEmpty()) return s
        val active = if (raw.containsKey("isActive")) raw["isActive"] as? Boolean
        else raw["active"] as? Boolean
        val reminder = if (raw.containsKey("isReminderOnly")) raw["isReminderOnly"] as? Boolean
        else raw["reminderOnly"] as? Boolean
        if (active == null && reminder == null) return s
        return s.copy(
            isActive = active ?: s.isActive,
            isReminderOnly = reminder ?: s.isReminderOnly
        )
    }

    /** Mô tả luật đang chạy — dùng cho banner đã lưu và cho nhật ký nội quy. */
    fun describe(s: UsageSchedule): String {
        val blocked = s.blockedPackages
        val parts = buildList {
            add(
                when {
                    blocked.isEmpty() && s.isReminderOnly -> "không chặn app nào, chỉ nhắc"
                    blocked.isEmpty() -> "không chặn app nào"
                    s.isReminderOnly -> "nhắc nhở ${blocked.size} app (chưa chặn thật)"
                    else -> "chặn thật ${blocked.size} app"
                }
            )
            add("${s.dailyTargetMinutes} phút/ngày")
            // Khung gio duoc noi ra moi lan vi "khong chan theo gio" la cai ma
            // gia dinh hay hieu nham thanh "bo chan da chay".
            add("khung giờ: ${windowDisplay(s.windowCode()) ?: "chưa đặt"}")
        }
        return parts.joinToString(" • ")
    }

    private fun matchesActive(
        s: UsageSchedule, blocked: List<String>, allowed: List<String>,
        reminderOnly: Boolean, minutes: Int, windowCode: String
    ) = s.blockedPackages.toSet() == blocked.toSet() &&
        s.allowedApps.toSet() == allowed.toSet() &&
        s.isReminderOnly == reminderOnly &&
        s.dailyTargetMinutes == minutes &&
        s.windowCode() == windowCode
}

enum class ProposeOutcome {
    /** Không có gì khác luật đang chạy, khỏi ghi Firestore. */
    NO_CHANGE,

    /** Luật mới đã được ghi nhận và có hiệu lực ngay. */
    APPLIED
}

data class ProposeResult(val outcome: ProposeOutcome, val schedule: UsageSchedule)
