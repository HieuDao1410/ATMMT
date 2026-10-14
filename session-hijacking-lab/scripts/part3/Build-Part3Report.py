"""Build a self-contained Vietnamese evidence report from a COMPLETE lab run."""
import csv
import html
import json
import sys
from pathlib import Path

directory = Path(sys.argv[1]).resolve()
if not (directory / "COMPLETE.txt").is_file():
    raise SystemExit("Refusing to present an incomplete run as a completed experiment")
with (directory / "events.tsv").open(encoding="utf-8-sig", newline="") as source:
    events = list(csv.DictReader(source, delimiter="\t", fieldnames=["id", "status", "time", "observation", "evidence"]))
by_id = {event["id"]: event for event in events}
required = [f"T{i:02}" for i in range(1, 9)] + ["T09-HTTP", "T09-HTTPS"]
if any(key not in by_id or by_id[key]["status"] != "PASS" for key in required):
    raise SystemExit("Required passing evidence is missing")
metadata = json.loads((directory / "run.json").read_text(encoding="utf-8-sig"))
manifest = json.loads((directory / "capture-manifest.json").read_text(encoding="utf-8-sig"))
with (directory / "packets-redacted.tsv").open(encoding="utf-8-sig", newline="") as source:
    packets = list(csv.DictReader(source, delimiter="\t"))
http_requests = [p for p in packets if p["capture"] == "http.pcap" and p["method"]]
tls_records = [p for p in packets if p["capture"] == "https.pcap" and (p["tls_content_type"] or p.get("tls_opaque_type"))]
tls_application = [p for p in tls_records if "23" in (p["tls_content_type"] + "," + p.get("tls_opaque_type", "")).split(",")]
protocol = (directory / "tls-protocol.txt").read_text(encoding="utf-8").strip()
fingerprints = list(csv.reader((directory / "session-fingerprints.tsv").open(encoding="utf-8"), delimiter="\t"))
e = html.escape
titles = {
    "T01": "Client B chưa đăng nhập", "T02": "Dùng lại cookie lấy từ PCAP HTTP",
    "T03": "Quan sát cookie trên đường truyền HTTP", "T04": "Đối chiếu bản bắt gói HTTPS",
    "T05": "Thuộc tính cookie bản bảo vệ", "T06": "Cookie HTTPS được giả định đã lộ",
    "T07": "Hủy phiên khi đăng xuất", "T08": "Hết hạn khi không hoạt động", "T09": "Đổi ID sau đăng nhập",
}
descriptions = {
    "T01": "B nhận 302 và được chuyển về /login.",
    "T02": "B gửi cookie được trích xuất bằng TShark từ gói HTTP; /me trả danh tính alice.",
    "T03": "TShark giải mã được trường http.cookie từ request thật của A; giá trị chỉ lưu trong .local/.",
    "T04": f"Có {len(tls_application)} frame chứa bản ghi TLS ngoài loại 23; không giải mã ra header cookie và không thấy chuỗi cookie đã biết trong byte PCAP. Loại ngoài không xác định nội dung đã mã hóa bên trong.",
    "T05": "Client TLS đã xác minh chứng chỉ nhận cookie Secure, HttpOnly, SameSite=Lax, Path=/, không có Domain. Đây là kiểm tra header, không phải ảnh DevTools.",
    "T06": "B dùng cookie HTTPS còn hiệu lực đã biết ở endpoint và được nhận diện là alice. Cookie này không lấy từ PCAP TLS.",
    "T07": "A logout; cả A và B dùng cookie cũ đều phải đăng nhập lại.",
    "T08": "Dừng request từ cả hai client 75 giây; cả hai bị chuyển về login. Timeout cấu hình là 60 giây không hoạt động.",
    "T09": "ID phiên thay đổi sau login ở cả HTTP và HTTPS; đối chiếu bằng fingerprint đã rút gọn.",
}

