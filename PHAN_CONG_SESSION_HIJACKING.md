# PHÂN CÔNG ĐỒ ÁN SESSION HIJACKING

**Môn học:** An toàn mạng máy tính  
**Nhóm:** 3 thành viên  
**Ngày lập:** 11/10/2026  
**Người thực hiện phần 1:** Bạn  
**Cập nhật:** 24/10/2026<br>
**Trạng thái:** Phần 1–2 đã triển khai, đạt 13 kiểm tra HTTP và 24 kiểm chứng HTTPS. Phần 3 đã bắt gói HTTP/HTTPS thật, phân tích bằng TShark (Wireshark CLI), dùng lại cookie lấy từ HTTP PCAP và đạt T01–T09. Đã có báo cáo, ảnh và video trình bày kết quả; PCAP gốc giữ riêng trong `.local/`. Chưa ghi ảnh DevTools/video thao tác trực tiếp và chưa có xác nhận chạy trên máy thành viên khác. Xem [README dự án](session-hijacking-lab/README.md) và [báo cáo chung — thực nghiệm](session-hijacking-lab/docs/ATMMT_nhom05.md#kich-ban).

**Kiểm chứng mới 24/10:** 21 JUnit, 25 kiểm chứng OWASP, 24 HTTPS và T01–T09 đạt trên image mới. [Bằng chứng](session-hijacking-lab/docs/minh-chung/owasp/run-20261024/verification.json). Thông tin nhóm đã bổ sung; phân công nói hiện tại theo [kịch bản](session-hijacking-lab/docs/KICH_BAN_BAO_CAO_NHOM05.md), demo của bạn ở cuối trước cảm ơn.

## 1. Đề tài và mục tiêu chung

**Tên đề tài đề xuất:** Tìm hiểu và mô phỏng tấn công Session Hijacking trên ứng dụng Spring Boot, triển khai và đánh giá các biện pháp bảo vệ phiên đăng nhập.

Nhóm tập trung vào **phiên đăng nhập HTTP dùng cookie**, với ứng dụng Java/Spring Boot do nhóm xây dựng. Ghi rõ phạm vi này trong đề cương gửi giảng viên để thống nhất với yêu cầu môn học.

Sản phẩm phải giải thích và minh họa được chuỗi:

1. Người dùng đăng nhập và trình duyệt nhận cookie phiên.
2. Cookie phiên bị lộ trong một tình huống lab.
3. Client thứ hai dùng lại cookie còn hiệu lực để được nhận diện là người dùng đó.
4. Nhóm áp dụng biện pháp phòng vệ, kiểm tra kết quả và giải thích giới hạn.

Phần thực hành chỉ sử dụng ứng dụng, tài khoản và dữ liệu giả của nhóm trên máy cục bộ hoặc mạng ảo riêng. Phiên bản HTTP phục vụ thí nghiệm phải chạy trong lab, không đưa lên Internet.

**Phạm vi bắt buộc:** ứng dụng có session; mô phỏng dùng lại cookie; so sánh lưu lượng HTTP/HTTPS; kiểm tra logout, timeout và thuộc tính cookie.

**Phần mở rộng nếu còn thời gian:** Session Fixation, xác thực lại trước thao tác nhạy cảm hoặc cảnh báo hoạt động bất thường. Hoàn thành phần bắt buộc trước.

## 2. Phân công tổng quát

| Thành viên | Phần việc chính | Sản phẩm bàn giao | Phụ thuộc |
|---|---|---|---|
| **Người 1 — Bạn** | Xây dựng ứng dụng Spring Boot và luồng đăng nhập bằng session | Ứng dụng chạy được; tài khoản mẫu; trang dữ liệu riêng; hướng dẫn chạy | Bắt đầu trước |
| **Người 2** | Bảo vệ và quản lý vòng đời session | HTTPS; cấu hình cookie; kiểm tra đổi session ID, logout và timeout; giải thích các biện pháp | Nhận ứng dụng tối thiểu từ người 1 |
| **Người 3** | Thiết lập lab, mô phỏng và kiểm chứng | Client thứ hai; bản bắt gói; bảng kết quả; ảnh/video; kịch bản demo | Nhận ứng dụng từ người 1, phối hợp với người 2 |

Mỗi người viết phần báo cáo tương ứng với công việc của mình. Cả nhóm phải hiểu toàn bộ luồng demo để trả lời câu hỏi khi bảo vệ.

## 3. Phần 1 — Bạn: Xây dựng ứng dụng nền tảng

### 3.1. Mục tiêu

Tạo ứng dụng nhỏ có đăng nhập và dữ liệu cá nhân để hai thành viên còn lại triển khai thí nghiệm. Ở bước này, ứng dụng cần hoạt động đúng với cơ chế có sẵn của Spring Security.

**Ứng dụng đề xuất:** Quản lý ghi chú cá nhân. Chỉ cần đăng nhập, xem danh tính hiện tại, xem ghi chú của mình và đăng xuất. Ghi chú chỉ đọc là đủ cho demo ban đầu.

### 3.2. Công nghệ đề xuất

| Thành phần | Lựa chọn | Lý do |
|---|---|---|
| Java | Java 17, theo JDK hiện có; Docker cũng dùng Java 17 | Chạy được trên máy hiện tại và thống nhất môi trường nhóm |
| Backend | Spring Boot 4.1.1, Spring MVC | Tạo ứng dụng web nhỏ và dễ chạy |
| Bảo mật | Spring Security, form login, session phía server | Sát nội dung cookie và phiên đăng nhập |
| Giao diện | Thymeleaf và HTML đơn giản | Giảm thời gian xây frontend riêng |
| Dữ liệu | Hai tài khoản mẫu và ghi chú trong bộ nhớ | Chưa cần database cho yêu cầu demo |
| Build | Maven | Dễ chia sẻ cách build và chạy |

Đây là cấu hình đã triển khai trong `session-hijacking-lab/pom.xml`. Maven Wrapper dùng Maven 3.9.16; Spring Boot quản lý phiên bản các thư viện Spring. Đã thêm Dockerfile và Compose để thành viên khác không cần phụ thuộc JDK/Maven trên host. Xem README để biết cách chạy và phạm vi đã kiểm chứng.

### 3.3. Các màn hình và đường dẫn đề xuất

| Đường dẫn | Chức năng | Quyền truy cập |
|---|---|---|
| `GET /` | Trang giới thiệu và liên kết đăng nhập | Công khai |
| `GET /login` | Trang đăng nhập; ban đầu có thể dùng trang mặc định | Công khai |
| `GET /me` | Hiển thị tên tài khoản đang được server xác thực | Phải đăng nhập |
| `GET /notes` | Hiển thị ghi chú mẫu của tài khoản hiện tại | Phải đăng nhập |
| `POST /logout` | Đăng xuất qua cơ chế của Spring Security | Gửi kèm CSRF token hợp lệ |

Các đường dẫn trên đã được triển khai trong phần 1. Giữ ổn định sau khi bàn giao để người 3 chuẩn bị kịch bản kiểm tra. `POST /login` và `POST /logout` do Spring Security xử lý.

### 3.4. Checklist thực hiện theo thứ tự

- [x] Khởi tạo dự án Maven với Spring Web, Spring Security và Thymeleaf phù hợp phiên bản Spring Boot.
- [x] Ghi JDK, phiên bản Spring Boot và cách chạy vào README.
- [x] Tạo trang chủ đơn giản.
- [x] Tạo hai tài khoản lab, `alice` và `bob`, bằng cơ chế tài khoản trong bộ nhớ của Spring Security.
- [x] Dùng `PasswordEncoder` để mã hóa mật khẩu mẫu; ghi rõ đây chỉ là tài khoản lab.
- [x] Bật form login bằng Spring Security; dùng cơ chế session phía server.
- [x] Yêu cầu đăng nhập đối với `/me` và `/notes`.
- [x] Hiển thị danh tính dựa trên principal đã xác thực phía server.
- [x] Tạo ghi chú mẫu khác nhau cho hai tài khoản; lấy ghi chú theo principal, không theo tên người dùng do client tùy ý gửi lên.
- [x] Thêm nút đăng xuất bằng form POST có CSRF token.
- [x] Giữ bảo vệ CSRF và chống Session Fixation của framework.
- [x] Kiểm tra hành vi phần 1 bằng 13 kiểm tra HTTP tự động, gồm các chức năng ở mục 3.5; quan sát DevTools thủ công do nhóm thực hiện tiếp.
- [x] Chuẩn bị gói để bàn giao qua Git: mã nguồn, tài khoản mẫu, README, báo cáo phần 1–2 và hướng dẫn nhận bàn giao; kiểm tra lại từ checkout sạch.
- [ ] Thành viên nhận xác nhận đã clone và chạy thành công trên máy riêng.

Không tự tạo chuỗi session ID hoặc tự viết lại cơ chế đăng nhập. Chưa cần JWT, OAuth 2.0, Redis, microservices hoặc frontend riêng cho phạm vi này.

### 3.5. Tiêu chí hoàn thành phần 1

| Kiểm tra | Kết quả cần đạt |
|---|---|
| Chưa đăng nhập, truy cập `/me` hoặc `/notes` | Bị chuyển đến đăng nhập với ứng dụng form login |
| Đăng nhập đúng bằng `alice` | `/me` hiển thị `alice` |
| Đăng nhập đúng bằng `bob` ở browser profile độc lập | `/me` hiển thị `bob` |
| Xem ghi chú từ hai tài khoản | Mỗi tài khoản chỉ thấy ghi chú của mình |
| Đăng nhập sai | Không tạo trạng thái xác thực thành công |
| Đăng xuất rồi truy cập trang riêng | Phải đăng nhập lại |
| Xem request trong DevTools | Nhận diện được cookie phiên thực tế của ứng dụng |

Tên cookie thường là `JSESSIONID` với session Servlet mặc định; ghi nhận tên thực tế trước khi bàn giao. Hai tab trong cùng browser profile thường dùng chung cookie nên không phù hợp để mô phỏng hai người dùng độc lập.

### 3.6. Tài liệu và kiến thức bạn phụ trách

README cần có: yêu cầu môi trường; cách chạy; đường dẫn ứng dụng; tài khoản lab; chức năng từng trang; cách dừng ứng dụng. Không ghi cookie phiên đang hoạt động vào README hoặc log ứng dụng.

Trong báo cáo, bạn giải thích luồng đăng nhập, quan hệ giữa cookie và session phía server, vai trò của Spring Security, và cách server xác định người dùng hiện tại. Chuẩn bị sơ đồ: trình duyệt gửi đăng nhập → server xác thực → phản hồi cookie → trình duyệt gửi cookie trong các request tiếp theo.

**Mốc bàn giao sớm:** Ngay khi đăng nhập và `/me` hoạt động, thông báo cho hai thành viên còn lại bắt đầu tích hợp. Có thể hoàn thiện `/notes` và README sau đó.

## 4. Phần 2 — Thành viên 2: Bảo vệ và quản lý session

### 4.1. Mục tiêu

Tiếp nhận ứng dụng từ người 1, cấu hình các biện pháp bảo vệ phiên, và phối hợp người 3 kiểm chứng từng tác dụng.

### 4.2. Checklist công việc

- [x] Đọc phiên bản và cấu hình thực tế của dự án trước khi sửa.
- [x] Thiết lập HTTPS cho bản bảo vệ; hướng dẫn client tin cậy chứng chỉ lab đúng cách, không dùng bỏ qua xác minh TLS làm cách chạy mặc định.
- [x] Tách rõ cấu hình HTTP dành cho lab và cấu hình HTTPS dành cho bản bảo vệ, tránh gửi cùng phiên qua cả hai kênh.
- [x] Cấu hình và kiểm tra cookie `Secure`, `HttpOnly`, `SameSite` phù hợp ứng dụng (header đã kiểm chứng; hành vi khác site trên trình duyệt thuộc phần 3).
- [x] Kiểm tra session ID được đổi sau đăng nhập nhờ cơ chế chống fixation của Spring Security.
- [x] Kiểm tra logout hủy session phía server; xóa cookie phía client nếu cần cho hành vi nhất quán.
- [x] Cấu hình timeout không hoạt động ngắn để dễ demo, ghi rõ đây là giá trị lab.
- [x] Giữ bảo vệ CSRF cho các thao tác thay đổi trạng thái.
- [x] Phối hợp phần 3 kiểm tra cookie cũ sau logout và hết hạn bằng hai Java client độc lập (T07/T08).
- [x] Viết phần biện pháp bảo vệ và giới hạn của từng biện pháp.

**Kết quả phần 2 đã có:** profile `http-lab` và `https`; Compose riêng; chứng chỉ tự tạo bằng `scripts/New-LabCertificate.ps1`; cookie HTTPS `__Host-LABSESSION_HTTPS`; timeout 1 phút; công cụ `scripts/VerifyHttpsLab.java` đạt 24 kiểm chứng thực tế. Nội dung giải thích, cách tin cậy chứng chỉ và bảng kết quả nằm trong README. Phần 3 đã kiểm tra lại cookie sao chép sau logout và sau 75 giây không hoạt động, kèm PCAP và video trình bày; quan sát DevTools/quay trực tiếp vẫn chưa được ghi.

**Bổ sung ngày 14/10:** đã soạn [báo cáo tổng hợp nhóm 05](session-hijacking-lab/docs/ATMMT_nhom05.md), [hướng dẫn bàn giao và DevTools](session-hijacking-lab/docs/BAN_GIAO_NHOM05.md), cùng README gốc repository. Kiểm tra lại từ mã nguồn sạch của commit `65b5835`, tạo chứng chỉ mới và build không dùng cache bước image: 13 kiểm tra HTTP và 24 kiểm chứng HTTPS đều đạt. Log đã che mã phiên được lưu cùng tài liệu bàn giao. Bằng chứng đường truyền bằng TShark đã bổ sung ở phần 3; chạy trên máy thành viên khác vẫn cần người nhận xác nhận.

Chứng chỉ lab của bản demo đã được nhập vào CurrentUser Root sau xác nhận của người dùng. Client Windows mặc định truy cập HTTPS được HTTP 200; người dùng xác nhận trang login HTTPS mở được trên trình duyệt ngoài Codex, không có cảnh báo. Chưa ghi nhận ảnh DevTools hoặc kết quả khác site từ kiểm tra này.

### 4.3. Những điểm phải giải thích đúng

| Biện pháp | Tác dụng | Giới hạn cần nêu |
|---|---|---|
| HTTPS | Bảo vệ dữ liệu trên đường truyền | Cookie đã bị lấy từ nguồn khác vẫn có thể bị dùng lại |
| `Secure` | Hạn chế trình duyệt gửi cookie qua kênh không bảo mật | Có ngoại lệ với localhost; cần chọn môi trường kiểm tra phù hợp |
| `HttpOnly` | Chặn JavaScript đọc cookie qua `document.cookie` | DevTools vẫn hiển thị cookie; XSS vẫn có thể gây hành động trong phiên |
| `SameSite` | Hạn chế gửi cookie trong một số request từ site khác | Không thay thế hoàn toàn CSRF token; không chặn dùng lại cookie đã lấy được |
| Đổi session ID sau đăng nhập | Chống Session Fixation | Không vô hiệu cookie mới bị lộ sau đăng nhập |
| Hủy session khi logout | Vô hiệu phiên phía server | Chỉ xóa cookie trên trình duyệt là chưa đủ |
| Timeout không hoạt động | Hết hạn phiên khi không có hoạt động | Client dùng cookie bị lộ có thể tiếp tục tạo hoạt động để giữ phiên |

Timeout không hoạt động khác với giới hạn tuổi thọ tuyệt đối. Nếu triển khai giới hạn tuyệt đối, ghi rõ cách thực hiện và kiểm tra riêng; không mặc định coi đó là cùng một cơ chế.

### 4.4. Sản phẩm và tiêu chí bàn giao

- Ứng dụng chạy qua HTTPS cùng hướng dẫn chứng chỉ lab.
- Tài liệu chỉ rõ cách chạy từng cấu hình, URL và tên cookie tương ứng.
- Bằng chứng thuộc tính cookie trên bản bảo vệ.
- Bằng chứng đổi session ID sau đăng nhập, hủy phiên khi logout và hết hạn khi không hoạt động.
- Bảng giải thích mỗi biện pháp bảo vệ khâu nào trong chuỗi chiếm phiên.

Người 2 không cần xây lại luồng đăng nhập đã có. Nếu phải sửa phần người 1 phụ trách, thống nhất trước để tránh chỉnh cùng khu vực đồng thời.

## 5. Phần 3 — Thành viên 3: Lab, mô phỏng và kiểm chứng

### 5.1. Mục tiêu

Tạo bằng chứng thực nghiệm rõ ràng, phân biệt được việc giả định cookie đã lộ với việc quan sát cookie lộ trên lưu lượng HTTP.

### 5.2. Môi trường tối thiểu

**Cách đã triển khai ngày 14/10:** hai Java client có kho cookie riêng; tcpdump bắt tại `eth0` trong network namespace của app Docker; TShark đọc PCAP offline. Thuộc tính cookie được kiểm tra tại client xác minh TLS, chưa chụp DevTools. Đây là phương án mạng ảo riêng cho các kịch bản dưới đây; không tuyên bố đã bắt Windows loopback bằng Wireshark GUI.

- Một máy chạy ứng dụng của nhóm.
- Hai browser profile độc lập hoặc một trình duyệt và một HTTP client có cookie jar riêng.
- DevTools để quan sát request, cookie và danh tính client.
- Wireshark để bắt lưu lượng trên loopback hoặc mạng ảo riêng mà nhóm kiểm soát.

Không cần Kali Linux hoặc ARP spoofing cho phạm vi tối thiểu. Chọn đúng interface có lưu lượng lab; một máy trong mạng LAN chuyển mạch không mặc nhiên đọc được lưu lượng giữa hai máy khác.

### 5.3. Kịch bản A — Mô phỏng cookie đã bị lộ

1. Client A đăng nhập bằng tài khoản lab.
2. Xác nhận danh tính của A qua `/me`.
3. Ghi nhận cookie phiên của A bằng DevTools trong lab.
4. Kiểm tra client B chưa đăng nhập nên không truy cập được `/me`.
5. Đưa cookie phiên của A vào B rồi truy cập `/me`.
6. Ghi nhận B được server nhận diện là tài khoản của A nếu cookie vẫn hợp lệ.

**Ý nghĩa:** chứng minh tác động của việc có cookie phiên hợp lệ. Thao tác lấy cookie bằng DevTools là giả lập sự cố lộ cookie, không phải bằng chứng khai thác để đánh cắp cookie.

### 5.4. Kịch bản B — Quan sát cookie qua HTTP trong lab

1. Chạy bản HTTP phục vụ thí nghiệm với phiên mới.
2. Bắt lưu lượng trên interface có request của client A.
3. A đăng nhập và truy cập trang riêng để tạo request chứa cookie.
4. Xác định request HTTP có cookie phiên đọc được trong bản bắt gói.
5. Dùng cookie quan sát được trên client B và kiểm tra danh tính qua `/me`.
6. Lưu bằng chứng liên kết: request chứa cookie → request dùng lại → kết quả danh tính.

Cookie và mật khẩu lab trong bản bắt gói là dữ liệu nhạy cảm của thí nghiệm. Kết thúc phiên sau demo và che giá trị cookie đầy đủ trên tài liệu trình chiếu.

### 5.5. Kịch bản C — Kiểm chứng bản bảo vệ

1. Chạy bản HTTPS, tạo phiên mới và dùng client tin cậy chứng chỉ lab.
2. Bắt lưu lượng mà không cung cấp khóa giải mã TLS; kiểm tra cookie không xuất hiện dưới dạng nội dung HTTP đọc được trên đường truyền.
3. Quan sát các thuộc tính cookie trong DevTools.
4. Mô phỏng dùng lại một cookie HTTPS hợp lệ đã lấy qua DevTools để chứng minh HTTPS không tự ngăn việc dùng lại cookie bị lộ từ nguồn khác.
5. A logout; kiểm tra B không còn dùng cookie cũ để xem dữ liệu riêng.
6. Tạo phiên mới, dừng hoạt động từ cả A và B, chờ đủ timeout và kiểm tra phiên hết hiệu lực.
7. Phối hợp người 2 kiểm tra đổi session ID trước và sau đăng nhập.

Đối với timeout, tránh trang tự tải lại hoặc request nền duy trì phiên. Giữ nguyên chuỗi giá trị cookie khi tái sử dụng để kết quả không bị nhiễu bởi lỗi sao chép.

### 5.6. Bảng ghi kết quả

Chỉ điền cột thực tế sau khi thực hiện; không dùng kết quả mong đợi như bằng chứng đã chạy.

| Mã | Tình huống | Kết quả mong đợi | Kết quả thực tế | Bằng chứng |
|---|---|---|---|---|
| T01 | B chưa đăng nhập truy cập trang riêng | Yêu cầu đăng nhập | Đạt: 302 về login | events.tsv: T01 |
| T02 | B dùng cookie hợp lệ của A | Nhận diện là A | Đạt: Alice, cookie lấy từ HTTP PCAP | events.tsv: T02 |
| T03 | Bắt request HTTP gửi cookie | Đọc được cookie trong request | Đạt: frame 22 của http-seed.pcap | T03, manifest PCAP |
| T04 | Bắt request HTTPS, không có khóa giải mã | Không đọc được cookie trong nội dung mã hóa | Đạt: 83 frame TLS, 0 header cookie giải mã | T04, packets-redacted.tsv |
| T05 | Kiểm tra cookie bản bảo vệ | Có thuộc tính đã cấu hình | Đạt bằng client TLS; chưa có ảnh DevTools | events.tsv: T05 |
| T06 | B dùng cookie đã lấy từ phiên HTTPS còn hiệu lực | Vẫn có thể được nhận diện là A | Đạt: Alice; cookie biết tại endpoint, không lấy từ TLS PCAP | events.tsv: T06 |
| T07 | A logout, B dùng lại cookie cũ | Không truy cập được dữ liệu riêng | Đạt: cả A/B về login | events.tsv: T07 |
| T08 | Phiên không hoạt động quá timeout | Phải đăng nhập lại | Đạt: sau 75.029 giây, cả hai về login | events.tsv: T08 |
| T09 | So sánh ID phiên trước/sau đăng nhập | ID thay đổi khi cơ chế chống fixation hoạt động | Đạt ở HTTP và HTTPS | session-fingerprints.tsv |

Kết quả thuộc lần `run-063d980c01c2`, ngày 14/10/2026. Các tên file trong bảng liên kết tại [báo cáo chung — kết quả và bằng chứng](session-hijacking-lab/docs/ATMMT_nhom05.md#ket-qua). Video là trình bày lại kết quả đã ghi; giữ PCAP chứa dữ liệu phiên trong `.local/`.

### 5.7. Sản phẩm bàn giao

- Sơ đồ môi trường lab, interface bắt gói và vai trò từng client.
- Kịch bản tái hiện theo từng bước, đủ để một thành viên khác chạy lại.
- Bảng kết quả thực tế kèm bằng chứng tương ứng.
- Bản bắt gói HTTP và HTTPS, ảnh chụp kết quả, video dự phòng nếu cần.
- Phần báo cáo về thí nghiệm, kết quả, sai số và giới hạn.

## 6. Thứ tự phối hợp và các mốc hoàn thành

| Mốc | Việc cần xong | Người phụ trách | Điều kiện chuyển bước |
|---|---|---|---|
| M1 | Chốt phạm vi, tên đề tài, công nghệ và đường dẫn | Cả nhóm | Thống nhất đề cương; ghi nhận yêu cầu giảng viên |
| M2 | Đăng nhập và `/me` hoạt động | Bạn | Hai client độc lập kiểm tra được danh tính |
| M3 | Ghi chú riêng, logout và hướng dẫn chạy | Bạn | Người khác chạy lại được ứng dụng |
| M4 | Demo dùng lại cookie và quan sát HTTP | Người 3 | Có bằng chứng tác động và đường lộ cookie |
| M5 | HTTPS và các biện pháp quản lý phiên | Người 2 | Có bản bảo vệ để kiểm tra |
| M6 | Kiểm tra đối chiếu và hoàn thiện bảng kết quả | Người 2 + người 3 | Giải thích được kết quả, kể cả cookie bị dùng lại qua HTTPS |
| M7 | Ghép báo cáo, chuẩn bị slide và tập demo | Cả nhóm | Mỗi người giải thích được công việc và cơ chế chung |

Sau M2, người 2 và người 3 có thể bắt đầu công việc trong khi bạn hoàn thiện M3. Chọn hạn cụ thể cho từng mốc sau khi biết ngày nộp và lịch rảnh của nhóm.

## 7. Cấu trúc báo cáo và phần trình bày

| Nội dung báo cáo | Người viết chính |
|---|---|
| Mục tiêu, phạm vi và mô hình đe dọa | Cả nhóm |
| HTTP, cookie, session và luồng đăng nhập Spring Security | Bạn |
| Thiết kế ứng dụng và cách chạy | Bạn |
| Session Hijacking và các kịch bản thí nghiệm | Người 3 |
| Biện pháp bảo vệ, cấu hình và giới hạn | Người 2 |
| Bảng kết quả và phân tích HTTP/HTTPS, logout, timeout | Người 2 + người 3 |
| Kết luận, giới hạn và hướng mở rộng | Cả nhóm |

Kịch bản trình bày đề xuất: bạn giới thiệu ứng dụng và luồng phiên → người 3 minh họa lộ cookie và dùng lại → người 2 giới thiệu phòng vệ → cả nhóm đối chiếu kết quả. Điều chỉnh thời lượng theo quy định giảng viên.

### Những câu hỏi cả nhóm cần trả lời được

1. Vì sao có cookie phiên còn hiệu lực lại có thể giả danh người dùng mà không cần mật khẩu?
2. Cookie chứa mã phiên và dữ liệu phiên phía server khác nhau như thế nào?
3. HTTPS, `Secure`, `HttpOnly` và `SameSite` bảo vệ những khâu nào?
4. Vì sao cookie lấy từ DevTools vẫn có thể dùng lại dù ứng dụng dùng HTTPS và `HttpOnly`?
5. Vì sao chỉ xóa cookie trên trình duyệt chưa đủ để logout an toàn?
6. Session Fixation khác tình huống dùng lại phiên sau đăng nhập như thế nào?
7. Timeout không hoạt động có giới hạn gì khi client khác vẫn gửi request?

## 8. Tài liệu kỹ thuật dùng chung

- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html): cơ chế và vòng đời phiên.
- [OWASP Cookie Theft Mitigation](https://cheatsheetseries.owasp.org/cheatsheets/Cookie_Theft_Mitigation_Cheat_Sheet.html): phòng vệ và giới hạn khi cookie bị lộ.
- [MDN — Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie): ý nghĩa các thuộc tính cookie.
- [Spring Security — Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html): quản lý phiên và chống fixation.
- [Spring Security — Handling Logouts](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html): cơ chế đăng xuất.
- [Spring Boot — Embedded Web Servers](https://docs.spring.io/spring-boot/how-to/webserver.html): cấu hình HTTPS.
- [OWASP — CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html): bảo vệ thao tác dùng cookie xác thực.

Đối chiếu tài liệu Spring đúng phiên bản của dự án khi triển khai; đường dẫn tài liệu mặc định có thể chuyển sang phiên bản mới theo thời gian.

## 9. Việc đầu tiên dành cho bạn

**Phần mã nguồn nền tảng và bản bảo vệ HTTPS đã được xây dựng, kiểm chứng tự động.** Việc tiếp theo là đọc hướng dẫn HTTPS trong README, làm quen hai profile/cookie, chủ động tin cậy đúng chứng chỉ lab nếu demo bằng trình duyệt và phối hợp người 3 bắt gói, lưu bằng chứng theo mục 5. Đọc `SecurityConfig`, `PageController`, `NoteService` và hai file cấu hình profile để giải thích được cơ chế khi bảo vệ đồ án.
