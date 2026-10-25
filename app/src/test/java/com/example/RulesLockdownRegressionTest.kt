package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test cho lo "khoa 5 khe ho" (ban 65.0, 2026-10-25).
 *
 * `firestore.rules` trong repo KHONG duoc CI deploy — file nay chi la bang
 * chung, con rules chay that tren production la thu nguoi dung bam "Publish"
 * trong Firebase Console. Vi vay day la lop phong duy nhat: no giu de khong ai
 * quay lai duoc `allow read, write: if isAuthenticated()`, va giu cho nhung
 * cho THAT SU can mo (phieu "gio trong quyen rieng" phu huynh duyet, query
 * `location_history` cua chinh may) van mo dung mot cho du — khong mo hon.
 *
 * Sau dieu uoc duot nhat cung bi bat bang loi, vi moi cai tung la mot loi thiet
 * ke da phai tra bang nuoc mat trong du an nay:
 *  1. Co DUY NHAT mot file `firestore.rules` (CI v64 do vi ban cu o `app/`).
 *  2. Quan he ghep noi phai duoc doi chieu bang HAI UID tren document ghep noi,
 *     khong tin truong `pairingId` do client tu ghi len.
 *  3. Cong thuc id ghep noi trong Kotlin va trong rules khop nhau.
 *  4. Lenh Cam phai cat duong du lieu dinh vi, khong chi cat ho so.
 *  5. Ham phai duoc khai BAO TRUOC noi dung no (bai hoc commit 5cc4367).
 *  6. (66.0) Lenh Cam phai co pham vi + tu duoc han; tu xoa tai khoan phai theo
 *     DUNG THU TU, va khong ai xoa duoc vet di chuyen khi ho so con nguyen.
 */
