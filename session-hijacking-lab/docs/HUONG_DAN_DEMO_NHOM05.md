# HƯỚNG DẪN MỞ PROJECT VÀ CHẠY DEMO — NHÓM 05

**Ngày soạn:** 17/10/2026.<br>
**Môi trường hướng dẫn:** Windows, Docker Desktop với Linux containers, PowerShell.<br>
**Mục tiêu:** mở mã nguồn, chạy ứng dụng ghi chú, trình diễn Session Hijacking trong lab và giải thích kết quả.

Tài liệu đi cùng: [test case](TEST_CASES_NHOM05.md), [khung slide](KHUNG_SLIDE_NHOM05.md), [báo cáo chung](ATMMT_nhom05.md), [bàn giao](BAN_GIAO_NHOM05.md).

**Thứ tự demo đề xuất:** bật app ở mục 3, thực hiện demo trình duyệt mục 5, chạy bắt gói thật mục 6, rồi quan sát HTTPS ở mục 7. Mục 5 dành cho thao tác thủ công trên Brave/Edge và có điểm dừng để xác nhận từng bước. Build image và kiểm tra chứng chỉ trước buổi trình bày theo mục 4.

## 1. Mở đúng project

### Máy hiện tại

Thư mục repository: `C:\Users\PC\Downloads\ATMMT`.<br>
Thư mục ứng dụng Maven: `C:\Users\PC\Downloads\ATMMT\session-hijacking-lab`.

- Trong IDE, mở thư mục `session-hijacking-lab` hoặc import file `pom.xml` của thư mục đó như một project Maven.
- Đặt Project SDK/JDK là Java 17 hoặc bản tương thích trở lên. Project biên dịch ở mức Java 17.
- Đợi IDE tải Maven dependencies nếu muốn đọc/chạy trực tiếp trong IDE.
- Chạy Docker bằng terminal theo mục 3 thì không cần bấm Run trong IDE. Việc mở IDE chỉ giúp nhóm xem mã nguồn.

Các file nên mở sẵn khi giải thích:

| File | Giải thích |
|---|---|
| [SecurityConfig.java](../src/main/java/vn/edu/atmmt/sessionlab/config/SecurityConfig.java) | Form login, quyền truy cập, CSRF mặc định và logout |
| [PageController.java](../src/main/java/vn/edu/atmmt/sessionlab/web/PageController.java) | Lấy danh tính từ principal |
| [NoteService.java](../src/main/java/vn/edu/atmmt/sessionlab/notes/NoteService.java) | Hai bộ ghi chú theo người dùng |
| [Profile HTTPS](../src/main/resources/application-https.properties) | TLS, cookie Secure và timeout 1 phút |
| [Part3Lab.java](../scripts/part3/Part3Lab.java) | Hai client độc lập, phát lại cookie và kiểm chứng vòng đời |

### Máy thành viên chưa có repository

Mở Git Bash tại nơi muốn lưu project:

```bash
git clone https://github.com/HieuDao1410/ATMMT.git
cd ATMMT/session-hijacking-lab
```

Sau đó mở **PowerShell** tại thư mục ứng dụng vừa clone và dùng các lệnh bên dưới. Đường dẫn trên máy khác phụ thuộc nơi clone; không sao chép nguyên đường dẫn `C:\Users\PC\...` của máy hiện tại. Nếu đã clone, bảo toàn thay đổi riêng trước khi cập nhật.

## 2. Chuẩn bị trước buổi thuyết trình

Làm các bước build/tạo chứng chỉ và thử demo từ trước, tránh tải image hoặc Maven dependencies trong giờ bảo vệ.

Trên máy hiện tại, mở PowerShell:

```powershell
Set-Location 'C:\Users\PC\Downloads\ATMMT\session-hijacking-lab'
docker version
docker compose version
java -version
javac -version
keytool -help
python --version
```

