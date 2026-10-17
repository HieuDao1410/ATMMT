# BÀN GIAO ĐỒ ÁN SESSION HIJACKING — NHÓM 05

**Môn học:** An toàn mạng máy tính.<br>
**Cập nhật:** 14/10/2026.<br>
**Người nhận:** cả ba thành viên nhóm 05.<br>
**Repository:** [HieuDao1410/ATMMT](https://github.com/HieuDao1410/ATMMT).

Tài liệu này hướng dẫn nhận mã nguồn, chạy ứng dụng và thực nghiệm, đọc bằng chứng, xác nhận kết quả trên máy nhận và chuẩn bị nộp bài. Nội dung giải thích cơ chế, thiết kế và kết quả được tập trung trong [báo cáo ATMMT_nhom05](ATMMT_nhom05.md).

## 1. Gói bàn giao và thứ tự đọc

| Nội dung | Vị trí và cách sử dụng |
|---|---|
| Báo cáo chung | [ATMMT_nhom05.md](ATMMT_nhom05.md): đọc trước để hiểu mục tiêu, cookie/session, thiết kế, phòng vệ và thí nghiệm |
| Kiểm thử và demo | [Test case](TEST_CASES_NHOM05.md), [mở project/chạy demo](HUONG_DAN_DEMO_NHOM05.md), [khung slide](KHUNG_SLIDE_NHOM05.md) |
| Hướng dẫn vận hành chi tiết | [README ứng dụng](../README.md): môi trường, chứng chỉ, cấu hình và cách xử lý lỗi |
| Phân công và trạng thái nhóm | [Phân công đồ án](../../PHAN_CONG_SESSION_HIJACKING.md) |
| Ứng dụng | Spring Boot, form login, `/me`, ghi chú chỉ đọc theo tài khoản và logout; HTTP/HTTPS có tiến trình và kho session riêng |
| Công cụ HTTPS | [Tạo chứng chỉ](../scripts/New-LabCertificate.ps1), [kiểm chứng HTTPS](../scripts/VerifyHttpsLab.java) |
| Công cụ thực nghiệm | [Run-Part3.ps1](../scripts/part3/Run-Part3.ps1), [Part3Lab.java](../scripts/part3/Part3Lab.java), [tạo báo cáo HTML](../scripts/part3/Build-Part3Report.py), [image công cụ](../scripts/part3/packet-tools/Dockerfile) |
| Bằng chứng phần ứng dụng | [13 kiểm tra HTTP](minh-chung/kiem-chung-http.txt), [24 kiểm chứng HTTPS](minh-chung/kiem-chung-https.txt) |
| Bằng chứng thực nghiệm | [Báo cáo HTML](minh-chung/phan3/run-063d980c01c2/index.html), [log T01–T09](minh-chung/phan3/run-063d980c01c2/events.tsv), [bảng gói đã che cookie](minh-chung/phan3/run-063d980c01c2/packets-redacted.tsv) |
| Ảnh và video | [HTTP](minh-chung/phan3/run-063d980c01c2/frame-01.jpg), [HTTPS](minh-chung/phan3/run-063d980c01c2/frame-02.jpg), [vòng đời phiên](minh-chung/phan3/run-063d980c01c2/frame-03.jpg), [video kết quả](minh-chung/phan3/run-063d980c01c2/demo-ket-qua.mp4) |

Tải repository về để mở HTML; GitHub không hiển thị file HTML như một website. Video dài 24 giây, không âm thanh, trình bày lại kết quả đã ghi; không phải bản quay trực tiếp lúc bắt gói. PCAP gốc, khóa riêng, mật khẩu keystore và dữ liệu phiên không thuộc gói bàn giao trên Git.

## 2. Điều kiện trên máy nhận

| Công việc | Điều kiện |
|---|---|
| Lấy mã nguồn | Git và quyền truy cập repository |
| Build/chạy ứng dụng bằng Docker | Docker Desktop đang chạy với Linux containers và Docker Compose |
| Tạo chứng chỉ, chạy client HTTPS và thực nghiệm | JDK 17 trở lên; `java` và `keytool` có trong PATH |
| Tạo HTML từ kết quả mới | Python 3 |
| Xem ứng dụng và bằng chứng | Trình duyệt; Wireshark GUI chỉ cần nếu muốn tự mở PCAP |

Ứng dụng trong Docker dùng Java 17, không phụ thuộc phiên bản Java trên host. Các công cụ Java chạy trên host vẫn cần JDK tương thích. Thực nghiệm tự động dùng cổng host mặc định 8080/8443 và bắt gói tại `eth0` trong network namespace của từng container ứng dụng.

## 3. Nhận mã nguồn và chạy ứng dụng

### 3.1. Clone và chạy HTTP — Git Bash hoặc terminal

```bash
git clone https://github.com/HieuDao1410/ATMMT.git
cd ATMMT/session-hijacking-lab
docker compose up --build -d
docker compose logs --tail 40 app
```

Nếu đã clone, dùng bản clone hiện có. Xem và bảo toàn thay đổi riêng trước khi cập nhật; không reset hoặc xóa thay đổi để xử lý lỗi đồng bộ.

Mở `http://127.0.0.1:8080`:

| Tài khoản lab | Mật khẩu công khai |
|---|---|
| `alice` | `AliceLab!2026` |
| `bob` | `BobLab!2026` |

Đăng nhập chuyển đến `/me`; `/notes` chỉ hiển thị ghi chú theo principal. Các ghi chú chỉ đọc. Tài khoản và dữ liệu đều giả, không nhập mật khẩu hoặc dữ liệu thật.

### 3.2. Chạy HTTPS — PowerShell trên Windows

Mở PowerShell tại `ATMMT/session-hijacking-lab`, kiểm tra `java -version` và `keytool -help`. Chỉ chạy bước tạo chứng chỉ khi chưa có bộ chứng chỉ của máy:

```powershell
# Chỉ chạy khi chưa có bộ chứng chỉ trong .local/tls/:
.\scripts\New-LabCertificate.ps1
docker compose -f compose.https.yaml up --build -d
java scripts/VerifyHttpsLab.java .local/tls/localhost.cer
```

Mỗi máy tự tạo chứng chỉ. Nếu đã có `.local/tls`, dùng bộ hiện có hoặc làm theo hướng dẫn quản lý chứng chỉ trong README; không xóa/ghi đè tùy ý. Công cụ kiểm chứng dùng kho tin cậy riêng của lần chạy, giữ xác minh chứng chỉ và hostname, không cần nhập Windows Root. Phép thử timeout chờ 75 giây.

URL HTTPS là `https://127.0.0.1:8443`. Khi demo bằng trình duyệt, người dùng đối chiếu fingerprint rồi chủ động tin cậy đúng `.cer` theo README. Không dùng nút bỏ qua cảnh báo TLS để ghi nhận thành công. Hai file Compose là hai project độc lập, không ghép chúng thành một lệnh với hai tùy chọn `-f`.

| Bản | Cookie | Timeout không hoạt động |
|---|---|---|
| HTTP 8080 | `JSESSIONID`, HttpOnly, SameSite=Lax | 20 phút |
| HTTPS 8443 | `__Host-LABSESSION_HTTPS`, Secure, HttpOnly, SameSite=Lax | 1 phút, dành cho demo |

Hai bản chỉ publish cổng trên loopback. Cookie không được cô lập theo số cổng; ứng dụng dùng tên cookie và kho session riêng để tránh nhận nhầm phiên.

## 4. Chạy lại thực nghiệm và nhận bằng chứng mới

Sau khi chuẩn bị ứng dụng và chứng chỉ, mở PowerShell tại `session-hijacking-lab`:

```powershell
docker build -t session-lab-packet-tools:local scripts/part3/packet-tools
.\scripts\part3\Run-Part3.ps1
```

Script bắt gói bằng tcpdump, phân tích bằng TShark (Wireshark CLI), dùng cookie trích từ HTTP PCAP để phát lại trên client B, đối chiếu HTTPS và kiểm chứng vòng đời phiên. Không cần cài Wireshark/Npcap trên host cho luồng này. Không cung cấp khóa giải mã TLS cho TShark.

Chờ giai đoạn timeout 75 giây; tiến độ được in mỗi 15 giây. Script tạo mã lần chạy `run-...` mới và chỉ công bố kết quả sau khi đủ T01–T09, gồm đổi ID ở cả HTTP/HTTPS:

- `.local/part3/run-.../`: PCAP gốc, cookie và state riêng; không push.
- `docs/minh-chung/phan3/run-.../`: kết quả đã che cookie, metadata, hash và dấu `COMPLETE.txt`; chỉ chia sẻ sau rà soát.
- Nếu lỗi giữa chừng, kết quả dở dang ở `.local/`; không coi đó là lần đạt.

Tạo HTML cho lần chạy mới, thay `run-...` bằng tên thực tế được in khi hoàn tất:

```powershell
python scripts/part3/Build-Part3Report.py docs/minh-chung/phan3/run-...
```

Đọc mô hình, nguồn cookie, cách mở PCAP và các bộ lọc tại [báo cáo chung — kịch bản](ATMMT_nhom05.md#kich-ban) và [phụ lục B](ATMMT_nhom05.md#phu-luc-b). HTTP dùng cookie lấy từ mạng thật; tình huống HTTPS dùng cookie đã biết tại endpoint để mô phỏng lộ cookie, không trích cookie từ TLS PCAP.

## 5. Checklist người nhận và quan sát trình duyệt

Các mục dưới đây là việc cần thành viên nhận tự thực hiện và ghi kết quả, không phải khẳng định đã kiểm tra trên máy đó. Thực hiện riêng từng bản với phiên mới; Alice và Bob dùng browser profile độc lập vì hai tab cùng profile thường chia sẻ cookie.

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

Phần 3 đã kiểm chứng client B dùng lại cookie, đối chiếu PCAP bằng TShark và lưu ảnh/video trình bày kết quả trong [báo cáo chung — kết quả](ATMMT_nhom05.md#ket-qua). Bằng chứng DevTools và video quay thao tác trực tiếp chưa được ghi; giữ riêng trạng thái này với kết quả bằng Java client.

## 6. Kết quả đã có và phạm vi xác nhận

### 6.1. Kiểm tra lại phần ứng dụng từ Git

Mã nguồn của lần kiểm tra checkout sạch: commit `65b583536ec2f8c8141c0ab744c9ade9ebc05fdf`. Đây là mốc kiểm chứng lịch sử, không phải khẳng định mọi thay đổi tài liệu hiện tại đã được push.

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

### 6.2. Thực nghiệm đường truyền

Ngày 14/10/2026, lần `run-063d980c01c2` dùng ứng dụng từ commit `11e1ce5973b37b551ce335b511bcc6c0c41f9ca3`, đạt 9/9 tình huống T01–T09. Kết quả gồm cookie HTTP đọc được và dùng lại thành công, đối chiếu TLS 1.3, thuộc tính cookie HTTPS, logout, timeout và đổi session ID.

PCAP HTTP ghép có 70 gói, gồm 10 request được giải mã. HTTPS có 200 gói, 83 frame chứa bản ghi TLS và 59 frame chứa loại ngoài 23; không giải mã ra header cookie, không thấy chuỗi cookie đã biết trong byte PCAP. Loại ngoài 23 không xác định nội dung mã hóa bên trong. Xem [bảng kết quả và bằng chứng](ATMMT_nhom05.md#ket-qua).

TShark là Wireshark CLI; chưa có ảnh Wireshark GUI/DevTools hoặc video quay trực tiếp quá trình bắt gói. Không suy ra kết quả nghe lén LAN, mọi quy tắc cookie trên trình duyệt hay khả năng chống mọi XSS/CSRF từ các phép thử này.

## 7. Trạng thái bàn giao và việc nhóm cần hoàn thiện

| Việc | Trạng thái |
|---|---|
| Ứng dụng, Docker, HTTPS và công cụ thực nghiệm | Đã triển khai trong thư mục làm việc; cần đưa đầy đủ bản cập nhật lên repository trước khi thành viên khác nhận |
| Báo cáo tổng hợp và hướng dẫn bàn giao chung | Đã chuẩn bị trong hai file `ATMMT_nhom05.md` và `BAN_GIAO_NHOM05.md` |
| Kiểm chứng ứng dụng trên máy phát triển | Có bằng chứng 13 kiểm tra HTTP và 24 kiểm chứng HTTPS |
| Thực nghiệm đường truyền trên máy phát triển | T01–T09 đạt; có bảng kết quả, ảnh và video trình bày |
| Thành viên nhận chạy trên máy riêng | Chưa có xác nhận; mỗi người tự chạy và ghi kết quả |
| Ảnh DevTools hoặc quay demo trực tiếp | Chưa có; bổ sung nếu giảng viên yêu cầu |
| Thông tin nhóm, bìa và hình thức nộp báo cáo | Cần bổ sung họ tên/MSSV, trường/lớp, giảng viên và định dạng theo mẫu |
| Slide và tập bảo vệ | Cả nhóm thực hiện; tham khảo [câu hỏi bảo vệ](ATMMT_nhom05.md#phu-luc-c) và [kịch bản demo](ATMMT_nhom05.md#phu-luc-d) |

Người nhận ghi ngày, hệ điều hành, phiên bản Java/Docker, kết quả từng bước và lỗi nếu có. Không thay trạng thái chưa xác nhận bằng kết quả mong đợi. Mỗi thành viên đọc toàn bộ luồng, đặc biệt sự khác nhau giữa nguồn cookie HTTP và giả định lộ cookie HTTPS.

## 8. File chia sẻ và file giữ riêng

Đưa lên Git mã nguồn, Docker/Maven, các script, Markdown và bằng chứng đã loại thông tin phiên. Giữ riêng `.local/`, `.p12`/`.pfx`, `runtime.env`, `target/`, file bắt gói có dữ liệu phiên và log chưa rà soát.

Đối với chứng chỉ tự tạo, chia sẻ `.cer` công khai nếu một client lab khác cần tin cậy server đó, đồng thời đối chiếu fingerprint qua kênh tin cậy. Không gửi `.p12` hoặc mật khẩu keystore để giúp người khác chạy project.

PCAP gốc của lần chạy hiện có được giữ tại `.local/part3/run-063d980c01c2/` trên máy thực nghiệm. Đối chiếu với [manifest SHA-256](minh-chung/phan3/run-063d980c01c2/capture-manifest.json) khi cần giảng viên kiểm tra; không đưa PCAP chứa cookie, CSRF token hoặc nội dung đăng nhập lên repository. Log, ảnh và video mới cần được rà soát và che dữ liệu phiên trước khi chia sẻ.

## 9. Dừng ứng dụng và xử lý vướng mắc

Chạy trong thư mục `session-hijacking-lab`:

```powershell
docker compose stop
docker compose -f compose.https.yaml stop
```

Session nằm trong bộ nhớ tiến trình; dừng/khởi động lại ứng dụng làm mất phiên đang hoạt động. Nếu không kết nối được, kiểm tra Docker Desktop, log của đúng bản HTTP/HTTPS, cổng host, bộ chứng chỉ và hostname theo [README ứng dụng](../README.md). Không xử lý lỗi bằng cách tắt xác minh TLS, đưa server ra toàn mạng, reset dữ liệu Docker hoặc xóa thay đổi Git.

Sau khi người phụ trách push bản cập nhật, nhóm nhận link GitHub kèm tài liệu này và báo cáo chung. File không xuất hiện trên máy nhận cần được đối chiếu với trạng thái push và nhánh đang dùng.
