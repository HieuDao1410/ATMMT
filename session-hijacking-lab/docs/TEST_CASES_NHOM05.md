# TEST CASE — SESSION HIJACKING LAB, NHÓM 05

**Ngày soạn:** 17/10/2026.<br>
**Mục đích:** kiểm tra trước khi demo và làm căn cứ ghi kết quả vào báo cáo.<br>
**Tài liệu liên quan:** [hướng dẫn mở/chạy demo](HUONG_DAN_DEMO_NHOM05.md), [báo cáo chung](ATMMT_nhom05.md).

## 1. Cách sử dụng và phạm vi

Bộ cơ sở mô tả **38 tình huống**: 13 HTTP, 10 nhóm HTTPS, 6 giao diện và 9 thực nghiệm T01–T09. Một số tình huống kiểm tra cùng cơ chế ở các môi trường khác nhau, không phải 38 kiểm tra tự động độc lập.

- H01–H13 ánh xạ đến 13 phương thức JUnit hiện có.
- S01–S10 nhóm các hành vi của `VerifyHttpsLab.java`; công cụ có 24 kiểm chứng, không tương ứng một-một với 10 dòng trong bảng.
- U01–U06 cần thao tác trình duyệt. Các kiểm chứng Java không xác nhận các mục giao diện này đã đạt.
- T01–T09 giữ nguyên mã của bộ thực nghiệm PCAP hiện có, dùng `Run-Part3.ps1`.

**Kết quả mới ngày 24/10/2026:** 21 JUnit (13 HTTP + 8 kiểm tra bộ giới hạn/log), 24 HTTPS, T01–T09 và 25 kiểm chứng OWASP đều đạt; xem mục 9.

**Kết quả lịch sử trên máy nhận:** nhóm thực nghiệm T01–T09 đã chạy lại bằng Windows PowerShell 5.1 ngày 17/10, đạt ở lần `run-043c53a32a5e` (mục 8). JUnit HTTP, công cụ 24 kiểm chứng HTTPS riêng và các mục giao diện chưa được chạy lại trong lượt sửa script này. Ghi PASS, FAIL, BLOCKED hoặc NOT RUN trong mẫu ở mục 7 sau khi thực hiện trên máy demo; không dùng kết quả lịch sử để điền thay.

Ưu tiên chạy nhóm HTTP, HTTPS và T01–T09 trước buổi bảo vệ. Kiểm tra giao diện sau khi xác nhận luồng xác thực hoạt động.

## 2. Điều kiện và dữ liệu chung

| Mục | Giá trị |
|---|---|
| HTTP | `http://127.0.0.1:8080`, profile `http-lab`, cookie `JSESSIONID` |
| HTTPS | `https://127.0.0.1:8443`, profile `https`, cookie `__Host-LABSESSION_HTTPS` |
| Alice | `alice` / `AliceLab!2026` |
| Bob | `bob` / `BobLab!2026` |
| Sai mật khẩu | `alice` / `wrong-password` |
| Dữ liệu riêng | Mỗi tài khoản có 2 ghi chú mẫu; không có tạo/sửa/xóa |
| Hai client | Hai browser profile độc lập hoặc Java clients có kho cookie riêng |
| Timeout HTTPS | 60 giây không hoạt động; chờ 75 giây để kiểm tra |
| Timeout HTTP | 20 phút; không áp dụng phép chờ 75 giây của HTTPS cho HTTP |

Mỗi tình huống bắt đầu với phiên mới, trừ các bước cố ý dùng cùng phiên. Không chạy song song các tình huống dùng chung cookie. Hai cửa sổ ẩn danh có thể chia sẻ kho cookie, nên không mặc nhiên coi đó là hai client độc lập.

