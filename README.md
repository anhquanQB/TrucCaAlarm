# Trực Ca & Báo Thức (Android APK — v1.1)

Ứng dụng Android theo dõi lịch trực 5 kíp (A, B, C, D, E) — 3 ca (Sáng `07:00–15:00`, Chiều `15:00–23:00`, Đêm `23:00–07:00`), hiển thị thẻ **ĐANG TRỰC** gradient xanh kèm đếm ngược thời gian thực, Widget màn hình chính, nhập tên người trực theo Kíp hoặc riêng từng ca, báo thức hệ thống và đẩy thông báo rung sang đồng hồ **Huawei Watch GT 6 Pro** qua **Huawei Sức khỏe**.

## Tính năng chính
- **Dữ liệu sẵn có:** Đã nạp sẵn lịch trực **Tháng 11/2026** và **Tháng 12/2026** (tô vàng kíp bạn chọn, mặc định Kíp A).
- **Quản lý nhân sự linh hoạt 2 cấp:**
  - Nhập danh sách người đi ca mặc định cho từng **Kíp (A–E)** trong tab **Báo thức**.
  - Chạm vào bất kỳ ngày nào trong tab **Lịch tháng** để nhập tên người đi ca **riêng cho từng Ca sáng / Ca chiều / Ca đêm** của ngày đó (khi có người đổi ca hoặc trực dự phòng).
- **Liên kết đồng hồ Huawei Watch GT 6 Pro (qua Huawei Sức khỏe):**
  - Tự phát kênh thông báo chuẩn (`Nhắc ca đồng hồ Huawei Watch`, không dùng chế độ nền ẩn nên không bị Huawei Sức khỏe lọc bỏ) lặp lại 1–5 nhịp (cách nhau 20 giây) khi báo thức kêu cho đến khi bạn tắt báo thức.
  - Có nút **Gửi thông báo thử sang đồng hồ** trong tab **Báo thức** để kiểm tra ngay.
- **Nhập lịch linh hoạt:** Hỗ trợ **Excel (`.xlsx`)**, **Word (`.docx`)**, **CSV**, **ảnh chụp bảng lịch (OCR)** và **tạo nhanh theo chu kỳ 10 ngày**.

## Cách bật thông báo trên đồng hồ Huawei Watch GT 6 Pro
1. Cài đặt file `app-debug.apk` lên điện thoại và cấp quyền **Thông báo** khi ứng dụng hỏi.
2. Mở ứng dụng **Huawei Sức khỏe (Huawei Health)** → vào mục **Thiết bị** → chọn **Huawei Watch GT 6 Pro** → **Thông báo** → bật công tắc cho **Trực Ca & Báo Thức**.
3. Mở lại ứng dụng **Trực Ca & Báo Thức** → tab **Báo thức** → bấm **Gửi thông báo thử sang đồng hồ** để kiểm tra đồng hồ rung.

## Cách tự xuất file `.apk` miễn phí bằng GitHub Actions
1. Giải nén file `TrucCaAlarm-Android.zip`.
2. Tạo một kho lưu trữ (Repository) trên GitHub và tải toàn bộ thư mục bên trong `TrucCaAlarm` lên (bao gồm `.github/workflows/build-apk.yml`).
3. Vào tab **Actions** → chạy workflow **Build Android APK** → chờ 2–4 phút và tải file `TrucCaAlarm-APK` ở mục **Artifacts**.