| Công cụ | Khi nào cần |
|---|---|
| Docker Desktop/Compose | Build/chạy app và bắt gói bằng công cụ Docker |
| JDK 17 trở lên trong PATH | Chạy script tạo chứng chỉ, Java HTTPS và thực nghiệm |
| Python 3 | Tạo HTML cho lần thực nghiệm mới hoặc mở server xem bằng chứng |
| Trình duyệt | Xem ứng dụng, cookie và báo cáo |
| IDE | Tùy chọn để đọc code, không bắt buộc cho Docker demo |

`docker version` phải có phần Server. Khi Docker chưa hoạt động, mở Docker Desktop và chờ engine sẵn sàng. Chỉ kiểm tra `java -version` chưa đủ xác nhận có JDK; `javac` và `keytool` cũng cần hoạt động.

Chuẩn bị một profile trình duyệt A và một profile độc lập B. Có thể dùng hai profile riêng hoặc hai trình duyệt. Hai tab cùng profile không tạo hai kho cookie riêng.

## 3. Bật ứng dụng HTTP và HTTPS bằng Docker

Tất cả lệnh phần này chạy ở **PowerShell tại `session-hijacking-lab`**.

### 3.1. HTTP

```powershell
docker compose up --build -d
docker compose ps
docker compose logs --tail 40 app
```

Mở `http://127.0.0.1:8080`. Chờ log ứng dụng khởi động hoàn tất và trang chủ/login tải được, không chỉ dựa vào trạng thái container `running`.

### 3.2. Chứng chỉ HTTPS

Kiểm tra các file cần thiết mà không in mật khẩu:

```powershell
Test-Path .local/tls/localhost.cer
Test-Path .local/tls/localhost.p12
Test-Path .local/tls/runtime.env
```

Nếu **cả ba chưa tồn tại**, tạo bộ mới một lần:

```powershell
.\scripts\New-LabCertificate.ps1
```

Nếu đủ cả ba, dùng bộ hiện có. Nếu thiếu một phần hoặc chứng chỉ hết hạn, dừng tại bước này và xử lý theo [README về chứng chỉ](../README.md), không ghi đè khóa hoặc xóa `.local/` tùy ý. Mỗi máy tự tạo chứng chỉ; Git không có khóa riêng và mật khẩu keystore.

### 3.3. HTTPS

```powershell
docker compose -f compose.https.yaml up --build -d
docker compose -f compose.https.yaml ps
docker compose -f compose.https.yaml logs --tail 40 app
```

URL: `https://127.0.0.1:8443`. Dùng đúng `https`, không mở `http` ở cổng 8443. Hai Compose là hai project riêng, không ghép hai file trong cùng lệnh.

Để dùng trình duyệt không có cảnh báo, người dùng cần đối chiếu fingerprint rồi tin cậy đúng chứng chỉ công khai `.cer` theo [README ứng dụng](../README.md). Không bỏ qua cảnh báo để coi là đã cấu hình TLS đúng. Công cụ Java ở mục 4 dùng kho tin cậy riêng, không cần nhập chứng chỉ vào Windows Root.

### 3.4. Tài khoản và điểm cần nhớ

| Tài khoản | Mật khẩu lab | Dữ liệu |
|---|---|---|
| `alice` | `AliceLab!2026` | 2 ghi chú của Alice |
| `bob` | `BobLab!2026` | 2 ghi chú của Bob |

HTTP dùng `JSESSIONID`, HTTPS dùng `__Host-LABSESSION_HTTPS`. Timeout HTTPS là 1 phút không hoạt động, HTTP là 20 phút. Dùng nhất quán `127.0.0.1` khi demo, không đổi qua `localhost` giữa chừng vì kho cookie theo hostname khác nhau.

## 4. Kiểm tra thử trước buổi demo

### Kiểm tra HTTP

```powershell
.\mvnw.cmd test
```

Chờ Maven hoàn tất; xem số test, failures/errors thực tế. JUnit dùng cổng ngẫu nhiên nên không cần dừng app Docker. Danh sách kỳ vọng 13 test được ghi trong [test case](TEST_CASES_NHOM05.md).

### Kiểm chứng HTTPS

```powershell
java scripts/VerifyHttpsLab.java .local/tls/localhost.cer
```