css = """
:root{font-family:'Segoe UI',system-ui,sans-serif;color:#213b32;background:#f8f7f2;line-height:1.6}
*{box-sizing:border-box}body{margin:0}main{width:min(1160px,calc(100% - 56px));margin:auto;padding:38px 0}
h1{font-size:38px;line-height:1.2;letter-spacing:-1.4px;margin:10px 0 16px}h2{font-size:23px;letter-spacing:-.5px;margin:0 0 14px}h3{font-size:17px;margin:0 0 8px}
p{margin:0 0 14px}.muted{color:#64745f}.eyebrow{font-size:11px;letter-spacing:2px;font-weight:700;color:#6b8059}
.topline,.footer,.nav{display:flex;justify-content:space-between;gap:16px;align-items:center}.brand{font-weight:700;font-size:20px}.tag,.pass{border-radius:5px;padding:4px 9px;background:#dbe8c8;color:#35552c;font-size:11px;font-weight:700;white-space:nowrap}
.intro{max-width:850px;font-size:15px;color:#64745f}.nav{justify-content:flex-start;flex-wrap:wrap;border-bottom:1px solid #dfe5d6;padding:20px 0;margin-bottom:26px}
a{color:#315741;text-underline-offset:4px}.nav a{font-size:13px;text-decoration:none;border:1px solid #c8d4bb;border-radius:6px;padding:7px 12px}.nav a:hover{background:#e8eedf}a:focus-visible{outline:3px solid #9b762b;outline-offset:4px}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:25px 0}.stat{background:#fffefa;border:1px solid #dfe5d6;border-radius:12px;padding:20px}.stat strong{display:block;font-size:30px;line-height:1.2}.stat span{display:block;color:#6e7c63;font-size:12px;margin-top:8px}
section{margin:30px 0}.panel{background:#fffefa;border:1px solid #dfe5d6;border-radius:12px;padding:24px}.green{background:#eaf0df}.sand{background:#f5eadb}.dark{background:#203e35;color:#f4f4e9}.dark .muted{color:#c2cfb8}
.chain{display:grid;grid-template-columns:1fr 30px 1fr 30px 1fr;align-items:center;gap:8px;margin:24px 0}.arrow{text-align:center;font-size:24px;color:#809366}.chain .panel{min-height:150px;padding:20px}.chain p{font-size:13px;margin:8px 0 0}
.two{display:grid;grid-template-columns:1fr 1fr;gap:22px}.three{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.three .panel{min-height:225px}.big{font-size:34px;font-weight:700;margin:14px 0}.note{padding:17px 20px;border-left:3px solid #8fa66b;background:#edf1e6;font-size:13px;color:#5d7051;margin:22px 0}
.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:13px}th,td{text-align:left;vertical-align:top;padding:14px 12px;border-bottom:1px solid #e2e6db}th{color:#6d7d60;font-size:11px;letter-spacing:.4px}td:first-child{white-space:nowrap;font-weight:600}
code{font-family:Consolas,monospace;font-size:12px;overflow-wrap:anywhere}.mono{font-family:Consolas,monospace}.footer{border-top:1px solid #dfe5d6;padding-top:18px;margin-top:32px;font-size:11px;color:#748069}.metadata{font-size:12px;color:#6d7d60;overflow-wrap:anywhere}.slide main{padding-top:26px}.slide h1{font-size:36px}.slide section{margin:22px 0}.slide .footer{margin-top:24px}
@media(max-width:700px){main{width:calc(100% - 32px);padding:25px 0}h1{font-size:29px}.stats,.two,.three{grid-template-columns:1fr}.chain{grid-template-columns:1fr}.arrow{transform:rotate(90deg)}.footer{align-items:flex-start;flex-direction:column}.topline{align-items:flex-start}}
@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}
"""

def shell(title, body, slide=False):
    return f"""<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{e(title)} · Session Lab</title><style>{css}</style></head><body class="{'slide' if slide else ''}"><main>
<div class="topline"><span class="brand">ghi chú. / session lab</span><span class="tag">KẾT QUẢ THỰC NGHIỆM</span></div>
{body}<footer class="footer"><span>{e(metadata['run_id'])} · {e(metadata['started_at'])}</span><span>Tái trình bày kết quả đã ghi · Cookie được che</span></footer></main></body></html>"""

chain = """<div class="chain"><div class="panel green"><span class="eyebrow">01 / CLIENT A</span><h3>A đăng nhập Alice</h3><p>Gửi GET /me và /notes qua HTTP, kèm cookie phiên.</p></div><div class="arrow">→</div><div class="panel sand"><span class="eyebrow">02 / PCAP THỰC TẾ</span><h3>TShark đọc cookie</h3><p>Trích http.cookie từ gói đúng marker của lần chạy.</p></div><div class="arrow">→</div><div class="panel dark"><span class="eyebrow">03 / CLIENT B</span><h3>B được nhận diện là Alice</h3><p class="muted">B gửi chính cookie đã trích xuất, không nhập mật khẩu.</p></div></div>"""
rows = []
for key, title in titles.items():
    source = "T09-HTTP + T09-HTTPS" if key == "T09" else key
    rows.append(f"<tr><td>{key}</td><td><strong>{e(title)}</strong><br><span class='muted'>{e(descriptions[key])}</span></td><td><span class='pass'>ĐẠT</span></td><td><code>{e(source)}</code></td></tr>")
