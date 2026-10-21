package com.example.service.ai

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Doan chat nam tren MAY, khong len Firestore.
 *
 * Gioi han so luot vai tro quan trong hon ve dung luong: Gemini tinh tien theo
 * token dau vao, ma lich su chat tang theo cap so doi. Giu 12 luot gan nhat
 * (6 hoi + 6 dap) du de AI nho mach hoi truoc do, ma khong keo dai prompt vo
 * han. Nut "Xoan noi dung" trong man hinh xoa sach, khong phai an.
 */
class ChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<List<ChatTurn>>(
        Types.newParameterizedType(List::class.java, ChatTurn::class.java)
    )

    fun load(): List<ChatTurn> =
        prefs.getString(KEY_TURNS, null)?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: emptyList()

    /** Ghi toan bo doan thoai, cat bom giu MAX_TURNS luot cuoi. */
    fun save(turns: List<ChatTurn>) {
        val trimmed = if (turns.size <= MAX_TURNS) turns else turns.takeLast(MAX_TURNS)
        prefs.edit().putString(KEY_TURNS, adapter.toJson(trimmed)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TURNS).apply()
    }

    companion object {
        private const val PREFS = "ai_config_v1"
        private const val KEY_TURNS = "chat_turns"
        private const val MAX_TURNS = 12
    }
}