Phép thử có 75 giây ngừng request. Chỉ ghi đạt khi chương trình chạy hết và hiện `RESULT: 24 checks passed...`. Lệnh này không bắt gói; phần bắt gói dùng mục 6.

### Chuẩn bị image bắt gói

```powershell
docker build -t session-lab-packet-tools:local scripts/part3/packet-tools
```

Đây là công cụ của lab chạy trong Docker, không cài driver bắt gói trên Windows. Lần build đầu cần Internet. Nếu định demo trực tiếp mục 6, nên chạy thử cả script ít nhất một lần trước buổi bảo vệ và ghi kết quả mới theo mẫu test case.

## 5. Demo thủ công từng bước trên Brave và Edge, khoảng 4–5 phút

### 5.1. Chuẩn bị hai client và kiểm tra ứng dụng

**Vai trò cố định để dễ theo dõi:**

| Client | Cửa sổ dùng trong hướng dẫn | Trạng thái trước khi sao chép |
|---|---|---|
| A | Brave, profile thường | Đăng nhập Alice |
| B | Edge, profile riêng với Brave | Chưa đăng nhập |

Có thể thay Edge bằng một browser profile độc lập khác. Hai tab trong cùng profile hoặc hai cửa sổ cùng kho cookie không phù hợp. A/B là hai client; sau khi sao chép thành công, cả hai dùng chung một phiên Alice phía server.

#### Bước 1 — Đưa cả hai client về trạng thái ban đầu

1. Mở Brave và Edge, đặt hai cửa sổ cạnh nhau để nhận diện A/B.
2. Tại từng cửa sổ, mở đúng `http://127.0.0.1:8080/me`.
3. Nếu đang thấy một tài khoản đã đăng nhập, bấm nút **Đăng xuất** trong ứng dụng. Chỉ làm bước chuẩn bị này trước khi đăng nhập Alice ở bước tiếp theo.
4. Xác nhận cả hai cửa sổ hiển thị form login HTTP. Có thể mở trực tiếp `http://127.0.0.1:8080/login` để chuẩn bị.

**Điểm dừng:** A/B đều đang ở bản HTTP cổng 8080. Không cần xóa cookie hoặc sửa tên cookie để bắt đầu.

#### Bước 2 — A đăng nhập Alice

Ở **Brave A**, nhập `alice` / `AliceLab!2026`, bấm **Đăng nhập**.

**Phải thấy:** URL `http://127.0.0.1:8080/me`, tiêu đề “Xin chào, alice.” và Tên đăng nhập là `alice`.

**Nói khi demo:** “Alice đăng nhập bình thường. Server lưu trạng thái xác thực của Alice trong session.”

#### Bước 3 — Chỉ dữ liệu riêng của Alice

Ở **Brave A**, mở `http://127.0.0.1:8080/notes`, rồi mở `http://127.0.0.1:8080/notes?username=bob`.

**Phải thấy:** cả hai lần chỉ có 2 ghi chú của Alice. Sau đó quay lại `http://127.0.0.1:8080/me`.

**Nói:** “Server chọn chủ sở hữu từ principal đã xác thực. Tham số username trên URL không đổi danh tính.”

#### Bước 4 — Chứng minh B chưa đăng nhập

Ở **Edge B**, mở `http://127.0.0.1:8080/me`.

**Phải thấy:** bị chuyển đến form login, chưa có danh tính Alice. Giữ B tại `http://127.0.0.1:8080/login` và không nhập tài khoản.

**Nếu B đã là Alice ngay ở đây:** dừng bước sao chép. Kiểm tra hai cửa sổ có thật sự là hai kho cookie độc lập và đã kết thúc các phiên thử trước đó hay chưa. Chuẩn bị lại từ bước 1.

### 5.2. Sao chép cookie của A sang B

Đoạn này mô phỏng cookie đã lộ tại endpoint của ứng dụng giả. Bắt cookie từ gói mạng thật được trình diễn riêng ở mục 6.

**Cả A và B phải dùng bản HTTP `http://127.0.0.1:8080`.** Không dùng A ở 8080 và B ở HTTPS 8443: hai ứng dụng có kho session riêng và đọc tên cookie khác nhau.