**Sau bổ sung OWASP ngày 18/10:** bộ đếm đăng nhập sai gắn với tài khoản, độc lập với cookie/trình duyệt. Nếu đã cố ý gây chặn trước đó, chờ ít nhất 60 giây từ lần sai thứ 5 rồi lấy lại form đăng nhập; mở phiên mới không xóa bộ đếm. Các bằng chứng lịch sử ở mục 8 được tạo trước thay đổi này. Cách trình bày phần mới nằm ở [mục 11 hướng dẫn demo](HUONG_DAN_DEMO_NHOM05.md#11-demo-phần-bổ-sung-owasp).

Khi kiểm tra HTTP status, Java clients không tự theo redirect. Trình duyệt thường theo redirect đến trang login nên có thể chỉ thấy phản hồi cuối 200; mở Network để xem phản hồi 302 ban đầu.

## 3. HTTP và nghiệp vụ — H01 đến H13

Chạy tự động toàn bộ bảng này bằng Maven ở mục 6. Các thao tác POST thiếu/sai CSRF dùng helper trong JUnit; không thay form hợp lệ bằng một request tùy ý rồi kết luận ứng dụng bị lỗi.

| Mã | Điều kiện và bước thực hiện | Kết quả mong đợi | Phương thức JUnit |
|---|---|---|---|
| H01 | Client chưa đăng nhập. GET `/`, `/login`, `/css/app.css` | Cả ba trả 200 | `publicPagesAndStylesAreAccessible` |
| H02 | Client chưa đăng nhập. GET `/me` và `/notes` | 302, Location có path `/login` | `privatePagesRequireAuthentication` |
| H03 | Alice và Bob đăng nhập bằng hai kho cookie riêng, token từ form của chính client | POST login 302 đến `/me`; GET `/me` trả đúng danh tính, có `Cache-Control` chứa `no-store` | `validCredentialsAuthenticateBothLabUsers` |
| H04 | Lấy form/token hợp lệ, đăng nhập Alice với `wrong-password` | 302 đến `/login?error`; vẫn không vào `/me`; trang lỗi báo “Tên đăng nhập hoặc mật khẩu không đúng.” | `wrongPasswordDoesNotAuthenticate` |
| H05 | Alice GET `/notes?username=bob`; Bob GET `/notes?username=alice` | 200; mỗi client chỉ có ghi chú của principal hiện tại | `notesUseAuthenticatedOwnerEvenWhenRequestSuggestsAnotherUser` |
| H06 | Có cookie từ `/login`. POST `/login` đúng tài khoản nhưng bỏ `_csrf` | 403; chưa xác thực, `/me` vẫn yêu cầu login | `loginWithoutCsrfTokenIsRejected` |
| H07 | Lấy token từ client A, gửi token đó cùng cookie client B để POST login | 403; B chưa xác thực | `csrfTokenFromAnotherSessionIsRejected` |
| H08 | Lưu ID trước login, đăng nhập hợp lệ, giữ bản sao ID cũ để GET `/me` | ID sau login khác ID trước; phiên mới là Alice, ID chưa xác thực cũ không vào được `/me` | `sessionIdChangesAfterLoginAndOldAnonymousIdDoesNotAuthenticate` |
| H09 | A đăng nhập Alice. B gửi bản sao cookie còn hiệu lực mà không nhập mật khẩu | B GET `/me` trả 200 và `alice` | `copiedLiveSessionIdentifiesTheSameUserWithoutPassword` |
| H10 | A/B đang dùng chung phiên Alice. A POST logout với token hợp lệ, cả hai GET `/me` | Logout 302 đến `/login?logout`; A/B mất quyền; có thông báo “Bạn đã đăng xuất.” | `logoutInvalidatesTheSessionForBothOriginalAndCopiedCookie` |
| H11 | Alice đang đăng nhập. POST logout không có `_csrf` rồi GET `/me` | Logout 403; Alice vẫn đăng nhập | `logoutWithoutCsrfTokenIsRejectedAndSessionRemainsValid` |
| H12 | Alice đang đăng nhập. GET `/logout`, sau đó GET `/me` | GET không hủy phiên; vẫn nhận diện Alice. Không cố định status của GET `/logout` trong tiêu chí này | `getLogoutDoesNotInvalidateTheSession` |
| H13 | GET `/login`, xem Set-Cookie và HTML | `JSESSIONID` có HttpOnly, SameSite=Lax; HTML không chứa `;jsessionid=` | `sessionCookieHasHttpOnlySameSiteAndNoUrlRewriting` |

**Nguồn:** [JUnit HTTP hiện có](../src/test/java/vn/edu/atmmt/sessionlab/SessionHijackingLabApplicationTests.java). H09 chứng minh tác động khi B có cookie tại endpoint; đường lộ từ PCAP được kiểm tra riêng ở T02/T03.

## 4. HTTPS và quản lý phiên — S01 đến S10

Chạy bằng `VerifyHttpsLab.java` ở mục 6, dùng bộ chứng chỉ đúng với container đang chạy. Kho tin cậy của client chỉ chứa chứng chỉ được chỉ định; vẫn xác minh hostname.

| Mã | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|
| S01 | GET HTTPS `/login`, kiểm tra phiên TLS và `/js/app.js` | Login/script trả 200; giao thức TLS 1.2 hoặc 1.3; có header HSTS. Không suy ra HSTS preload hoặc mọi hành vi trình duyệt từ header |
| S02 | Kiểm tra Set-Cookie ở login HTTPS | Đúng tên `__Host-LABSESSION_HTTPS`, Secure, HttpOnly, SameSite=Lax, Path=/, không phát thuộc tính Domain |
| S03 | Client chưa xác thực GET `/me`; POST login bỏ `_csrf` | `/me` 302 về login; POST thiếu token 403 |
| S04 | Lấy token hợp lệ, đăng nhập Alice, so sánh ID, thử ID chưa xác thực cũ | Đăng nhập thành công, ID đổi; client mới là Alice, ID cũ không xác thực |
| S05 | B gửi bản sao cookie HTTPS còn sống đã biết tại endpoint | B được nhận diện Alice; đây là giới hạn của TLS khi cookie đã lộ từ nguồn khác |
| S06 | Gửi giá trị phiên HTTPS dưới tên `JSESSIONID` tới HTTPS `/me` | Không xác thực, 302 về login |
| S07 | Alice GET HTTPS `/notes?username=bob` | Chỉ có ghi chú Alice |
| S08 | A POST HTTPS logout thiếu token, sau đó GET `/me` | 403 ở logout; vẫn nhận diện Alice |
| S09 | A logout hợp lệ khi B giữ cookie sao chép; xem Set-Cookie, thử lại A/B | 302 đến `/login?logout`; server làm hết hạn đúng cookie HTTPS với Path=/ và Secure; A/B mất quyền. `Expires` trong quá khứ là cơ chế xóa hợp lệ, không bắt buộc phải thấy `Max-Age=0` |
| S10 | Tạo phiên Bob mới và bản sao tại B; ngừng request từ cả hai 75 giây, truy cập lại | Cả hai 302 về login; không có refresh/request nền giữ phiên trong khoảng chờ |

**Nguồn:** [VerifyHttpsLab.java](../scripts/VerifyHttpsLab.java). Kiểm tra header không thay cho kiểm tra chính sách Secure/HttpOnly/SameSite trên trình duyệt khác site.

## 5. Giao diện — U01 đến U06

Chạy thủ công bằng trình duyệt, bật JavaScript. Bảng này là tiêu chí cần kiểm tra, chưa có bằng chứng chạy mới đi kèm.

| Mã | Bước thực hiện | Kết quả mong đợi |
|---|---|---|
| U01 | Ở login, để trống username/password rồi submit | Trình duyệt chặn form theo `required`; không coi đây là kiểm chứng validation ở server |
| U02 | Nhập mật khẩu lab, bấm “Hiện” rồi “Ẩn” | Đổi hiển thị mà không đổi giá trị; nút không submit form, trạng thái `aria-pressed` thay đổi |
| U03 | Alice mở `/notes`, tìm `ke hoach`, rồi tìm `KẾ HOẠCH` | Cả hai lọc ra ghi chú “Kế hoạch của Alice”; tìm không phân biệt dấu/chữ hoa |
| U04 | Tìm `zzzz_khong_co_ghi_chu`, sau đó bấm “Xóa tìm kiếm” | Hiện “Chưa tìm thấy ghi chú”; xóa từ khóa khôi phục 2 ghi chú của Alice và focus ô tìm kiếm |
| U05 | Submit login/logout hợp lệ; kiểm tra trạng thái nút và quay lại trang bằng lịch sử nếu trình duyệt phục hồi trang | Có phản hồi đang gửi; không giữ nút disabled sau sự kiện `pageshow`. Nếu thao tác quá nhanh không quan sát được loading thì ghi nhận đúng giới hạn, không tự đánh FAIL |
| U06 | Dùng Tab/Enter ở login và notes; kiểm tra ở cửa sổ hẹp khoảng 390 px và desktop | Label/focus rõ, thao tác được bằng bàn phím; nội dung không bị cắt mất, các form/nút dùng được |

**Nguồn:** [login.html](../src/main/resources/templates/login.html), [notes.html](../src/main/resources/templates/notes.html), [app.js](../src/main/resources/static/js/app.js). Chưa có tạo/sửa/xóa ghi chú, nên không đưa CRUD vào tiêu chí đạt của phiên bản này. Trạng thái tài khoản không có ghi chú không thuộc bộ demo vì cả Alice/Bob đều có dữ liệu mẫu.

## 6. Thực nghiệm đường truyền T01–T09 và lệnh chạy

### 6.1. Test case thực nghiệm

Điều kiện chung: cả hai app Docker hoạt động, image `session-lab-packet-tools:local` đã build, JDK 17 trở lên, host port 8080/8443. Chạy trọn script theo thứ tự; T02 phụ thuộc cookie của T03, T07 phụ thuộc phiên HTTPS còn sống, T08 tạo phiên Bob riêng.

| Mã | Các bước chính | Kết quả mong đợi | Bằng chứng cần lưu |
|---|---|---|---|
| T01 | B chưa có phiên GET HTTP `/me` | 302 về login | Dòng T01 trong `events.tsv` |
| T02 | Sau T03, B đọc file cookie do TShark trích xuất và gửi HTTP `/me` | 200, danh tính Alice, B không nhập mật khẩu | T02, request `stage=replay`, liên kết PCAP seed/replay |
| T03 | A đăng nhập; gửi request chứa marker; tcpdump bắt gói, TShark trích `http.cookie` đúng marker | Lấy được `JSESSIONID` của phiên đã xác thực từ PCAP thật | T03, request `stage=seed`, manifest SHA-256. Frame không cố định giữa các lần chạy |
| T04 | Bắt TLS trước khi client kết nối; phân tích offline không có khóa giải mã, đối chiếu chuỗi cookie đã biết với byte PCAP | Có lưu lượng TLS/bản ghi ngoài loại 23; 0 header cookie được giải mã; chuỗi cookie đã biết không xuất hiện trong PCAP | T04, `packets-redacted.tsv`, manifest. Không suy số HTTP request từ số frame loại 23 |
| T05 | Client xác minh TLS đọc Set-Cookie | Các thuộc tính đúng như S02 | T05; ghi rõ client TLS, không ghi là ảnh DevTools |
| T06 | B dùng bản sao cookie HTTPS còn sống đã biết tại endpoint | B được nhận diện Alice | T06; ghi rõ cookie không lấy từ TLS PCAP |
| T07 | A logout HTTPS hợp lệ; A/B thử lại `/me` | Cả hai mất quyền | T07, phản hồi chuyển về login |
| T08 | Tạo phiên Bob mới; hai client ngừng request 75 giây rồi thử lại | Cả hai mất quyền | T08, thời gian không hoạt động thực tế |
| T09 | So sánh ID trước/sau login ở cả HTTP và HTTPS | ID thay đổi ở cả hai bản | Hai dòng T09-HTTP/T09-HTTPS và `session-fingerprints.tsv` |

Script còn kiểm tra cookie HTTP đã bắt mất quyền sau logout (`HTTP-LOGOUT`), tạo manifest PCAP, xác nhận 0 gói bị kernel drop và chỉ công bố kết quả khi đủ điều kiện. Mã test case không diễn tả thứ tự thời gian: T03 phải xảy ra trước T02.

### 6.2. Chạy H01–H13

Mở **PowerShell tại `session-hijacking-lab`**:

```powershell
.\mvnw.cmd test
```

JUnit dùng server ở cổng ngẫu nhiên nên không cần bật app 8080 trước. Xem kết quả thực tế và `target/surefire-reports/`; không push `target/`. Dockerfile cũng chạy `./mvnw -B verify` khi build. Docker có thể dùng lại build cache, vì vậy một lệnh `compose up --build` không tự chứng minh đã chạy lại test ở lần đó.

### 6.3. Chạy S01–S10

Chuẩn bị chứng chỉ và bật HTTPS theo [hướng dẫn demo](HUONG_DAN_DEMO_NHOM05.md), rồi:

```powershell
java scripts/VerifyHttpsLab.java .local/tls/localhost.cer
```

Chờ 75 giây ở timeout. Khi đạt toàn bộ, công cụ hiện `RESULT: 24 checks passed...`. Nếu dừng giữa chừng thì ghi FAIL/BLOCKED theo nguyên nhân, không điền đủ 24.

### 6.4. Chạy T01–T09

```powershell
docker build -t session-lab-packet-tools:local scripts/part3/packet-tools
.\scripts\part3\Run-Part3.ps1
```

Chỉ ghi đạt trọn bộ khi có `COMPLETE:` và `COMPLETE.txt` tại thư mục lần chạy mới. Tên `run-...`, marker, số frame và hash phải lấy từ lần mới, không sao chép số liệu cũ.

## 7. Mẫu ghi kết quả

**Ngày/giờ:** …<br>
**Người chạy:** …<br>
**Máy, hệ điều hành, JDK, Docker:** …<br>
**Commit:** lấy bằng `git rev-parse HEAD`; ghi thêm nếu có thay đổi chưa commit.<br>
**Cách chạy:** Maven / Java HTTPS / script PCAP / trình duyệt.<br>
**Run ID thực nghiệm nếu có:** …

| Mã case | Kết quả thực tế, status/quan sát | PASS/FAIL/BLOCKED/NOT RUN | File bằng chứng đã che | Ghi chú |
|---|---|---|---|---|
| Điền mã sau khi chạy | Chưa ghi nhận | NOT RUN | — | — |

PASS: thực hiện đủ bước và quan sát đúng. FAIL: đã thực hiện nhưng khác mong đợi. BLOCKED: chưa thực hiện được vì môi trường/chứng chỉ/cổng. NOT RUN: chưa chạy. Không điền PASS chỉ vì đọc thấy cấu hình đúng trong source.

Khi FAIL, lưu mã case, thời gian, bước tái hiện và phản hồi thực tế. Không sửa cấu hình bảo vệ hoặc thay kỳ vọng chỉ để phép thử đạt. Che session ID, CSRF token và dữ liệu nhạy cảm trước khi chia sẻ.

## 8. Bằng chứng đã ghi nhận để đối chiếu

- [Log HTTP ngày 14/10](minh-chung/kiem-chung-http.txt): 13 kiểm tra đạt.
- [Log HTTPS ngày 14/10](minh-chung/kiem-chung-https.txt): 24 kiểm chứng đạt.
- [Events thực nghiệm ngày 14/10](minh-chung/phan3/run-063d980c01c2/events.tsv): T01–T09 đạt, có thêm HTTP-LOGOUT.
- [Bảng gói đã che](minh-chung/phan3/run-063d980c01c2/packets-redacted.tsv) và [fingerprint](minh-chung/phan3/run-063d980c01c2/session-fingerprints.tsv).
- [Events thực nghiệm ngày 17/10](minh-chung/phan3/run-043c53a32a5e/events.tsv) và [báo cáo HTML](minh-chung/phan3/run-043c53a32a5e/index.html): T01–T09 đạt trên Windows PowerShell 5.1 sau sửa xử lý stderr/mảng JSON. [COMPLETE](minh-chung/phan3/run-043c53a32a5e/COMPLETE.txt) chỉ được tạo khi đủ kiểm chứng; PCAP gốc giữ riêng trong `.local/`.

13, 24 và 9 có đơn vị/phạm vi khác nhau, không cộng thành tổng số test độc lập. Bộ này chưa kiểm chứng đầy đủ các chính sách cookie khác site trên trình duyệt, khai thác XSS hay nghe lén LAN.

## 9. OWASP và kiểm chứng cuối ngày 24/10/2026

**Kiểm chứng ngày 24/10/2026:** build và nạp lại hai app HTTP/HTTPS; **21 kiểm thử JUnit** (13 HTTP + 8 kiểm tra bộ giới hạn/log), **25 kiểm chứng OWASP**, **24 kiểm chứng HTTPS** và **T01–T09** đều đạt. [Bằng chứng OWASP đã rà soát](minh-chung/owasp/run-20261024/verification.json), [báo cáo PCAP mới](minh-chung/phan3/run-927bdc5629a1/index.html). Đây là các bộ kiểm chứng có phạm vi khác nhau, không cộng thành tổng kiểm thử độc lập.

| Mã trong VerifyOwaspLab | Hành vi đã kiểm tra trên hai app | Kết quả |
|---|---|---|
| O01–O03 | Trang login, đăng nhập đúng, CSRF thiếu bị từ chối trước khi đếm sai | PASS HTTP/HTTPS |
| O04–O06 | 5 lần sai vẫn 302; lượt sau 429/Retry-After/no-store/trang HTML; đổi client/hoa thường/khoảng trắng không né hạn mức | PASS HTTP/HTTPS |
| O07–O08 | Bob không dùng chung hạn mức Alice; cookie đã xác thực vẫn dùng được khi chặn login | PASS HTTP/HTTPS |
| O09–O10 | Retry-After giảm; thử trong lúc chặn không kéo dài timer; chờ hết 60 giây rồi đăng nhập đúng | PASS HTTP/HTTPS |
| O11–O12 | Login thành công reset số sai; logout vẫn làm cookie sao chép mất quyền | PASS HTTP/HTTPS |
| O13 | HTTPS đăng nhập được trong lúc tài khoản HTTP còn bị chặn | PASS HTTPS |
| Rà log | 27 dòng HTTP và 26 dòng HTTPS; đúng 5 trường cho phép; không có các giá trị bí mật của lần chạy; failure/limit cùng UUID | PASS |

Thêm 8 JUnit: 7 kiểm tra limiter (ngưỡng, chuẩn hóa tài khoản, reset, giữ chỗ cho request đồng thời, giải phóng lượt không có kết quả, hết block/cửa sổ, cấu hình sai) và 1 kiểm tra log (trường cho phép, không lộ dữ liệu, chống xuống dòng giả và UUID cùng request). Hai kiểm tra thời gian dùng cấu hình ngắn riêng của đối tượng unit test; app Docker và kiểm chứng chạy giữ nguyên 5/300/60.

**Giao diện:** U01–U04 đã quan sát trên trình duyệt tích hợp. U05 mới xác nhận login/logout và nút sử dụng được, chưa ghi loading/cache phục hồi; U06 xác nhận focus/đăng xuất bằng bàn phím ở desktop, chưa kiểm tra màn hình hẹp. Không ghi PASS đầy đủ cho U05/U06.

Kết quả thuộc thư mục làm việc hiện tại; `verification.json` ghi commit nền, SHA-256 nguồn và image ID. Không suy ra mọi file đã commit/push. Chạy lại công cụ bằng lệnh ở README; dữ liệu cookie/token gốc chỉ lưu `.local/`.