http_rows = "".join(f"<tr><td>{e(p['frame'])}</td><td>{e(p['tcp_stream'])}</td><td>{e(p['method'])}</td><td><code>{e(p['uri'])}</code></td><td><code>{e(p['cookie'])}</code></td></tr>" for p in http_requests)
manifest_rows = "".join(f"<tr><td>{e(item['file'])}</td><td>{item['bytes']}</td><td><code>{e(item['sha256'])}</code></td></tr>" for item in manifest)
fingerprint_rows = "".join(f"<tr><td>{e(row[0])}</td><td><code>{e(row[1])}</code></td><td><code>{e(row[2])}</code></td><td>Khác nhau</td></tr>" for row in fingerprints)
header = f"""<p class="eyebrow" style="margin-top:30px">AN TOÀN MẠNG MÁY TÍNH · PHẦN 3</p><h1>Từ gói tin đến phiên đăng nhập.</h1><p class="intro">Thực nghiệm trên ứng dụng giả của nhóm. Bắt gói tại eth0 trong network namespace của từng container; client kết nối qua cổng loopback của Windows.</p><p class="metadata">{e(metadata['tshark_version'])}<br>Mã nguồn: {e(metadata['source_commit'])}</p>"""
stats = f"""<div class="stats"><div class="stat"><strong>9 / 9</strong><span>Tình huống T01–T09 đạt</span></div><div class="stat"><strong>{len(http_requests)}</strong><span>Request HTTP được phân tích</span></div><div class="stat"><strong>{len(tls_application)}</strong><span>Frame có bản ghi TLS ngoài loại 23</span></div><div class="stat"><strong>0</strong><span>Header cookie giải mã từ TLS</span></div></div>"""
body = header + """<nav class="nav" aria-label="Điều hướng báo cáo"><a href="#results">Bảng kết quả</a><a href="#http">Gói HTTP</a><a href="#tls">Đối chiếu TLS</a><a href="#manifest">Đối chiếu PCAP</a><a href="events.tsv" download>Log kết quả TSV</a></nav>""" + stats + chain
body += """<div class="note"><strong>Phạm vi bằng chứng:</strong> HTTP dùng cookie trích từ PCAP thật. HTTPS dùng cookie đã biết tại endpoint để mô phỏng lộ cookie; không lấy được cookie đó từ bản bắt gói TLS. Thuộc tính cookie được kiểm tra bằng client TLS, không phải ảnh DevTools.</div>"""
body += f"<section id='results'><h2>Bảng kết quả thực tế</h2><div class='table-wrap panel'><table><thead><tr><th>MÃ</th><th>QUAN SÁT</th><th>KẾT QUẢ</th><th>LOG</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div></section>"
body += f"<section id='http'><h2>Request HTTP — cookie đã che</h2><div class='table-wrap panel'><table><thead><tr><th>FRAME</th><th>STREAM</th><th>METHOD</th><th>URI</th><th>COOKIE</th></tr></thead><tbody>{http_rows}</tbody></table></div></section>"
body += f"""<section id="tls"><h2>HTTPS: có dữ liệu truyền, cookie không đọc được</h2><div class="two"><div class="panel sand"><h3>HTTP</h3><p class="big">http.cookie</p><p>TShark đọc được header trong gói tin và B sử dụng lại thành công.</p></div><div class="panel green"><h3>{e(protocol)}</h3><p class="big">{len(tls_application)} frame mã hóa</p><p>Không cung cấp khóa giải mã. Có lưu lượng TLS thực tế; không giải mã ra cookie và chuỗi cookie đã biết không xuất hiện trong byte PCAP.</p></div></div></section>"""
body += f"<section><h2>Session ID trước/sau login — fingerprint SHA-256 rút gọn</h2><div class='panel table-wrap'><table><thead><tr><th>KÊNH</th><th>TRƯỚC LOGIN</th><th>SAU LOGIN</th><th>ĐỐI CHIẾU</th></tr></thead><tbody>{fingerprint_rows}</tbody></table></div></section>"
body += f"<section id='manifest'><h2>Đối chiếu bản bắt gói gốc</h2><p class='muted'>PCAP gốc giữ riêng tại <code>{e(metadata['raw_artifacts_location'])}</code>. Hash liên kết bảng công khai với file thực nghiệm, không thay thế bản gốc.</p><div class='panel table-wrap'><table><thead><tr><th>FILE</th><th>BYTE</th><th>SHA-256</th></tr></thead><tbody>{manifest_rows}</tbody></table></div></section>"
body += "<div class='note'>Giới hạn: một máy Windows và mạng Docker riêng; chưa có ảnh DevTools, chưa thử mọi quy tắc trình duyệt khác site. Trong TLS 1.3, loại ngoài 23 có thể chứa cả handshake đã mã hóa; không suy ra số request HTTP từ số frame này. Video đi kèm là trình bày lại số liệu đã ghi, không phải video quay trực tiếp lúc bắt gói.</div>"
if (directory / "demo-ket-qua.mp4").is_file():
    body += "<section><h2>Video trình bày kết quả đã ghi</h2><video controls preload='metadata' style='width:100%;border-radius:12px' src='demo-ket-qua.mp4'>Trình duyệt không hỗ trợ video.</video><p class='muted'>Không có âm thanh; không phải bản quay trực tiếp quá trình bắt gói.</p></section>"