| Client | Trang dùng trong bước này | Cookie cần chọn |
|---|---|---|
| A đã đăng nhập Alice | `http://127.0.0.1:8080/me` | `JSESSIONID` |
| B chưa xác thực | `http://127.0.0.1:8080/login` | `JSESSIONID` |

#### Bước 5 — Mở bảng cookie tại A

Ở **Brave A**, đang đăng nhập Alice trên HTTP:

1. Nhấn **F12** hoặc **Ctrl+Shift+I**.
2. Bấm tab **Application**. Nếu chưa thấy, tìm trong dấu `>>` ở cuối hàng tab.
3. Ở cột trái, tìm **Cookies** trong nhóm Storage. Bấm mũi tên cạnh Cookies để mở rộng.
4. Bấm đúng dòng **`http://127.0.0.1:8080`** bên dưới Cookies. Không dừng ở trang tổng quan Storage có nút “Clear selected”.
5. Trong bảng bên phải, tìm dòng có **Name = `JSESSIONID`**.

```text
Application
  Storage
    Cookies
      http://127.0.0.1:8080   ← Chọn địa chỉ này
```

**Điểm dừng:** bảng có cột Name, Value và dòng `JSESSIONID`. Nếu bảng không có dòng này, kiểm tra lại URL và trạng thái đăng nhập Alice trước khi tiếp tục.

#### Bước 6 — Sao chép toàn bộ Value của A

1. Ở dòng **`JSESSIONID`**, bấm đúp **ô Value** để mở chỉnh sửa giá trị.
2. Khi con trỏ đang trong ô Value, nhấn **Ctrl+A**, rồi **Ctrl+C** để lấy toàn bộ chuỗi, kể cả phần đang bị bảng thu gọn bằng dấu `…`.
3. Nhấn **Esc** để thoát chỉnh sửa, giữ nguyên giá trị của A.

**Không sửa ô Name.** Sau bước này Name vẫn phải là `JSESSIONID`. Không đăng xuất A và không khởi động lại app trong lúc chuẩn bị B. Không chụp/quay giá trị phiên đầy đủ để chia sẻ.

#### Bước 7 — Dán vào đúng ô tại B

Ở **Edge B**, vẫn đang ở `http://127.0.0.1:8080/login`:

1. Mở F12, chọn Application → Storage → Cookies → **`http://127.0.0.1:8080`**.
2. Tìm đúng dòng **Name = `JSESSIONID`**. Nếu chưa có, tải lại trang login HTTP một lần rồi xem lại.
3. Bấm đúp **ô Value** của dòng này.
4. Khi con trỏ ở trong ô Value, nhấn **Ctrl+A**, **Ctrl+V**, rồi **Enter để lưu**.
5. Kiểm tra Name vẫn là `JSESSIONID`, Path là `/`. Không thay dòng `__Host-LABSESSION_HTTPS`.

**Điểm dừng:** chỉ Value của `JSESSIONID` ở B đã nhận chuỗi của A. Không logout tại A hoặc B trước bước 8.

#### Bước 8 — B sử dụng phiên Alice

Ở **Edge B**, mở chính xác **`http://127.0.0.1:8080/me`**. Có thể đóng DevTools trước để không trình chiếu cookie.

**Phải thấy:** “Xin chào, alice.” và Tên đăng nhập là `alice`, dù B không nhập mật khẩu Alice. B cũng mở được `http://127.0.0.1:8080/notes` và thấy ghi chú Alice.

**Nói:** “Ban đầu B chưa đăng nhập. Khi B gửi cookie phiên của Alice còn hiệu lực, server nhận diện B là Alice. Hai client lúc này cùng sử dụng một phiên phía server.”

Nếu B vẫn ở login, không đăng nhập Alice tại B để làm cho kết quả trông như đạt. Kiểm tra đúng HTTP 8080, đúng Value đầy đủ, đã Enter để lưu, tên `JSESSIONID`, và phiên A vẫn còn sống. Sau đó lấy lại cookie hiện tại từ A và thử lại.

