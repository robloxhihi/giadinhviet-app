# Gia Đình Việt

Ứng dụng Android dành cho gia đình: ba mẹ xem được vị trí và thời gian dùng điện thoại
của con, đặt giới hạn ứng dụng theo khung giờ, và nhận cấp cứu khi cần.
Sản phẩm dự thi khoa học kỹ thuật, viết bằng Kotlin + Jetpack Compose, dữ liệu đồng bộ
qua Firebase Firestore.

**Tải bản cài đặt mới nhất:** <a href="tai-app.html"><strong>Gia Đình Việt &mdash; file cài đặt</strong></a>
(`tai-app.html` trong thư mục này, kèm kích thước và hash kiểm chứng)

*Trang giới thiệu có kiểm chứng người thật (Cloudflare Turnstile):*
<https://giadinhviet-9eud03puq7u.qoder.website>

## Ứng dụng làm gì

| Nhóm | Chi tiết |
| --- | --- |
| Định vị | Bản đồ ngoại tuyến (osmdroid), nhiều lớp bản đồ, sai số hiển thị thật, vùng an toàn, lịch sử di chuyển |
| Thời gian dùng máy | Đo bằng UsageStatsManager, biểu đồ theo ngày, hạn mức phút, xin thêm thời gian |
| Chặn ứng dụng | AccessibilityService chặn app theo khung giờ; ba mẹ bật/tắt từ xa, con không tự gỡ được |
| Học tập trung | Ba mẹ bấm "giờ học" trên máy mình, máy con chặn ứng dụng trong suốt thời gian đó |
| Cấp cứu | Nút SOS nổi, gọi hotline, SMS toạ độ khi mất Internet, đọc vị trí bằng giọng nói |
| Gia đình | Chat gia đình, nhắc nhở, trợ lý AI (key không nằm trong repo) |

## Vì sao nhiều quyền phải tự bật

Android **cấm** một ứng dụng tự cấp cho nó các quyền đặc biệt: Trợ năng, Hiển thị trên
ứng dụng khác, Dữ liệu sử dụng, Tối ưu pin. Nút trong app chỉ mở đúng màn hình Cài đặt
của hệ thống cho từng quyền, rồi đọc lại trạng thái thật khi bạn quay về — app không bao
giờ tự đánh dấu "đã cấp". Quyền Vị trí và Tin nhắn thì có hộp thoại hệ thống, app xin
ngay khi bạn vào.

## Build

Repo này **không** cần Android SDK trên máy bạn để có file cài đặt: mỗi tag `v*` được
GitHub Actions tự chạy unit test, build `assembleDebug`, ký bằng `debug.keystore` trong
repo và gắn file `GiaDinhViet.apk` vào Release.

```bash
git tag -a v51 -m "ban 51.0"
git push origin refs/tags/v51          # -> https://github.com/robloxhihi/giadinhviet-app/releases
```

Bản mới nhất có file cài đặt thật: **v51** (`GiaDinhViet.apk` gắn kèm Release, tải qua
[GiaDinhViet.apk](https://github.com/robloxhihi/giadinhviet-app/releases/download/v51/GiaDinhViet.apk)
hoặc trang [Tai app](https://robloxhihi.github.io/giadinhviet-app/tai-app.html)).
Tag `v50` vẫn nằm trong repo nhưng **không có Release**: CI đỏ vì hai test hồi quy tự
viết do tôi sai (`stripComments` ăn cả `https://` trong URL), sửa ở `v51`.

Muốn build bằng Android Studio: mở thư mục này, tạo `.env` chứa `GEMINI_API_KEY`
(xem `.env.example` — không commit key thật), rồi chạy `assembleDebug`.

## Kiểm thử

Toàn bộ logic quan trọng có unit test chạy trên CI, không cần thiết bị:
`app/src/test/java/com/example/`. Các test đó chặn đúng những lỗi đã xảy ra thật —
số liệu bịa, thẻ vị trí che bản đồ, nhãn đường "chưa đặt tên" in ra như địa chỉ thật,
quyền hiển thị sai trạng thái.

```bash
./gradlew test          # cần Android SDK + JDK 17 trở lên
```

## Giới hạn thật của bản đang phát hành

* APK ký bằng **debug keystore công khai trong repo**: chứng minh file không bị sửa,
  không chứng minh người ký. Đây là bản demo, không phải bản phát hành chính thức.
* Chưa có **`.exe`** cho Windows và **`.ipa`** cho iPhone, và không thể có: app dựa vào
  AccessibilityService, UsageStatsManager và quyền định vị nền — ba cơ chế chỉ Android
  mới có. iOS cấm hoàn toàn mô hình "app cha điều khiển app con"; muốn chạy trên Windows
  chỉ có giả lập Android.
* Đăng nhập Google có thể lỗi trên một số máy vì SHA-256 của khoá debug chưa được cập
  nhật vào cấu hình Firebase của dự án.
* Quy tắc Firestore trong `firestore.rules` cần được deploy lại để tính năng "một nhà"
  (nhiều người cùng xem một bé) hoạt động.

## Cấu trúc

```
app/src/main/java/com/example/
  data/        mô hình + repository Firestore (không nuốt lỗi khi ghi)
  service/     telemetry, định vị, chặn app, SOS, quyền, tải bản đồ offline
  ui/screens/  7 tab chính
  ui/theme/    bảng màu tối/sáng + ảnh nền
.github/workflows/build-apk.yml   pipeline test -> build -> release
firestore.rules, firebase-blueprint.json   luật truy cập và sơ đồ dữ liệu
```
