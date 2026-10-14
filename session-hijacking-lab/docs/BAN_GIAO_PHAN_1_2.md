# Bàn giao ứng dụng và bản bảo vệ HTTPS

**Cập nhật:** 14/10/2026.<br>
**Người nhận:** các thành viên tiếp tục làm đồ án, đặc biệt người 3 phụ trách lab mạng.<br>
**Mã nguồn được kiểm tra:** commit `65b583536ec2f8c8141c0ab744c9ade9ebc05fdf`. Các thay đổi bổ sung ở lượt này là tài liệu và bằng chứng bàn giao.

## 1. Những gì được bàn giao

- Ứng dụng ghi chú chỉ đọc, form login bằng session, hai tài khoản mẫu và dữ liệu riêng.
- Giao diện trang chủ, login, tài khoản và ghi chú; hỗ trợ tìm kiếm trên dữ liệu đã render.
- Profile HTTP và HTTPS độc lập; cấu hình Docker và Maven Wrapper.
- Công cụ tạo chứng chỉ tại từng máy, kiểm chứng HTTPS và timeout.
- [Nội dung báo cáo người 1–2](BAO_CAO_PHAN_1_2.md), [hướng dẫn vận hành](../README.md) và [phân công cấp nhóm](../../PHAN_CONG_SESSION_HIJACKING.md).

Khóa riêng, mật khẩu keystore và session đang hoạt động không thuộc gói bàn giao. Những file cần dùng riêng được tạo lại ở máy nhận.

## 2. Chạy trên máy nhận

### 2.1. HTTP — Git Bash hoặc terminal

```bash
git clone https://github.com/HieuDao1410/ATMMT.git
cd ATMMT/session-hijacking-lab
docker compose up --build -d
docker compose logs --tail 40 app
```

Điều kiện: Docker Desktop đang chạy với Linux containers. Mở `http://127.0.0.1:8080`; đăng nhập `alice` / `AliceLab!2026` rồi xem `/me` và `/notes`.

Nếu repository đã clone, chuyển đến thư mục tương ứng của bản clone đó. Chỉ cập nhật từ Git sau khi đã xem và bảo toàn các thay đổi riêng của thành viên.

### 2.2. HTTPS — PowerShell trên Windows

Tại `ATMMT/session-hijacking-lab`, kiểm tra `java -version` và `keytool -help`; cần JDK 17 trở lên.

```powershell
.\scripts\New-LabCertificate.ps1
docker compose -f compose.https.yaml up --build -d
java scripts/VerifyHttpsLab.java .local/tls/localhost.cer
```

Script chứng chỉ chỉ chạy một lần khi chưa có bộ chứng chỉ. Nếu đã có `.local/tls`, dùng bộ hiện có hoặc làm theo hướng dẫn quản lý chứng chỉ trong README; không xóa/ghi đè để xử lý lỗi một cách tùy ý.

URL HTTPS là `https://127.0.0.1:8443`. Công cụ Java tin cậy chứng chỉ trong lần chạy, không cần nhập Windows Root. Nếu demo bằng trình duyệt, người dùng chủ động tin cậy đúng `.cer` theo README; không dùng nút bỏ qua cảnh báo TLS để ghi nhận kết quả thành công.

Mật khẩu `bob` là `BobLab!2026`. Đây là tài khoản giả công khai.

## 3. Checklist nhận bàn giao trên trình duyệt

Thực hiện riêng ở từng bản với phiên mới. Dùng profile trình duyệt độc lập cho Alice và Bob; hai tab cùng profile thường chia sẻ cookie.

| Mã | Thao tác | Kết quả cần ghi nhận |
|---|---|---|
| P1-01 | Chưa đăng nhập, mở `/me` | Chuyển đến `/login` |
| P1-02 | Đăng nhập Alice | `/me` hiển thị `alice` |
| P1-03 | Mở `/notes?username=bob` bằng phiên Alice | Chỉ có ghi chú Alice |
| P1-04 | Profile độc lập đăng nhập Bob | `/me` là `bob`, ghi chú thuộc Bob |
| P1-05 | Đăng nhập sai | Hiển thị thông báo chung, không vào trang riêng |
| P1-06 | Đăng xuất bằng nút; mở lại `/me` | Phải đăng nhập lại |
| P2-01 | Mở HTTPS khi đã tin cậy đúng chứng chỉ | Không bỏ qua cảnh báo TLS; trang tải được |
| P2-02 | Xem cookie HTTPS trong DevTools | Tên `__Host-LABSESSION_HTTPS`, Secure, HttpOnly, SameSite=Lax, Path `/`, không có Domain |
| P2-03 | So sánh ID cookie trước và sau login | ID thay đổi; chỉ ghi kết luận, không đưa ID đầy đủ vào tài liệu |
| P2-04 | Tạo phiên HTTPS, ngừng mọi request từ phiên này ít nhất 75 giây | Truy cập lại `/me` phải login |

### Cách quan sát cookie bằng DevTools

1. Mở DevTools (`F12` trên trình duyệt có hỗ trợ), chọn **Network** và giữ log nếu cần so sánh luồng chuyển trang.
2. Tải `/login`, chọn request của chính ứng dụng. Xem **Response Headers → Set-Cookie** để nhận diện tên và thuộc tính cookie; không chụp giá trị đầy đủ.
3. Đăng nhập, quan sát phản hồi POST `/login` trước khi chuyển sang `/me`. So sánh ID trong bộ nhớ để kết luận đã đổi phiên.
4. Trong **Application/Storage → Cookies**, chọn origin của ứng dụng. Xem cột Secure, HttpOnly, SameSite, Path và Domain. Trình duyệt có thể hiển thị hostname ở cột Domain ngay cả với cookie host-only; đối chiếu việc server không phát thuộc tính `Domain` trong `Set-Cookie`.
5. Với request `/me`, quan sát **Request Headers → Cookie**. Đây là dữ liệu mà trình duyệt thấy tại endpoint, không phải bằng chứng cookie đọc được trên đường truyền HTTPS.
6. Logout rồi xác nhận cookie bị xóa và `/me` yêu cầu đăng nhập. Kết thúc phiên trước khi lưu/chia sẻ hình; che cả giá trị cookie lẫn CSRF token.