### 5.3. Chứng minh logout hủy cả bản sao cookie

#### Bước 9 — A logout

Sau khi B đã hiện Alice, ở **Brave A** bấm **Đăng xuất** trong ứng dụng.

**Phải thấy:** A quay về login và có thông báo “Bạn đã đăng xuất.”

#### Bước 10 — B thử dùng lại phiên cũ

Ở **Edge B**, giữ nguyên cookie và mở lại **`http://127.0.0.1:8080/me`**.

**Phải thấy:** B phải đăng nhập lại.

**Nói:** “Logout đã hủy phiên ở server. B còn bản sao cookie nhưng phiên đó không còn quyền truy cập.”

Nếu muốn lặp lại demo, A cần đăng nhập lại và nhóm phải sao chép Value mới. Không dùng mã phiên đã bị logout để đánh giá lần thử sau.

DevTools có thể hiện cả `JSESSIONID` và `__Host-LABSESSION_HTTPS` do cookie không được cô lập theo số cổng. Việc thấy cả hai dòng không có nghĩa hai server dùng chung phiên; phải chọn đúng tên cookie và URL như bảng trên. Nếu muốn thử bản HTTPS riêng, cần đăng nhập A ở HTTPS trước, sao chép đúng cookie HTTPS sang B ở HTTPS và thực hiện trước khi phiên hết timeout 1 phút.

**Cách nói chính xác:** “Ở đoạn thủ công này nhóm giả định cookie đã lộ tại client. Phần bắt gói tiếp theo mới chứng minh lấy cookie từ HTTP trên mạng lab.” Không gọi thao tác sao chép DevTools là lấy cookie từ PCAP, và không dùng `document.cookie` để tìm cookie HttpOnly.

**Sau phần thủ công:** chuyển sang mục 6 để trình diễn đường lộ cookie thật và các phép kiểm chứng còn lại. Java clients A/B do script tạo có kho cookie riêng với Brave/Edge, nên các dòng PASS của script không thay đổi danh tính của cửa sổ trình duyệt bạn đang mở.

## 6. Demo bắt gói và phát lại thật, khoảng 2–3 phút sau khi đã build

Giữ app HTTP/HTTPS chạy. Mở PowerShell tại thư mục ứng dụng, không đặt cổng host khác mặc định 8080/8443:

```powershell
.\scripts\part3\Run-Part3.ps1
```

Nếu Windows PowerShell báo `running scripts is disabled`, kiểm tra `Get-ExecutionPolicy -List`. Khi `MachinePolicy` và `UserPolicy` đều `Undefined`, có thể chạy script cục bộ trong một tiến trình riêng:

```powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\part3\Run-Part3.ps1
```

