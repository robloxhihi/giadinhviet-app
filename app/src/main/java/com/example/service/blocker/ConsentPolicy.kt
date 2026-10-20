package com.example.service.blocker

import com.example.data.model.RequestStatus
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole

/**
 * Phiếu đồng thuận của nội quy gia đình: không ai được một mình đổi luật.
 *
 * Nguyên tắc: mẹ là người đặt luật, nhưng mẹ không được tự thông qua. Một
 * thay đổi (chặn thêm app, bỏ chặn, đổi hạn mức phút, chuyển "chỉ nhắc"
 * sang "chặn thật") chỉ có hiệu lực khi bên kia bấm Đồng ý. Trong lúc chờ
 * duyệt, luật cũ vẫn áp dụng như cũ — đó là lý do phần "xin đổi" nằm ở
 * trường riêng thay vì ghi đè lên `blockedPackages`:
 * - đề xuất không làm mất hiệu lực của luật cũ (không có khoảng trống
 *   "máy không chặn gì cả" trong lúc chờ duyệt);
 * - nếu bị từ chối thì luật cũ vẫn nguyên, không phải hoàn tro.
 *
 * File này thuần Kotlin, không chạm Firestore/Android, nên chạy được bằng
 * unit test trên máy tính (xem ConsentPolicyTest.kt).
 *
 * Giới hạn cần nói thẳng, đừng nói quá: đây là khóa mềm có người duyệt,
 * không phải ràng buộc kỹ thuật. Tài khoản nào đăng nhập được vẫn có thể
 * ghi thẳng vào Firestore nếu security rules còn mở — phần đó nằm trong
 * `firestore.rules`, không nằm ở đây.
 */
object ConsentPolicy {

    /** Tên gọi trong giao diện, ngắn để vừa banner. */
    fun label(role: UserRole?): String = when (role) {
        UserRole.PARENT -> "Mẹ"
        UserRole.CHILD -> "Con"
        null -> "Gia đình"
    }