class RulesLockdownRegressionTest {

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile.canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "firestore.rules").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Không tìm thấy thư mục gốc dự án (phải có settings.gradle.kts + firestore.rules)")
    }

    private fun repoFile(name: String): String =
        String(File(repoRoot(), name).readBytes(), Charsets.UTF_8)

    /**
     * Cat comment truoc khi noi "ma rules KHONG con chuoi X". Mot dong giai
     * thich ve cai loi cu ("truoc day: allow read, write: if isAuthenticated()")
     * du lam assert do that bai vi ly do sai. Phai cat theo DONG — ban Regex
     * `//[^\n]*` an ca `https://` ben trong chuoi.
     */
    private fun stripComments(source: String): String = source
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    private val rules: String by lazy { stripComments(repoFile("firestore.rules")) }

    /**
     * Lay than cua mot block o NGON.
     *
     * `match /location_history/{historyId}` xuat hien HAI lan: ban 6 dong nam
     * DUOI `pairings/{id}` (da khat bang isPairMember tu lau) va ban o ngon
     * (moi khoa ban 65.0). Tim chuoi tran lang se ra ban duoi pairings va do
     * fail vi ly do sai. Neo bang 4 dau cach dau dong → chi block o ngon.
     *
     * `substringAfter` mặc định trả về CẢ TỆP khi không thấy marker, tức test
     * sẽ đạt mà không đo gì cả (tìm thấy thứ muốn tìm ở bất kỳ đâu). Vì vậy
     * mỗi đoạn phải được kết luận là có thật trước khi cắt.
     */
    private fun segment(startMarker: String, endMarker: String = "\n    }"): String {
        val i = rules.indexOf(startMarker)
        assertTrue("firestore.rules thiếu đoạn bắt đầu bằng '$startMarker'", i >= 0)
        val rest = rules.substring(i + startMarker.length)
        val j = rest.indexOf(endMarker)
        assertTrue("Đoạn '$startMarker' không đóng bằng '$endMarker'", j >= 0)
        return rest.substring(0, j)
    }

    private fun block(path: String): String = segment("\n    match $path {")

    // ── 1. Chi mot file rules ────────────────────────────────────────────────

    /**
     * `app/firestore.rules` va `app/applet/firestore.rules` la hai ban 58 dong
     * bi quen tu ban 55.0: chung mo toan bo quyen ghi cho /users (ke ca `delete`).
     * Ai do deploy tu thu muc `app/` la toan bo nhung khoa o day bay mau, va
     * test doc rules se do sai file.
     */
    @Test
    fun chiCoMotFileRulesDuyNhatTrongRepo() {
        val copies = repoRoot().walkTopDown()
            .maxDepth(3)
            .filter { it.name == "firestore.rules" }
            .filter { f ->
                val p = f.invariantSeparatorsPath
                !p.contains("/build/") && !p.contains("/.git/") && !p.contains("/.gradle/")
            }
            .toList()
        val goc = repoRoot()
        assertTrue(
            "Phải còn ĐÚNG một file firestore.rules, nằm ở gốc repo. Bản sao ở " +
                "thư mục con là bản cũ và mở toang rules: " +
                copies.joinToString { it.path },
            copies.size == 1 &&
                copies.first().invariantSeparatorsPath ==
                File(goc, "firestore.rules").invariantSeparatorsPath
        )
    }

    // ── 2. Bon khe ho o ngon da khat ─────────────────────────────────────────

    @Test
    fun khongConDuongMoToanBoChoDuLieuOngon() {
        // Chuoi ky nan cua ca bon block cu: mo cho moi tai khoan da dang nhap.
        assertTrue(
            "Không được còn dòng 'allow read, write: if isAuthenticated();' — đó " +
                "là chữ ký của các block mở toang",
            !rules.contains("allow read, write: if isAuthenticated();")
        )

        val loc = block("/locations/{userId}")
        assertTrue("locations: chủ máy phải đọc được doc của mình",
            loc.contains("request.auth.uid == userId"))
        assertTrue("locations: người ghép nối thật mới đọc được",
            loc.contains("isPairedWith(userId)"))

        val hist = block("/location_history/{historyId}")
        assertTrue("location_history ngọn: chỉ vết của CHÍNH máy này (khớp " +
            "whereEqualTo(\"userId\", uid) mà app gửi lên)",
            hist.contains("resource.data.userId == request.auth.uid") &&
                hist.contains("request.resource.data.userId == request.auth.uid"))

        val sos = block("/sos_alerts/{alertId}")
        assertTrue("Phiếu SOS: chỉ máy lập phiếu được ghi",
            sos.contains("request.resource.data.userId == request.auth.uid"))
        assertTrue("Phiếu SOS: cha/me ghép nối phải đọc được, không thì nút SOS vô nghĩa",
            sos.contains("isPairedWith(resource.data.userId)"))

        assertTrue("pairings: đọc phải là thành viên (hoặc quản trị)",
            block("/pairings/{pairingId}").contains("allow read: if isPairMember(pairingId) || isAdmin();"))
        assertTrue("pairings: tạo phải LÀ một trong hai người",
            rules.contains("request.resource.data.parentId == request.auth.uid"))

        assertTrue("pairing_requests đã vô sinh từ 59.0 thì phải deny hết",
            block("/pairing_requests/{requestId}").contains("allow read, write: if isAdmin();"))
    }

    @Test
    fun callsKhongConLaHopThuWebrtcCuaAiCungMo() {
        val calls = block("/calls/{callId}")
        // May goi dat roomId = activePairing.id → id document chinh la id ghep noi.
        assertTrue("calls: phải là một ben của ghép nối mang id đó",
            calls.contains("isPairMember(callId)"))
        assertTrue("calls: exists() phải đi trước get() để id lạ không làm lỗi điều kiện",
            calls.contains("exists(/databases/$(database)/documents/pairings/$(callId))"))
        assertTrue("calls: nhánh 'room_<uid>' nội bộ khi chưa ghép nối vẫn chạy được",
            calls.contains("callId == 'room_' + request.auth.uid"))
    }

    // ── 3. Khong tin chu document duoc hoi ───────────────────────────────────

    /**
     * `resource.data.pairingId` do client tu ghi, nen no chi duoc dung nhu MOT
     * CANDI: pairOfTwo phai ton tai + PAIRED + {parentId, childId} khop dung
     * {a, b}. Neu bo mot trong ba dieu kien thi "gian pairingId = nha ke ben"
     * lai mo duoc du lieu cua ho.
     *
     * Nang cap ban 65.0: ba dieu kien do VAN chua du, vi chinh tai khoan tan
     * cong tao duoc document `pair_<minh>_<nan nhan>` status PAIRED (no nam
     * trong hai ten thiet ke). Mot lop xac nhan nua nam ben NGUOI BI DOI:
     * attestedPair phai doc `users/<ben kia>.activePairingId`, la cho ma chi
     * thiet bi cua ho ghi duoc.
     */
    @Test
    fun doiChieuHaiUidTrenDocumentGhepNoiChuKhongTinPairingId() {
        val fn = segment("function pairOfTwo(pairingId, a, b) {")
        listOf(
            "pairingId is string" to "phải từ chối pairingId thiếu/không phải chuỗi",
            "exists(/databases/$(database)/documents/pairings/$(pairingId))" to "phải kiểm tra ghép nối có thật",
            "data.status == 'PAIRED'" to "phải đòi status PAIRED đúng như UI đang đòi",
            "data.parentId in [a, b]" to "phải đối chiếu parentId",
            "data.childId in [a, b]" to "phải đối chiếu childId",
            "data.parentId !=" to "hai uid phải là đúng cặp, không phải hai cặp trùng một người"
        ).forEach { (needle, why) ->
            assertTrue("pairOfTwo $why (thiếu: $needle)", fn.contains(needle))
        }

        val att = segment("function attestedPair(pairingId, otherUid) {")
        assertTrue("attestedPair phải gọi pairOfTwo, không tự xử",
            att.contains("pairOfTwo(pairingId, request.auth.uid, otherUid)"))
        assertTrue("attestedPair phải đọc hồ sơ CỦA BEN KIA (ke tan cong khong ghi duoc)",
            att.contains("get(/databases/$(database)/documents/users/$(otherUid)).data.activePairingId == pairingId"))
        assertTrue("exists() phai di truoc get() de ho so khong ton tai khong lam loi dieu kien",
            att.contains("exists(/databases/$(database)/documents/users/$(otherUid))"))
        assertTrue("phai dung 'activePairingId' in ... de ho so cu thieu truong bi tu choi chu khong loi",
            att.contains("'activePairingId' in get(/databases/$(database)/documents/users/$(otherUid)).data"))

        // Nguoi hoi phai duoc truyen bang request.auth.uid, khong phai mot gia
        // tri client tu chon, va duong doc theo `pairingId` trong document cung
        // phai qua lop xac nhan hai chu.
        assertTrue("Cặp phải được đối chiếu với request.auth.uid, không với uid đọc từ doc",
            rules.contains("attestedPair(resource.data.pairingId, userId)"))
        assertTrue("Phiếu SOS cũng đi qua attestedPair, không phải pairOfTwo trần",
            rules.contains("attestedPair(resource.data.pairingId, resource.data.userId)"))
    }

    /**
     * Hai khe con lai cua chinh document ghep noi — ca hai tung mo trong ban
     * 65.0 dau tien va chi bi phat hien khi doc lai dieu kien nhu ke tan cong:
     *  1. `allow update` dung `request.resource.data.childId == toi`: ke xau
     *     tu them ten minh vao document cua gia dinh khac va tro thanh "thanh
     *     vien" — mo tung `pairings/<id>/location_history`, `/messages`,
     *     `/schedules` cua ho.
     *  2. `allow create` cho phep ke tan cong tao document co `childId` la nan
     *     nhan; may nan nhan CHUA ghep noi se tu nhan no (findPairingFor) va
     *     de tang luon su xac nhan hai chu.
     */
    @Test
    fun documentGhepNoiKhongChoGiaThuocVaKhongChoKeXauTuXuongTen() {
        val pairings = block("/pairings/{pairingId}")
        val upd = pairings.substringAfter("allow update:").substringBefore(";")
        assertTrue("update phai doi ben CU, khong duoc tin `request.resource.data`",
            !upd.contains("request.resource.data"))
        assertTrue("update van phai cho ca hai ben that doi chieu voi resource.data",
            upd.contains("resource.data.parentId == request.auth.uid") &&
                upd.contains("resource.data.childId == request.auth.uid"))
        assertTrue("create chi may PHU HUYNH lam duoc (pairDirectly luong parentId = uid minh)",
            pairings.contains(
                "allow create: if isAuthenticated() && notBanned() &&\n" +
                    "        request.resource.data.parentId == request.auth.uid;"
            ) &&
                !pairings.contains("request.resource.data.childId == request.auth.uid")
        )
    }

    /**
     * Id ghep noi duoc sinh trong Kotlin (`"pair_" + parentId + "_" + childId`).
     * Rules doi cong thuc ay — ai doi ben nay ma quen ben kia la toan nha mat
     * nhau tren ban do ma CI van xanh.
     */
    @Test
    fun congThucIdGhepNoiKhopNhauGiuaKotlinVaRules() {
        val repo = String(
            File(repoRoot(), "app/src/main/java/com/example/data/repository/VesperaRepository.kt")
                .readBytes(), Charsets.UTF_8
        )
        assertTrue("Kotlin vẫn phải sinh id ghép nối theo công thức pair_<parent>_<child>",
            repo.contains("val pairingId = \"pair_\${parentProfile.id}_\${student.id}\""))
        assertTrue("Rules phải thử CẢ HAI thứ tự (không biết bên nào là cha)",
            rules.contains("attestedPair('pair_' + request.auth.uid + '_' + otherUid") &&
                rules.contains("attestedPair('pair_' + otherUid + '_' + request.auth.uid"))
    }

    // ── 4. Ngoai le can giu: duyet phieu quyen rieng ──────────────────────────

    /**
     * May PHU HUYNH ghi len `locations/<childId>` khi duyet phieu "gio trong
     * quyen rieng". Do la lan duy nhat mot tai khoan cham vao du lieu cua tai
     * khoan khac, va phai bo hai: dung nguyen vong `set(merge)`, chi doi
     * `isPrivacyActive` + `updatedAt`. Test nay chang ai phai mo them.
     */
    @Test
    fun phieuQuyenRiengVanGhiDuocLenMayCuaCon() {
        val loc = block("/locations/{userId}")
        val exception = "['isPrivacyActive', 'updatedAt']"
        val lan = loc.split(exception).size - 1
        assertTrue(
            "Ca create lan update phai cho phep ngoai le quyen rieng, hien thay $lan lan " +
                "(can 2)",
            lan == 2
        )
        // update thi so sanh bang diff(), vi set(merge) tra ve CA document sau hop nhat.
        assertTrue("Phai dung diff().affectedKeys(), khong kiem keys tran lang",
            loc.contains("request.resource.data.diff(resource.data).affectedKeys()"))
        assertTrue("Create cua ngoai le phai kiem keys tran lang (doc chua ton tai)",
            loc.contains("request.resource.data.keys().hasOnly("))
    }

    @Test
    fun lenhCamCatCaDuongDuLieuDinhVi() {
        // Neu chi /users bi notBanned() thi tai khoan bi cam van tiep tuc gui GPS
        // va van doc duoc vi tri cua me — "lenh Cam" chi la chu ghi chu.
        listOf("/locations/{userId}", "/location_history/{historyId}", "/sos_alerts/{alertId}", "/calls/{callId}")
            .forEach { path ->
                assertTrue("$path phai co notBanned()", block(path).contains("notBanned()"))
            }
    }

    // ── 4b. Lenh cam 66.0: co PHAM VI, co HAN, tu mo duoc ────────────────────

    /**
     * App quan tri co bon nut cam (7 ngay / 30 ngay / vinh vien / cam hoi AI),
     * tat ca deu ghi mot document `bans/<uid>`. Toan bo ngu nghia nam trong hai
     * truong `scope` va `expiresAt`, nen neu rules doc sai hai truong ay thi bon
     * nut kia cho ra bon lenh giong het nhau. Hai loi that tung xuyen o day:
     *  1. Quen `exists()` truoc khi `get()` → voi tai khoan CHUA bi cam, rules
     *     ERROR va tu choi moi yeu cau cua chinh no (app bi xac dinh nhan).
     *  2. Doc `banDoc().scope` khi document cu (63.0) khong co truong do → cung
     *     la rules ERROR. Vi vay phai co `!('scope' in banDoc())` o dau.
     */
    @Test
    fun lenhCamCoPhamViVaTuMoDungHan() {
        val still = segment("function banStillRunning() {")
        assertTrue("khong co expiresAt = vinh vien (dung nhu lenh cam ban cu)",
            still.contains("!('expiresAt' in banDoc())"))
        assertTrue("co expiresAt thi phai so voi request.time (thoi gian MAY CHU, " +
            "khong dong ho may cua ngu bi cam)",
            still.contains("banDoc().expiresAt > request.time"))

        val forFn = segment("function isBannedFor(kind) {")
        val exists = forFn.indexOf("exists(/databases/$(database)/documents/bans/$(request.auth.uid))")
        val get = forFn.indexOf("banStillRunning()")
        assertTrue("exists() phai di TRUOC banStillRunning() de tai khoan chua bi " +
            "cam khong bi rules ERROR", exists in 0 until get)
        assertTrue("scope 'ALL' phai cat ca app lan AI",
            forFn.contains("banDoc().scope == 'ALL'"))
        assertTrue("lenh cam cu khong co scope van phai la 'cam app'",
            forFn.contains("!('scope' in banDoc())"))

        assertTrue("notBanned() = cam dung app", segment("function notBanned() {").contains("isBannedFor('ALL')"))
        assertTrue("notAiBanned() = chi cam hoi AI", segment("function notAiBanned() {").contains("isBannedFor('AI')"))
    }

    /**
     * Lenh cam do quan tri ghi len phai tro DUNG nguoi (uid trong document = uid
     * trong id), dung PHAM VI (chi 'ALL' hoac 'AI' — mot typo nhu 'all' se khien
     * lenh cam vo hoc ma khong bao), va han phai O TUONG LAI. Ly do khong chan
     * `expiresAt` qua khus: `isBannedFor()` doc truong nay, nen mot lenh cam da
     * het han duoc ghi len se mo nguoc (con document, khong ai hieu sao no mo).
     */
    @Test
    fun banGhiLenPhaiTroDungNguoiDungPhamViDungTuongLai() {
        val w = segment("function banWriteIsValid(targetUid) {")
        listOf(
            "request.resource.data.uid == targetUid" to "phải trỏ đúng người bị cấm",
            "request.resource.data.scope in ['ALL', 'AI']" to "phải chặn typo scope",
            "request.resource.data.expiresAt > request.time" to "hạn cấm phải ở tương lai (server time)",
            "request.resource.data.expiresAt is timestamp" to "expiresAt phải là timestamp"
        ).forEach { (needle, why) ->
            assertTrue("banWriteIsValid $why (thiếu: $needle)", w.contains(needle))
        }

        val bans = block("/bans/{uid}")
        assertTrue("chi quan tri duoc ghi/xoa lenh cam",
            bans.contains("allow create, update: if isAdmin() && banWriteIsValid(uid);") &&
                bans.contains("allow delete: if isAdmin();"))
        assertTrue("lenh cam cua ai thi nguoi ay doc duoc (de app in ly do ra man hinh)",
            bans.contains("isAuthenticated() && request.auth.uid == uid"))
        assertTrue("khong duoc liet ke toan bo nguoi bi cam cho bat ky tai khoan nao",
            !bans.contains("allow read: if isAuthenticated();"))
    }

    /**
     * "Cam hoi AI" trong app quan tri chi co THE cat duong ghi bo dem luu tren
     * /users — cau goi AI that di thang len nha cung cap. Test nay giu dung cai
     * that do: dung mot dieu kien `notAiBanned()` tran lang se chan luon ca
     * cap nhat ten/lop cua mot tai khoan bi cam AI, con thieu no thi lenh cam
     * AI chi la chu.
     */
    @Test
    fun camAIchiCatDuongGhiBoDemKhongCatHoSo() {
        val users = block("/users/{userId}")
        val upd = statementIn(users, "update")
        assertTrue("update /users phai biet den lenh cam AI", upd.contains("notAiBanned()"))
        assertTrue("lenh cam AI chi cat khi cham vao bo dem", upd.contains("!aiQuotaTouched()"))
        assertTrue("so sanh bo dem phai dung diff() vi set(merge) tra ve ca document",
            segment("function aiQuotaTouched() {").contains(".diff(resource.data).affectedKeys()"))
        // Cam AI khong duoc cham vao doc/tao/da ho so.
        assertTrue("read /users khong duoc dung notAiBanned",
            !statementIn(users, "read").contains("notAiBanned"))
        assertTrue("delete /users khong duoc dung notAiBanned (nguoi bi cam AI van phai " +
            "tu rut duoc du lieu theo chinh sach quyen rieng)",
            !statementIn(users, "delete").contains("notAiBanned"))
    }

    // ── 4c. Tu xoa tai khoan 66.0: thu tu bi rang buoc bang rules ─────────────

    /**
     * Nut "Xóa tài khoản" dan mot chuong trinh don du lieu, va moi buoc don deu
     * la mot lenh delete bi rules chan neu no den SOM HOAC MUON. Dieu kien
     * `accountIsBeingDeleted` phan biet "don tai khoan" voi "xoa don duong di de
     * an duong": mot may con nguyen ho so thi khong duoc xoa vi tri cua no.
     * Dao nguoc cung vay — xoa ho so roi ma chua don vet thi vet con lai nam
     * duoi ten mot nguoi khong con ton tai, nen app phai don het theo dung thu
     * tu (users/<uid> TRUOC).
     */
    @Test
    fun tuXoaTaiKhoanBiRangBuocBangThuTu() {
        val fn = segment("function accountIsBeingDeleted(userId) {")
        assertTrue("phai la chinh chu (hoac dieu kien se mo lenh xoa cho ca the gioi)",
            fn.contains("isOwner(userId)"))
        assertTrue("dieu kien la HO SO KHONG CON ton tai, khong phai con ton tai",
            fn.contains("!exists(/databases/$(database)/documents/users/$(userId))"))

        val users = block("/users/{userId}")
        assertTrue("/users: chu tai khoan tu xoa duoc khi chua bi cam, hoac quan tri xoa",
            users.contains("allow delete: if (isOwner(userId) && notBanned()) || isAdmin();"))

        val loc = statementIn(block("/locations/{userId}"), "delete")
        assertTrue("locations: chi don khi ho so da mat hoac quan tri ra tay",
            loc.contains("accountIsBeingDeleted(userId)"))
        assertTrue("locations: KHONG duoc mo lenh xoa cho chu may khi ho so con " +
            "ton tai (hoc sinh xoa vet de an duong di)",
            !loc.contains("request.auth.uid == userId"))

        assertTrue("location_history: vet cua ai thi theo do",
            statementIn(block("/location_history/{historyId}"), "delete")
                .contains("accountIsBeingDeleted(resource.data.userId)"))
        assertTrue("sos_alerts: phieu SOS cung bi don theo tai khoan",
            statementIn(block("/sos_alerts/{alertId}"), "delete")
                .contains("accountIsBeingDeleted(resource.data.userId)"))
        assertTrue("sos_alerts: phieu da lap van khong ai sua duoc",
            block("/sos_alerts/{alertId}").contains("allow update: if false;"))

        val audit = blockUnderPairing("/rule_audit/{entryId}")
        assertTrue("nhat ky noi quy van khong SUA duoc",
            statementIn(audit, "update").contains("if false"))
        assertTrue("nhat ky chi XOA duoc khi quan tri don hoac tai khoan dang bi don",
            audit.contains("isAdmin() ||") &&
                audit.contains("accountIsBeingDeleted(request.auth.uid)"))
        assertTrue("pairings: quan tri don duoc document ghep noi (tai khoan thu nghiem)",
            statementIn(block("/pairings/{pairingId}"), "delete").contains("isAdmin()"))
    }

    /** Lay than cua mot subcollection nam DUOI `pairings/{pairingId}` (nho 6 dau cach). */
    private fun blockUnderPairing(path: String): String =
        segment("\n      match $path {", "\n      }")

    /** Mot cau `allow <kung>: ...;` duy nhat trong doan, khong ke comment. */
    private fun statementIn(text: String, keyword: String): String =
        text.substringAfter("allow $keyword").substringBefore(";")

    /**
     * Nut "Xóa tài khoản" phia `:admin` chi chay duoc neu quan tri LIET KE duoc
     * document cua nguoi bi don: Firestore khong co lenh xoa theo truong, khong
     * tu xoa subcollection, va client khong co Admin SDK. Ban 66.0 vi the mo
     * `isAdmin()` vao duong DOC o ba collection ngon + sau subcollection duoi
     * ghep noi. Test nay giu hai thu:
     *  1. Duong do VANG that — thieu mot cho la mot nhom du lieu khong ai xoa
     *     duoc, va bien nhan "da xoa N muc" luon luon sai.
     *  2. `isAdmin()` nam ben NGOAI va TRUOC `notBanned()` — lenh cam khong duoc
     *     chen vao duong quan tri don rac, va dat dau tien tranh loi `get()` tren
     *     document da bi xoa.
     */
    @Test
    fun quanTriLietKeDuocDeDonTaiKhoan() {
        listOf("/locations/{userId}", "/location_history/{historyId}", "/sos_alerts/{alertId}")
            .forEach { path ->
                val read = statementIn(block(path), "read")
                assertTrue("$path: read phai mo cho quan tri", read.contains("isAdmin() ||"))
                assertTrue("$path: isAdmin() phai di TRUOC notBanned()",
                    read.indexOf("isAdmin()") < read.indexOf("notBanned()"))
            }
        val calls = block("/calls/{callId}")
        assertEquals(
            "calls: ca document cha lan subcollection deu phai cho quan tri don " +
                "(neu khong thi bien nhan xoa thieu mot nhom khong lay lai duoc)",
            2,
            Regex("allow read, write: if isAdmin\\(\\) \\|\\|").findAll(calls).count()
        )
        listOf(
            "/schedules/{scheduleId}", "/agreements/{agreementId}", "/extra_time/{requestId}",
            "/privacy_requests/{requestId}", "/messages/{messageId}", "/location_history/{historyId}"
        ).forEach { sub ->
            assertTrue("pairings$sub: phai la thanh vien hoac quan tri",
                blockUnderPairing(sub).contains(
                    "allow read, write: if isPairMember(pairingId) || isAdmin();"
                )
            )
        }
    }

    // ── 5. Thu tu khai bao ham ───────────────────────────────────────────────
    /**
     * Firestore khai ham theo thu tu: de mot ham dung ham chua khai bao la ly
     * do rules tu choi deploy luc minh khong mong (commit 5cc4367).
     */
    @Test
    fun moiHamDuocKhaiBaoTruocNoiDungDenNo() {
        val names = Regex("function (\\w+)\\(").findAll(rules).map { it.groupValues[1] }.toList()
        assertTrue("Phat hien it nhat 3 ham trong rules", names.size >= 3)
        names.forEach { name ->
            val khaiBao = rules.indexOf("function $name(")
            val dungSaiCho = Regex("(?<!function )\\b$name\\(").findAll(rules)
                .map { it.range.first }
                .filter { it < khaiBao }
                .toList()
            assertTrue("Ham $name bi dung truoc khi khai bao tai ${dungSaiCho}",
                dungSaiCho.isEmpty())
        }
    }
}