Chính sách này chỉ áp dụng cho tiến trình vừa mở, không đổi chính sách lâu dài của máy. Không ghi đè Group Policy nếu máy do đơn vị quản lý. Xem [Microsoft — Execution policies](https://learn.microsoft.com/powershell/module/microsoft.powershell.core/about/about_execution_policies?view=powershell-5.1).

| Giai đoạn trên terminal | Chỉ cho người xem | Giải thích |
|---|---|---|
| T01 và T09-HTTP | B chưa xác thực, ID đổi khi A login | Hai client có kho cookie riêng |
| T03 rồi T02 | Cookie được trích từ gói đúng marker, B được nhận diện Alice | B dùng file do TShark trích từ PCAP, không đọc cookie jar của A để phát lại |
| T05, T09-HTTPS, T06 | Thuộc tính cookie và B dùng lại phiên HTTPS còn sống | Cookie HTTPS này đã biết tại endpoint, không lấy từ TLS PCAP |
| T07 | A logout, các client mất quyền | Server hủy trạng thái phiên |
| Tiến độ 15/75 đến 75/75 giây | Không có request trong khoảng chờ | Timeout là không hoạt động |
| T08, T04, COMPLETE | Mất quyền sau timeout và kết quả đối chiếu TLS | Không cấp khóa giải mã cho TShark; chỉ công bố sau khi đủ điều kiện |

Script bắt gói tại `eth0` trong network namespace của từng app Docker, sau chuyển tiếp cổng của Docker Desktop và trước Tomcat giải mã TLS. Không phải bắt Npcap trên Windows loopback hoặc nghe lén LAN.

Khi hoàn tất, terminal in:

```text
COMPLETE: .../docs/minh-chung/phan3/run-...
PRIVATE PCAPS: .../.local/part3/run-...
```

Thay `run-...` bằng **mã thực tế của lần mới** để tạo HTML:

```powershell
python scripts/part3/Build-Part3Report.py docs/minh-chung/phan3/run-...
```

Mở `index.html` của thư mục mới bằng trình duyệt. Chỉ tên file, marker, status và fingerprint được công khai; không mở file cookie/state riêng để trình chiếu. Các số frame, SHA-256, thời gian và số gói thay đổi giữa các lần chạy.

## 7. Demo HTTPS trên trình duyệt

Chỉ thực hiện sau khi trình duyệt tin cậy đúng chứng chỉ:

1. Mở `https://127.0.0.1:8443/login`, DevTools → Network.
2. Xem Set-Cookie từ login, chỉ các thuộc tính Secure, HttpOnly, SameSite=Lax, Path=/ và tên `__Host-LABSESSION_HTTPS`. Che Value và CSRF token.
3. Đăng nhập, xem request `/me` để giải thích trình duyệt biết cookie tại endpoint dù gói TLS trên đường truyền đang mã hóa.
4. Dùng kết quả T06 để chỉ rõ: client khác đã có cookie còn sống vẫn có thể được xác thực. Không nói HTTPS “chặn toàn bộ Session Hijacking”.
5. Dùng T07/T08/T09 để giải thích hủy phiên, timeout và đổi ID. Nếu tự demo timeout bằng trình duyệt thì dừng mọi request/refresh từ tất cả client dùng phiên đó trong 75 giây.

Quan sát header chưa chứng minh đầy đủ chính sách cookie trên trình duyệt khác site. Chỉ ghi ảnh DevTools là đã thực hiện khi nhóm thật sự quan sát và lưu ảnh đã che thông tin phiên.

## 8. Mở bằng chứng có sẵn và phương án dự phòng

Nếu muốn xem ngay bộ kết quả **ngày 14/10/2026**, mở bằng File Explorer:

`session-hijacking-lab/docs/minh-chung/phan3/run-063d980c01c2/index.html`.

Hoặc mở terminal PowerShell riêng, chạy server chỉ phục vụ thư mục bằng chứng đã che:

```powershell
python -m http.server 8090 --bind 127.0.0.1 --directory docs/minh-chung/phan3/run-063d980c01c2
```

Mở `http://127.0.0.1:8090`. Nếu 8090 đã dùng, kiểm tra dịch vụ đang phục vụ đúng báo cáo hoặc đổi riêng cổng xem báo cáo, ví dụ 8091; không dừng tiến trình khác tùy ý. Giữ terminal này chạy trong lúc xem, dừng bằng Ctrl+C khi xong.

Có ảnh [HTTP](minh-chung/phan3/run-063d980c01c2/frame-01.jpg), [HTTPS](minh-chung/phan3/run-063d980c01c2/frame-02.jpg), [vòng đời phiên](minh-chung/phan3/run-063d980c01c2/frame-03.jpg) và [video 24 giây](minh-chung/phan3/run-063d980c01c2/demo-ket-qua.mp4) trong cùng thư mục. Khi dùng dự phòng, nói rõ: “Đây là kết quả đã ghi ngày 14/10, không phải lần chạy trực tiếp hôm nay.” Video trình bày lại kết quả, không ghi quá trình bắt gói.

**Chốt kiểm tra trước khi lên trình bày:** trang 8080 tải được, HTTPS được Java xác minh, image công cụ đã build, đã thử script, đã mở báo cáo/video offline và biết mỗi người phụ trách đoạn nào. Bảng slide, lời nói và thời lượng gợi ý nằm trong [khung slide](KHUNG_SLIDE_NHOM05.md).

## 9. Lỗi thường gặp

| Hiện tượng | Kiểm tra và hướng xử lý |
|---|---|
| Docker không có Server hoặc không kết nối daemon | Mở Docker Desktop và chờ engine. Nếu lỗi `sailor-ingest.sock`, xem mục tương ứng trong README; không reset dữ liệu Docker |
| `java`, `javac` hoặc `keytool` không nhận | Kiểm tra JDK và PATH/JAVA_HOME, mở terminal mới sau khi cấu hình. Docker app có thể vẫn chạy nhưng Java client sẽ không chạy được |
| Không tải được 8080/8443 hoặc báo port đang dùng | Xem `compose ps` và log đúng project. Không chạy thêm app IDE trên cùng cổng; dừng bản IDE mình đã mở nếu chọn Docker |
| `.ps1` bị policy chặn | Xem `Get-ExecutionPolicy -List`. Theo chính sách máy, ưu tiên PowerShell/terminal được phép chạy script. Không tự vô hiệu chính sách toàn máy để demo |
| `NativeCommandError` tại `docker logs`, dù có `tcpdump: listening on eth0` | Bản script ngày 17/10 đã xử lý stderr của công cụ native trên Windows PowerShell 5.1 và vẫn kiểm tra exit code. Dùng bản cập nhật; dòng listening là thông báo sẵn sàng |
| Báo không tìm thấy cookie dù PCAP có request | Dùng bản script ngày 17/10 đã tách từng phần tử mảng JSON trên PowerShell 5.1. Nếu vẫn lỗi, kiểm tra PCAP/marker trong `.local/`; không lấy cookie từ nguồn khác để thay thế |
| Thiếu `.local/tls/runtime.env` | Máy nhận chưa tạo đủ bộ chứng chỉ. Xem mục 3.2; không lấy mật khẩu/khóa riêng từ Git hoặc máy khác |
| HTTPS báo chứng chỉ không tin cậy/sai hostname/hết hạn | Đối chiếu bộ `.cer` với keystore đang chạy, hostname và hạn chứng chỉ. Không dùng trust-all hoặc bỏ qua TLS |
| Lấy cookie nhưng B chưa thành Alice | Kiểm tra đúng hostname, tên cookie theo bản HTTP/HTTPS, profile độc lập và phiên A còn sống. Không trộn cookie của hai bản |
| Chờ 75 giây nhưng vẫn login | Nếu đang dùng HTTP, timeout là 20 phút. Nếu HTTPS, kiểm tra request nền hoặc client khác đang giữ sống phiên |
| Script dừng giữa chừng | Đọc lỗi, giữ dữ liệu chẩn đoán trong `.local/`; không tự thêm COMPLETE hoặc dùng bảng kết quả dự kiến |
| Không thấy HTML/video trên GitHub như trang web | Clone/tải repository và mở cục bộ, hoặc dùng server chỉ phục vụ thư mục bằng chứng ở mục 8 |

## 10. Cách chạy trực tiếp không dùng Docker và cách dừng

Nếu chỉ muốn mở giao diện HTTP bằng JDK, tại thư mục ứng dụng:

```powershell
.\mvnw.cmd spring-boot:run
```

Lệnh này giữ terminal chạy; dừng bằng Ctrl+C. Không chạy cùng bản Docker HTTP trên cổng 8080. Chọn lại Docker khi thực hiện mục 6 vì script cần network namespace của app container. Chạy HTTPS trực tiếp có hướng dẫn riêng trong [README](../README.md).

Dừng hai app Docker sau buổi demo:

```powershell
docker compose stop
docker compose -f compose.https.yaml stop
```

Session nằm trong bộ nhớ nên dừng/khởi động lại tiến trình làm mất phiên cũ. Không xóa `.local/`, khóa chứng chỉ hoặc bằng chứng để kết thúc demo. Khi cập nhật Git, chỉ đưa tài liệu/script và bằng chứng đã rà soát; giữ riêng `.local/`, `target/`, `.p12`, `.pfx`, `runtime.env` và PCAP gốc.

## 11. Demo phần bổ sung OWASP

Thực hiện **sau kịch bản Session Hijacking chính**, khi container đã được build lại từ mã mới theo [README mục 13](../README.md#owasp-bo-sung). Các bước dưới đây là hướng dẫn thao tác, chưa có bằng chứng chạy cho phần OWASP. HTTP và HTTPS có bộ đếm riêng; dùng HTTP xuyên suốt đoạn này để tránh nhầm app hoặc để timeout HTTPS làm đổi phiên.

### 11.1. Giới hạn đăng nhập sai

1. Đăng xuất HTTP nếu đang có phiên; mở `http://127.0.0.1:8080/login`.
2. Nhấn F12 → **Network**, bật **Preserve log** để giữ các phản hồi redirect.
3. Nhập `alice` / `wrong-password`, gửi form **5 lần** trong 5 phút. Mỗi lần chờ trang login trả về rồi mới gửi tiếp, để form luôn có CSRF hợp lệ. Năm lần này báo lỗi chung và POST `/login` trả 302 đến `/login?error`.
4. Ngay sau lần thứ 5, thử `alice` với mật khẩu đúng `AliceLab!2026`. POST `/login` sẽ nhận **429** và trang “Vui lòng chờ một chút.”; chưa đăng nhập. Chọn request trong Network → **Headers** để xem `Retry-After` và `Cache-Control: no-store`.
5. Mở trình duyệt B tại cùng địa chỉ HTTP, thử Alice: vẫn chịu hạn mức của Alice. Trình duyệt mới không xóa bộ đếm. Bob có bộ đếm riêng và có thể đăng nhập nếu Bob chưa bị chặn.
6. Chờ ít nhất **60 giây tính từ lần sai thứ 5**. Việc thử lại trong lúc chờ không kéo dài thời gian chặn. Bấm “Quay lại đăng nhập” để lấy form rồi đăng nhập Alice đúng mật khẩu; vào `/me`.

Lời giải thích: “Giới hạn này giảm việc đoán mật khẩu qua endpoint đăng nhập. Cookie còn sống đã bị lộ vẫn có thể được dùng lại vì request đó không cần nhập mật khẩu.” Không gọi phần này là biện pháp ngăn toàn bộ Session Hijacking.

### 11.2. Quan sát log bảo mật

Tại PowerShell trong `session-hijacking-lab`:

```powershell
docker compose logs --tail 100 app | Select-String 'security_event='
```

Quan sát các sự kiện `LOGIN_FAILURE`, `LOGIN_LIMIT_REACHED`, `LOGIN_THROTTLED`, `LOGIN_SUCCESS`; logout hợp lệ có `LOGOUT`. `LOGIN_FAILURE` của lần thứ 5 và `LOGIN_LIMIT_REACHED` có cùng `request_id`. Timestamp do Spring Boot thêm vào, UUID chỉ dùng nối log cùng request và không phải mã phiên.

Log chỉ có sự kiện, UUID, địa chỉ kết nối đã làm sạch, nhãn route và method; không có username, mật khẩu, cookie, CSRF/token, body/query hoặc nội dung exception. Địa chỉ Docker có thể là gateway. Che thêm địa chỉ nếu chia sẻ ảnh log ra ngoài nhóm. WARN là mức log, chưa phải hệ thống tự gửi cảnh báo.

### 11.3. Cách trình bày đối chiếu OWASP

Mở [báo cáo mục 8.1–8.3](ATMMT_nhom05.md#81-đối-chiếu-owasp-top-102025): giới thiệu bảng đủ 10 nhóm của **OWASP Top 10:2025**, tập trung A07 (xác thực), A09 (log/cảnh báo), A01 (quyền truy cập) và A04 (mật mã/TLS). Nêu rõ cột giới hạn: lab chưa có MFA, bộ đếm nhiều instance, quét CVE, log tập trung hoặc cảnh báo tự động. Đây là đối chiếu cơ chế hiện có, không phải chứng nhận an toàn toàn diện.