(directory / "index.html").write_text(shell("Bằng chứng Session Hijacking", body), encoding="utf-8")

slide_http = "<p class='eyebrow' style='margin-top:28px'>01 / COOKIE LỘ QUA HTTP</p><h1>Một cookie. Hai client cùng danh tính.</h1><p class='intro'>Cookie B dùng được trích từ request thật trong PCAP, không lấy từ cookie jar của A.</p>" + chain
slide_http += f"<div class='two'><div class='panel'><h3>T01 · Trước khi có cookie</h3><p>B → GET /me → <strong>302 /login</strong></p></div><div class='panel green'><h3>T02 + T03 · Sau khi đọc PCAP</h3><p>B → cookie từ PCAP → /me → <strong>alice</strong></p><p class='muted'>{len(http_requests)} request HTTP trong bản phân tích.</p></div></div>"
slide_tls = f"<p class='eyebrow' style='margin-top:28px'>02 / ĐỐI CHIẾU ĐƯỜNG TRUYỀN</p><h1>HTTPS mã hóa dữ liệu đang truyền.</h1><p class='intro'>Gói được bắt trước khi Tomcat xử lý TLS; không cung cấp khóa giải mã cho TShark.</p><div class='two'><div class='panel sand'><h2>HTTP</h2><p class='big'>Cookie đọc được</p><p>Trường http.cookie có trong request.</p><p class='mono'>JSESSIONID=&lt;redacted&gt;</p></div><div class='panel green'><h2>{e(protocol)}</h2><p class='big'>{len(tls_application)} frame, loại ngoài 23</p><p>0 header cookie được giải mã.</p><p>Chuỗi cookie đã biết không xuất hiện trong byte PCAP.</p></div></div><div class='note'><strong>T06 vẫn đạt:</strong> cookie HTTPS được giả định đã lộ ở endpoint vẫn nhận diện B là Alice. Loại ngoài 23 không xác định nội dung mã hóa bên trong; không dùng số frame này làm số request HTTP.</div>"
slide_lifecycle = "<p class='eyebrow' style='margin-top:28px'>03 / VÒNG ĐỜI PHIÊN</p><h1>Cookie cũ mất quyền sau khi phiên kết thúc.</h1><p class='intro'>Kết quả được xác nhận bằng request thực tế từ client gốc và client giữ cookie sao chép.</p><div class='three'><div class='panel green'><span class='tag'>T07</span><h2 style='margin-top:16px'>Logout</h2><p>A logout hợp lệ.</p><p><strong>A và B → 302 /login</strong></p></div><div class='panel sand'><span class='tag'>T08</span><h2 style='margin-top:16px'>Timeout</h2><p>Ngừng mọi request 75 giây; timeout cấu hình 60 giây.</p><p><strong>Cả hai mất quyền</strong></p></div><div class='panel'><span class='tag'>T09</span><h2 style='margin-top:16px'>Đổi ID</h2><p>Fingerprint trước và sau đăng nhập khác nhau.</p><p><strong>Đạt ở HTTP và HTTPS</strong></p></div></div><div class='note'>9/9 tình huống T01–T09 đạt. Bản gốc PCAP giữ trong .local; báo cáo, log và bảng gói đã che cookie có thể chia sẻ.</div>"
for name, slide in [("slide-http", slide_http), ("slide-tls", slide_tls), ("slide-lifecycle", slide_lifecycle)]:
    (directory / f"{name}.html").write_text(shell(name, slide, slide=True), encoding="utf-8")
print(f"Report generated: {directory / 'index.html'}")
