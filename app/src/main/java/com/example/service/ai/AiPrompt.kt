package com.example.service.ai

/**
 * Cau lenh he chung cho MOI model trong tro ly gia dinh.
 *
 * Nam riêng một file vì khuôn này là hop dong giua app va moi model: no tung
 * nam trong `GeminiClient`, va khi them Orcarouter thi model moi am tham tra
 * loi theo kieu khac. 69.0 xoa Gemini nhung giu file nay — noi dung van phai
 * di qua mot duong duy nhat de moi lan doi model khong doi luong.
 *
 * Hai che do, vi nguoi dung phan nan ngay tren may (2026-10-22): truoc day MOI
 * tin nhan deu bi ep theo khuon bao cao "Nguyên nhân / Giải pháp / Lời khuyên",
 * nen mot cau chao hoi cung ra mot ban phan tich dai dong — "nó cứ lải nhải mãi".
 * Khuon do chi dung khi nguoi dung XIN phan tich.
 *
 * Lan do lai ngay 2026-10-23, cung tren may that ("cho ai trả lời vô tư như bình
 * thường đi, chứ nó gò bó quá"): che do chat van con gò. Ba thu ngan model noi
 * chuyen binh thuong, ca ba deu do chinh prompt nay gay ra —
 *   1) pham vi tra loi bi ghan vao "chi so lieu duoc cung cap" nen model tu choi
 *      ca cau hoi khong lien quan den so lieu, chi quanh co hoi lai;
 *   2) tran do dai cung lenh cam liet ke khien khong the giai thich du;
 *   3) "tự nhiên, tôn trọng" hop voi viec cam chao hoi thua => model thanh kieu
 *      le phep qua muc, mo dau "Dạ" va ket "ạ" moi cau, xin loi ca khi chua lam
 *      gi sai.
 * Cam doan khong bịa số liệu GIỮ NGUYÊN tuyệt đối — chi thu hep no ve dung
 * pham vi: so lieu cua may thi phai lay tu may, con lai duoc phep tra loi.
 *
 * Lan ba ngay 2026-10-26 ("ai trả lời tự do như ai bình thường, không phải đọc j
 * hết, quá hạn chế và chỉ đọc dữ liệu, phân tích khi yêu cầu"): file nay tung
 * LUON LUON dam so lieu thiet bi vao prompt, nen moi luot hoi deu co nguy co
 * model doc lai ca bang so lieu — du nguoi dung chi hoi mot chuyen rat thuong.
 * Tu day so lieu chi di theo khi that su duoc xin: `report` (ban phan tich) hoac
 * khi cau hoi cua nguoi dung nhac den so lieu, qua `wantsDeviceData`. Khong con
 * dong "Chi duoc tra loi tu so lieu duoc cung cap" nao nua; quy tac an toàn
 * (khong chan doan benh, khong theo doi bi mat) giu nguyen vi do la ranh gioi
 * dao duc cua chinh du an, khong phai loi han che.
 */
object AiPrompt {

    /**
     * Nhung tu cho thay nguoi dung dang hoi ve so lieu cua may, dung cho man
     * hinh AI quyet dinh co truyen `summaryForAi()` len model hay khong. Danh
     * sach co tinh quy uoc: "thoi gian dung may" la mot cau khuyen, khong phai
     * mot yeu cau doc so lieu, nen no KHONG co mat o day — neu cho no vao thi
     * moi luot hoi chuyen nan deu bi ep doc lai bang thong ke, dung cai nguoi
     * dung phan nan.
     */
    private val DATA_WORDS = listOf(
        "số liệu", "dữ liệu", "thống kê", "báo cáo", "phân tích",
        "giải pháp", "hôm nay", "hôm qua", "tuần này", "tháng này",
        "bao nhiêu phút", "bao nhiêu tiếng", "mấy phút", "mấy tiếng",
        "lịch sử", "vị trí", "định vị", "bị chặn", "chặn app", "nhiều nhất"
    )

