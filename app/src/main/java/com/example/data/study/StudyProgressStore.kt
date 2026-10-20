package com.example.data.study

import android.content.Context
import java.util.Calendar

/**
 * Lightweight, dependency-free persistence for the gamified study timer.
 * Everything lives in SharedPreferences so XP, streaks and badges survive
 * app restarts without touching Firestore.
 */
data class StudyProgress(
    val xp: Int = 0,
    val totalSessions: Int = 0,
    val sessionsToday: Int = 0,
    val streakDays: Int = 0,
    val minutesToday: Int = 0,
    val minutesThisWeek: Int = 0
) {
    val level: Int get() = xp / XP_PER_LEVEL + 1
    val xpIntoLevel: Int get() = xp % XP_PER_LEVEL
    val levelProgress: Float get() = xpIntoLevel.toFloat() / XP_PER_LEVEL.toFloat()
    val xpToNextLevel: Int get() = XP_PER_LEVEL - xpIntoLevel

    val levelTitle: String
        get() = when {
            level >= 12 -> "Huyền thoại tập trung"
            level >= 9 -> "Bậc thầy tập trung"
            level >= 6 -> "Cao thủ học tập"
            level >= 3 -> "Chiến binh chăm chỉ"
            else -> "Người mới bắt đầu"
        }

    companion object {
        const val XP_PER_LEVEL = 250
    }
}

class StudyProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("study_progress_v1", Context.MODE_PRIVATE)

    fun load(): StudyProgress {
        rollDayIfNeeded()
        return read()
    }

    /**
     * Records a completed focus session and returns the updated progress.
     * Grants XP, advances the daily streak and accumulates weekly minutes.
     */
    fun recordSession(minutes: Int): StudyProgress {
        rollDayIfNeeded()
        val today = epochDay()
        val lastDay = prefs.getLong(KEY_LAST_DAY, 0L)
        var streak = prefs.getInt(KEY_STREAK, 0)
        streak = when {
            lastDay == today -> streak.coerceAtLeast(1)
            lastDay == today - 1 -> streak + 1
            else -> 1
        }

        val bonus = (minutes / 5) * 2
        prefs.edit()
            .putInt(KEY_XP, prefs.getInt(KEY_XP, 0) + XP_PER_SESSION + bonus)
            .putInt(KEY_TOTAL, prefs.getInt(KEY_TOTAL, 0) + 1)
            .putInt(KEY_TODAY, prefs.getInt(KEY_TODAY, 0) + 1)
            .putInt(KEY_MIN_TODAY, prefs.getInt(KEY_MIN_TODAY, 0) + minutes)
            .putInt(KEY_MIN_WEEK, prefs.getInt(KEY_MIN_WEEK, 0) + minutes)
            .putInt(KEY_STREAK, streak)
            .putLong(KEY_LAST_DAY, today)
            .apply()

        return read()
    }

    fun addBonusXp(amount: Int): StudyProgress {
        prefs.edit().putInt(KEY_XP, prefs.getInt(KEY_XP, 0) + amount).apply()
        return read()
    }

    private fun read() = StudyProgress(
        xp = prefs.getInt(KEY_XP, 0),
        totalSessions = prefs.getInt(KEY_TOTAL, 0),
        sessionsToday = prefs.getInt(KEY_TODAY, 0),
        streakDays = prefs.getInt(KEY_STREAK, 0),
        minutesToday = prefs.getInt(KEY_MIN_TODAY, 0),
        minutesThisWeek = prefs.getInt(KEY_MIN_WEEK, 0)
    )

    private fun rollDayIfNeeded() {
        val today = epochDay()
        val storedDay = prefs.getLong(KEY_CURRENT_DAY, today)
        if (storedDay != today) {
            // Reset the daily counters, keep XP / totals / streak history
            prefs.edit()
                .putInt(KEY_TODAY, 0)
                .putInt(KEY_MIN_TODAY, 0)
                .putInt(KEY_MIN_WEEK, 0)
                .putLong(KEY_CURRENT_DAY, today)
                .apply()
        }
    }

    private fun epochDay(): Long {
        val cal = Calendar.getInstance()
        return (cal.timeInMillis / 86_400_000L)
    }

    companion object {
        const val XP_PER_SESSION = 25
        private const val KEY_XP = "xp"
        private const val KEY_TOTAL = "total_sessions"
        private const val KEY_TODAY = "sessions_today"
        private const val KEY_MIN_TODAY = "minutes_today"
        private const val KEY_MIN_WEEK = "minutes_week"
        private const val KEY_STREAK = "streak_days"
        private const val KEY_LAST_DAY = "last_session_day"
        private const val KEY_CURRENT_DAY = "current_day"
    }
}