Với timeout, đóng tab hoặc dừng mọi request nền sử dụng cùng phiên trước khi chờ. Bộ kiểm chứng Java sử dụng cookie jar riêng nên các tab trình duyệt không giữ sống phiên Bob của phép thử đó.

Các mục yêu cầu client B dùng cookie sao chép, lưu ảnh/video thí nghiệm và đối chiếu Wireshark thuộc phần phối hợp với người 3. Nhóm không đánh dấu các mục này đã hoàn tất chỉ từ checklist dự kiến.

## 4. Kiểm tra gói bàn giao từ Git

Lần kiểm tra trên máy phát triển dùng một checkout sạch được xuất từ commit trên. Checkout ban đầu không có `.local/` và `target/`. Script tạo chứng chỉ được chạy trong checkout này để xác nhận không cần nhận khóa riêng từ người viết mã.

Docker build được thực hiện không dùng cache của các bước build image; cache tải Maven dependency vẫn có thể được dùng. Vì vậy, kết quả xác nhận mã nguồn và Wrapper đủ để build trong môi trường Docker hiện tại, không phải phép thử tải mọi dependency trên một máy ngoại tuyến hoặc hoàn toàn mới.

**Kết quả lần kiểm tra 14/10/2026:**

| Phép kiểm tra | Kết quả và bằng chứng |
|---|---|
| Checkout sạch từ Git | Không có `.local/` hoặc `target/` trong bản xuất ban đầu |
| Tạo chứng chỉ ở checkout mới | Thành công; dùng khóa mới, không sao chép keystore của máy phát triển |
| Docker build không dùng cache bước build image | BUILD SUCCESS; 13 kiểm tra HTTP đạt — [trích xuất kết quả](minh-chung/kiem-chung-http.txt) |
| HTTPS của checkout sạch | 24 kiểm chứng đạt, kết nối TLS 1.3 — [log đã che mã phiên](minh-chung/kiem-chung-https.txt) |
| Logout và timeout | Phiên gốc và cookie sao chép mất quyền sau logout; cả hai hết hiệu lực sau 75 giây không hoạt động |
| Tin cậy TLS tại Windows trên máy phát triển | Người dùng xác nhận nhập đúng chứng chỉ lab vào CurrentUser Root; client Windows mặc định truy cập HTTPS được HTTP 200, giữ xác minh chứng chỉ |
| Mở trang login HTTPS trên trình duyệt ngoài Codex | Người dùng xác nhận ngày 14/10: mở được, không có cảnh báo chứng chỉ; chưa có ảnh DevTools kèm theo |

HTTPS kiểm chứng dùng cổng loopback tạm `18443` để không đụng bản demo ở `8443`; profile và nghiệp vụ giống nhau. Log không chứa khóa riêng, mật khẩu keystore, mật khẩu đăng nhập hoặc mã phiên đầy đủ. Các kết quả này xác nhận chạy lại trên cùng máy bằng file lấy từ Git; chưa có xác nhận chạy từ máy của thành viên khác.

Việc nhập Windows Root dùng chứng chỉ của bản demo `8443`, khác chứng chỉ mới của checkout sạch. Trình duyệt tích hợp Codex vẫn từ chối chứng chỉ sau khi Windows client đã truy cập được; nhóm không bỏ qua cảnh báo. Bước P2-01 được người dùng xác nhận riêng trên trình duyệt ngoài Codex. P2-02/P2-03 và các thao tác DevTools khác vẫn cần ghi bằng chứng theo checklist; kết quả mở trang không thay cho các kiểm tra đó.

## 5. Ranh giới bàn giao và việc cần người nhận xác nhận

| Việc | Trạng thái |
|---|---|
| Mã nguồn và công cụ đã có trên Git | Đã bàn giao qua repository |
| README gốc, nội dung báo cáo và checklist nhận bàn giao | Đã chuẩn bị; cần push bản cập nhật này |
| Chạy lại từ checkout sạch trên máy phát triển | Đạt 13 kiểm tra HTTP và 24 kiểm chứng HTTPS; có log liên kết ở mục 4 |
| Thành viên nhận chạy trên máy riêng | Chưa có xác nhận từ thành viên đó |
| Bản bắt gói HTTP/HTTPS, ảnh/video tấn công và đối chiếu | Người 3 tiếp tục thực hiện |
| Ghép báo cáo chung, thông tin nhóm và chuẩn bị bảo vệ | Cả nhóm hoàn thiện sau thí nghiệm |

Người nhận ghi rõ ngày, môi trường và kết quả sau khi tự chạy. Không thay trạng thái “chưa có xác nhận” bằng kết quả dự kiến.

## 6. Những file được đưa lên Git

Đưa lên Git mã nguồn, Docker/Maven, các script, Markdown và bằng chứng đã loại thông tin phiên. Giữ riêng `.local/`, `.p12`/`.pfx`, `runtime.env`, `target/`, file bắt gói có dữ liệu phiên và log chưa rà soát.

Đối với chứng chỉ tự tạo, chia sẻ `.cer` công khai nếu một client lab khác cần tin cậy server đó, đồng thời đối chiếu fingerprint qua kênh tin cậy. Không gửi `.p12` hoặc mật khẩu keystore để giúp người khác chạy project.
