# ATMMT — Session Hijacking Lab

Đồ án môn **An toàn mạng máy tính**: tìm hiểu và mô phỏng Session Hijacking trên ứng dụng ghi chú Java/Spring Boot, triển khai HTTPS và đánh giá vòng đời phiên đăng nhập.

Ứng dụng dùng tài khoản, dữ liệu giả và chỉ publish cổng trên loopback của máy chạy lab. Các ghi chú chỉ đọc; mỗi người dùng xem dữ liệu theo danh tính được server xác thực.

## Tài liệu của nhóm

| Nội dung | Tài liệu |
|---|---|
| Báo cáo tổng hợp của nhóm 05: lý thuyết, triển khai và thực nghiệm | [ATMMT_nhom05](session-hijacking-lab/docs/ATMMT_nhom05.md) |
| Phân công, phạm vi, phần việc còn lại | [Phân công đồ án](PHAN_CONG_SESSION_HIJACKING.md) |
| Chạy HTTP/HTTPS, chứng chỉ, tài khoản, kiểm chứng | [README ứng dụng](session-hijacking-lab/README.md) |
| Nhận project, chạy HTTP/HTTPS và thực nghiệm, DevTools và checklist | [Bàn giao nhóm 05](session-hijacking-lab/docs/BAN_GIAO_NHOM05.md) |
| Test case HTTP/HTTPS, giao diện và thực nghiệm T01–T09 | [Bộ test case nhóm 05](session-hijacking-lab/docs/TEST_CASES_NHOM05.md) |
| Mở project, chuẩn bị máy và thao tác demo | [Hướng dẫn demo](session-hijacking-lab/docs/HUONG_DAN_DEMO_NHOM05.md) |
| Nội dung slide, hình minh họa và ghi chú người nói | [Khung slide Markdown](session-hijacking-lab/docs/KHUNG_SLIDE_NHOM05.md) |

## Chạy nhanh bản HTTP

Máy cần Git, Docker Desktop đang chạy với Linux containers và Docker Compose. Trên máy nhận project mới, mở terminal:

```bash
git clone https://github.com/HieuDao1410/ATMMT.git
cd ATMMT/session-hijacking-lab
docker compose up --build -d
```

Mở **http://127.0.0.1:8080**. Tài khoản giả để thực hành:

| Tài khoản | Mật khẩu lab |
|---|---|
| `alice` | `AliceLab!2026` |
| `bob` | `BobLab!2026` |

Đăng nhập chuyển đến `/me`; `/notes` hiển thị ghi chú của chính người dùng. Đăng xuất bằng nút trong ứng dụng.

Các mật khẩu trên công khai và chỉ dùng cho lab. Không nhập dữ liệu hoặc mật khẩu thật.

## Chạy bản HTTPS

Trên Windows, mở **PowerShell** tại `ATMMT/session-hijacking-lab`. Máy cần JDK 17 trở lên để dùng `keytool` và công cụ kiểm chứng:

```powershell
# Tạo chứng chỉ riêng trên máy này, chỉ chạy khi chưa có bộ chứng chỉ:
.\scripts\New-LabCertificate.ps1
docker compose -f compose.https.yaml up --build -d
```

Mở **https://127.0.0.1:8443**. Trình duyệt cần tin cậy đúng chứng chỉ lab; xem [hướng dẫn chứng chỉ](session-hijacking-lab/README.md#chạy-bản-https--phần-2). Công cụ kiểm chứng dưới đây tin cậy chứng chỉ chỉ trong lần chạy, không cần sửa kho chứng chỉ Windows:

```powershell
java scripts/VerifyHttpsLab.java .local/tls/localhost.cer
```

Phép thử timeout chờ 75 giây. Cookie HTTPS là `__Host-LABSESSION_HTTPS`; timeout không hoạt động 1 phút phục vụ demo. HTTP dùng `JSESSIONID`, timeout 20 phút. Hai bản là hai tiến trình và kho session riêng.

## Công nghệ và trạng thái

- Java 17, Spring Boot 4.1.1, Spring Security, Thymeleaf; Maven Wrapper có trong mã nguồn.
- Phần 1: ứng dụng, dữ liệu riêng, đăng nhập/logout, giao diện và Docker đã có.
- Phần 2: HTTPS, thuộc tính cookie, đổi ID khi login, logout và timeout đã được kiểm chứng; kết quả chi tiết nằm trong tài liệu bàn giao.
- Phần 3: đã bắt gói HTTP/HTTPS bằng tcpdump, phân tích bằng TShark (Wireshark CLI), phát lại cookie lấy từ PCAP và đạt T01–T09. Có báo cáo, ảnh và video trình bày kết quả; PCAP gốc giữ riêng trong `.local/`. Chưa có ảnh DevTools hoặc video quay trực tiếp lúc bắt gói.

HTTPS bảo vệ đường truyền; trong kiểm chứng của ứng dụng này, cookie HTTPS đã được sao chép vẫn xác thực được khi phiên còn sống. Logout hoặc timeout làm cookie cũ mất quyền. Cần trình bày cả kết quả phòng vệ và giới hạn này.

## File giữ riêng trên từng máy

`.local/`, keystore `.p12`/`.pfx`, `runtime.env` bên trong `.local/` và `target/` không thuộc bản bàn giao trên Git. Mỗi thành viên tự tạo chứng chỉ của mình; không gửi khóa riêng hoặc mật khẩu keystore qua repository.

Khi cập nhật, chọn đúng file của đồ án để commit; các bài giảng và bài giải trong thư mục làm việc bên ngoài project không thuộc mã nguồn ứng dụng.
