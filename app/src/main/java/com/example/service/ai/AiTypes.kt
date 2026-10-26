package com.example.service.ai

/**
 * Mot luot noi trong doan chat. `role` dung theo ngon ngu cua API "user" cho
 * nguoi lon, "model" cho tra loi cua may — do la chuoi ma ChatHistoryStore luu
 * lai va ma OpenRouterClient doi khi doc lai doan.
 *
 * TRUOC DAY hai lop nay nam trong `GeminiClient.kt`. Ban 69.0 xoa han Gemini
 * (repo khong co khoa GEMINI_API_KEY nen tuc la mot nut chon chet, va nguoi
 * dung yeu cau "xóa cái gemini đi"), nen chung di theo — neu khong thi
 * `OpenRouterClient` phai tham chieu mot file khong con ton tai.
 */
data class ChatTurn(val role: String, val text: String)

/**
 * Loi khi mot luot goi AI that bai, kem loi da dich sang tieng Viet de man
 * hinh in thang ra duoc — nguoi dung khong bao gio phai doc ten endpoint hay
 * ma HTTP la loi gi.
 */
class AiException(message: String) : Exception(message)