    fun opposite(role: UserRole): UserRole =
        if (role == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT

    // ── Đề xuất ───────────────────────────────────────────────────────────

    /**
     * Một bên xin đổi nội quy.
     *
     * @param hasCounterparty false khi chưa ghép nối -> áp dụng thẳng vì
     *        không có ai để hỏi (app vẫn dùng được trên 1 máy khi demo).
     */
    fun propose(
        base: UsageSchedule,
        byRole: UserRole,
        hasCounterparty: Boolean,
        blockedPackages: List<String>,
        allowedApps: List<String> = base.allowedApps,
        reminderOnly: Boolean = base.isReminderOnly,
        dailyTargetMinutes: Int = base.dailyTargetMinutes,
        now: Long = System.currentTimeMillis()
    ): ProposeResult {
        val blocked = blockedPackages.distinct()
        val allowed = allowedApps.distinct()
        val pending = base.changeStatus == RequestStatus.PENDING

        if (pending && matchesPending(base, blocked, allowed, reminderOnly, dailyTargetMinutes)) {
            return ProposeResult(ProposeOutcome.NO_CHANGE, base)
        }

        if (matchesActive(base, blocked, allowed, reminderOnly, dailyTargetMinutes)) {
            // Đề xuất trùng với luật đang chạy: bản chất là rút phiếu đang mở.
            return if (pending) {
                ProposeResult(ProposeOutcome.WITHDRAWN, cleared(base, byRole, now))
            } else {
                ProposeResult(ProposeOutcome.NO_CHANGE, base)
            }
        }

        if (!hasCounterparty) {
            return ProposeResult(
                ProposeOutcome.APPLIED,
                cleared(base, byRole, now).copy(
                    blockedPackages = blocked,
                    allowedApps = allowed,
                    isReminderOnly = reminderOnly,
                    dailyTargetMinutes = dailyTargetMinutes,
                    isActive = true,
                    changeStatus = RequestStatus.APPROVED
                )
            )
        }

        return ProposeResult(
            ProposeOutcome.PENDING,
            base.copy(
                changeStatus = RequestStatus.PENDING,
                proposedByRole = byRole,
                pendingBlockedPackages = blocked,
                pendingAllowedApps = allowed,
                pendingIsReminderOnly = reminderOnly,
                pendingDailyTargetMinutes = dailyTargetMinutes,
                proposedAt = now,
                respondedAt = 0L
            )
        )
    }

    // ── Duyệt ─────────────────────────────────────────────────────────────

    /**
     * Ben kia tra loi. Chinh ben da de xuat khong duoc tu thong qua phieu
     * cua minh — do chinh la cai "dong long" ma ban thi can chung minh.
     */
    fun respond(
        base: UsageSchedule,
        byRole: UserRole,
        accept: Boolean,
        now: Long = System.currentTimeMillis()
    ): RespondResult {
        if (base.changeStatus != RequestStatus.PENDING) {
            return RespondResult(RespondOutcome.NOTHING_PENDING, base)
        }
        if (base.proposedByRole == byRole) {
            return RespondResult(RespondOutcome.WRONG_SIDE, base)
        }

        val kept = cleared(base, base.proposedByRole ?: byRole, now)

        return if (accept) {
            RespondResult(
                RespondOutcome.APPROVED,
                kept.copy(
                    blockedPackages = base.pendingBlockedPackages,
                    allowedApps = base.pendingAllowedApps,
                    isReminderOnly = base.pendingIsReminderOnly ?: base.isReminderOnly,
                    dailyTargetMinutes = base.pendingDailyTargetMinutes
                        ?: base.dailyTargetMinutes,
                    isActive = true,
                    changeStatus = RequestStatus.APPROVED
                )
            )
        } else {
            RespondResult(
                RespondOutcome.REJECTED,
                kept.copy(changeStatus = RequestStatus.REJECTED)
            )
        }
    }

    /** Chỉ bên đề xuất mới rút lại được phiếu của mình. */
    fun withdraw(
        base: UsageSchedule,
        byRole: UserRole,
        now: Long = System.currentTimeMillis()
    ): RespondResult {
        if (base.changeStatus != RequestStatus.PENDING) {
            return RespondResult(RespondOutcome.NOTHING_PENDING, base)
        }
        if (base.proposedByRole != byRole) {
            return RespondResult(RespondOutcome.WRONG_SIDE, base)
        }
        return RespondResult(
            RespondOutcome.APPROVED,
            cleared(base, byRole, now).copy(changeStatus = RequestStatus.APPROVED)
        )
    }

    // ── Trạng thái cho giao diện ──────────────────────────────────────────

    fun hasPendingChange(schedule: UsageSchedule?): Boolean =
        schedule?.changeStatus == RequestStatus.PENDING

    /** Bên này có được bấm Đồng ý / Từ chối không. */
    fun canVote(schedule: UsageSchedule?, myRole: UserRole?): Boolean =
        schedule != null &&
            schedule.changeStatus == RequestStatus.PENDING &&
            myRole != null &&
            schedule.proposedByRole != myRole

    /** Bên này có được bấm "Rút đề xuất" không. */
    fun canWithdraw(schedule: UsageSchedule?, myRole: UserRole?): Boolean =
        schedule != null &&
            schedule.changeStatus == RequestStatus.PENDING &&
            myRole != null &&
            schedule.proposedByRole == myRole

    /**
     * Dòng chữ cho banner. Tra null khi trạng thái bình thường (APPROVED,
     * không phiếu nào đang mở) để giao diện không hiện chữ rải rác.
     */
    fun summary(schedule: UsageSchedule?, myRole: UserRole?): String? {
        val s = schedule ?: return null
        return when (s.changeStatus) {
            RequestStatus.PENDING ->
                "${label(s.proposedByRole)} đề xuất: ${describe(s, pending = true)} — " +
                    (if (canVote(s, myRole)) "chờ bạn duyệt" else "chờ ${label(voterOf(s))} duyệt")

            RequestStatus.REJECTED ->
                "${label(voterOf(s))} đã từ chối đề xuất thay đổi nội quy. " +
                    "Luật hiện tại vẫn giữ nguyên."

            RequestStatus.APPROVED -> null
        }
    }

    /** Ai là người có quyền duyệt phiếu đang mở. */
    fun voterOf(schedule: UsageSchedule): UserRole? =
        schedule.proposedByRole?.let { opposite(it) }

    /** Mô tả nội dung thay đổi — dùng cho cả banner duyệt và nhật ký. */
    fun describe(s: UsageSchedule, pending: Boolean): String {
        val blocked = if (pending) s.pendingBlockedPackages else s.blockedPackages
        val reminder = if (pending) s.pendingIsReminderOnly ?: s.isReminderOnly else s.isReminderOnly
        val minutes = if (pending) s.pendingDailyTargetMinutes ?: s.dailyTargetMinutes else s.dailyTargetMinutes

        val parts = buildList {
            add(
                when {
                    blocked.isEmpty() && reminder -> "không chặn app nào, chỉ nhắc"
                    blocked.isEmpty() -> "không chặn app nào"
                    reminder -> "nhắc nhở ${blocked.size} app (chưa chặn thật)"
                    else -> "chặn thật ${blocked.size} app"
                }
            )
            if (pending && s.pendingDailyTargetMinutes != null &&
                s.pendingDailyTargetMinutes != s.dailyTargetMinutes
            ) {
                add("hạn mức ${s.dailyTargetMinutes} → ${s.pendingDailyTargetMinutes} phút/ngày")
            } else {
                add("$minutes phút/ngày")
            }
            if (pending && s.pendingIsReminderOnly != null &&
                s.pendingIsReminderOnly != s.isReminderOnly
            ) {
                add(if (s.pendingIsReminderOnly == true) "chuyển về chỉ nhắc" else "chuyển sang chặn thật")
            }
        }
        return parts.joinToString(" • ")
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun matchesActive(
        s: UsageSchedule, blocked: List<String>, allowed: List<String>,
        reminderOnly: Boolean, minutes: Int
    ) = s.blockedPackages.toSet() == blocked.toSet() &&
        s.allowedApps.toSet() == allowed.toSet() &&
        s.isReminderOnly == reminderOnly &&
        s.dailyTargetMinutes == minutes

    private fun matchesPending(
        s: UsageSchedule, blocked: List<String>, allowed: List<String>,
        reminderOnly: Boolean, minutes: Int
    ) = s.pendingBlockedPackages.toSet() == blocked.toSet() &&
        s.pendingAllowedApps.toSet() == allowed.toSet() &&
        s.pendingIsReminderOnly == reminderOnly &&
        s.pendingDailyTargetMinutes == minutes

    /** Xóa hết phần xin đổi, giữ nguyên phần luật đang chạy, dong phieu lai. */
    private fun cleared(s: UsageSchedule, actorRole: UserRole?, now: Long) = s.copy(
        changeStatus = RequestStatus.APPROVED,
        proposedByRole = actorRole,
        pendingBlockedPackages = emptyList(),
        pendingAllowedApps = emptyList(),
        pendingIsReminderOnly = null,
        pendingDailyTargetMinutes = null,
        respondedAt = now
    )
}

enum class ProposeOutcome {
    /** Không có gì khác, khỏi ghi Firestore. */
    NO_CHANGE,

    /** Đã lưu vào hàng chờ, bên kia cần duyệt. */
    PENDING,

    /** Không có bên kia (chưa ghép nối) -> áp dụng thẳng. */
    APPLIED,

    /** Đề xuất trùng luật đang chạy -> phiếu đang mở bị gỡ. */
    WITHDRAWN
}

data class ProposeResult(val outcome: ProposeOutcome, val schedule: UsageSchedule)

enum class RespondOutcome {
    APPROVED,
    REJECTED,

    /** Không có phiếu nào đang mở để duyệt. */
    NOTHING_PENDING,

    /** Duyệt sai bên: bên đề xuất không tự thông qua phiếu của mình. */
    WRONG_SIDE
}

data class RespondResult(val outcome: RespondOutcome, val schedule: UsageSchedule)