    fun wantsDeviceData(question: String): Boolean {
        val q = question.lowercase()
        return DATA_WORDS.any { q.contains(it) }
    }

    /**
     * `stats` rong co nghia la luot nay khong lien quan den so lieu thiet bi,
     * va model khong nhin thay con so nao cua may het — dung nhu nguoi dung yeu
     * cau. Khuon bao cao chi bat khi `report` = true.
     */
    fun system(stats: String, report: Boolean = false): String = buildString {
        append(
            "Bạn là trợ lý gia đình trong ứng dụng Gia Đình Việt. Người hỏi là cha mẹ hoặc " +
                "người giám hộ. Nói tiếng Việt tự nhiên như người thật nhắn tin.\n" +
                "Bạn là một trợ lý AI bình thường: cứ trả lời thẳng và đủ ý — giải thích, lên ý " +
                "tưởng, viết giúp tin nhắn, chia sẻ cách ứng xử với con, trả lời thắc mắc chung. " +
                "Đừng từ chối, đừng vòng vo xin thêm thông tin khi câu hỏi đã đủ để trả lời.\n" +
                "Số liệu thiết bị: mọi con số về máy của con (phút dùng, lần bị chặn, vị trí, cảm " +
                "xúc con ghi) chỉ được lấy từ phần SỐ LIỆU ở cuối, nếu phần đó có mặt; không có " +
                "thì không nói con số nào về máy cả, và nếu bị hỏi mà app chưa kịp đo thì " +
                "nói rõ là chưa có — tuyệt đối không bịa, không ước lượng.\n" +
                "An toàn: không chẩn đoán bệnh, không kết luận con hư hay sai, không gợi ý theo " +
                "dõi bí mật hay đọc lén tin nhắn của con.\n"
        )
        if (report) {
            append(
                "Nguoi dung dang YEU CAU phan tich. Nhiem vu: chi ra nguyen nhan co kha nang nhat, " +
                    "roi dua GIẢI PHÁP cụ thể được đánh số (3-5 bước, làm được trong tuần này) " +
                    "va LỜI KHUYÊN ngắn cho cha mẹ khi nói chuyện với con.\n" +
                    "TRÌNH BÀY (màn hình chỉ có chỗ cho văn bản ngắn, làm đúng khuôn này):\n" +
                    "- Trả lời dưới 200 từ, chia đúng 3 khối theo thứ tự: **Nguyên nhân:**, **Giải pháp:**, " +
                    "**Lời khuyên cho cha mẹ:**.\n" +
                    "- Sau mỗi nhãn là xuống dòng. Mỗi ý nằm trọn trong MỘT dòng, không viết hai ý chung một dòng.\n" +
                    "- Khối Giải pháp đánh số `1. **Việc cần làm:** mô tả cụ thể trong 1-2 câu.` mỗi bước một dòng.\n" +
                    "- Không heading `#`, không bảng, không emoji, không lặp lại nguyên văn số liệu.\n"
            )
        } else {
            append(
                "Day la chuyen thoai thuong, KHONG phai yeu cau phan tich. Tra loi nhu mot nguoi " +
                    "binh thuong, tu do theo do dai ma cau hoi can: cau don gian thi ngan, can " +
                    "giai thich thi dai hon, duoc phep liet ke va neu vi du khi that su giup de " +
                    "hieu. Khong ep minh vao bat ky khuon nao, khong dung ba khoi bao cao, khong " +
                    "lap lai khuon mau, khong tu nhien doc lai so lieu thiet bi. Khong mo dau moi " +
                    "cau bang \"Dạ\" cung khong ket bang \"ạ\", khong chao hoi thua, khong xin loi " +
                    "khi chua lam gi sai. Vao thang noi dung.\n"
            )
        }
        if (stats.isNotBlank()) {
            append("SỐ LIỆU THIẾT BỊ (app đo trực tiếp, không phải ước lượng):\n").append(stats)
        }
    }
}
