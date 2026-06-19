# Prompt cho Claude Code — Tính năng đối soát thanh toán hỗ trợ AI (ai-service)

> Copy toàn bộ nội dung dưới đây và đưa cho Claude Code. Nên chạy ở **plan mode** trước (Shift+Tab) để duyệt kế hoạch rồi mới cho thực thi.

---

## Vai trò & mục tiêu

Bạn là senior backend engineer trên dự án **BadmintonHub** (Spring Boot microservices). Hãy **LẬP KẾ HOẠCH rồi TRIỂN KHAI** tính năng "đối soát chứng từ chuyển khoản hỗ trợ AI" trong `ai-service`, tích hợp vào trang Quản trị → tab **"Duyệt thanh toán"** đã có.

Mục đích: giúp staff xác nhận thanh toán nhanh hơn. Khi staff bấm **"Xem"** một payment, hệ thống đọc ảnh biên lai, tra giao dịch ngân hàng theo mã đơn, chấm điểm độ khớp, và hiển thị một **thẻ gợi ý** (match level + chỉ chỗ cần soi trong ảnh). **Staff vẫn là người bấm Xác nhận/Từ chối — đây là công cụ hỗ trợ, không thay con người.**

## Bối cảnh hiện có (TỰ ĐỌC CODE để xác minh, không giả định)

- Microservices có `ai-service`, `payment-service`, ... (Eureka + API Gateway). `ai-service` dùng `postgres-ai` (DB `ai_db`).
- Trang Quản trị, tab "Duyệt thanh toán": danh sách payment đang `PENDING`, mỗi dòng có nút **Xem / Xác nhận / Từ chối**. Bấm "Xem" mở modal **"Chi tiết #N"** hiển thị: Loại (vd BOOKING), Số tiền, Thời gian, Nhận tiền (vd `Vietcombank · 1022044984`), `booking` id (UUID), và khu vực **"Ảnh chuyển khoản"**.
- Đã có sẵn cơ chế approve/reject payment. **Tìm endpoint/service approve/reject hiện tại và TÁI SỬ DỤNG — không viết lại.**

**Trước khi viết code, khảo sát và tóm tắt:** tên package/class thật của `ai-service` và `payment-service`; cách frontend admin gọi API; cách `payment-service` lưu & truy vấn giao dịch ngân hàng; entity payment/booking; cách ảnh biên lai được lưu và lấy ra.

## RÀNG BUỘC BẮT BUỘC (không được vi phạm)

1. **AI chỉ chạy khi staff bấm "Xem"** (lazy / on-demand). TUYỆT ĐỐI không chạy AI ở thời điểm user upload.
2. **KHÔNG BAO GIỜ tự động duyệt.** AI chỉ tạo gợi ý. Xác nhận/Từ chối luôn do staff bấm tay. Tách rõ hai loại hành động:
   - `verify` = **chỉ đọc**, chạy AI, trả gợi ý, **KHÔNG đổi trạng thái** payment.
   - `approve` / `reject` = đổi trạng thái, **chỉ được kích hoạt từ click của staff** (dùng cơ chế hiện có).
   - Không có bất kỳ code path nào tự gọi `approve`.
3. **Giao dịch ngân hàng là sự thật (ground truth).** Quyết định mức khớp do **code Java deterministic (`MatchScorer`)** đảm nhiệm — KHÔNG để LLM quyết duyệt. LLM chỉ trích xuất field + chỉ vị trí trong ảnh.
4. **Không tin ảnh một mình.** Mức `HIGH` bắt buộc phải có một giao dịch ngân hàng thật khớp mã đơn + số tiền.
5. **Chống hallucination:** nếu LLM đọc ra mã/giao dịch mà bank API không có → tin bank, bỏ LLM.

## Contract cần triển khai

### Endpoint (ai-service, expose qua gateway)

- `POST /api/ai/payment-proofs/{paymentId}/verify` → trả `VerificationResult`. Read-only, có cache. Hỗ trợ `?refresh=true` để chạy lại.
- Giữ nguyên endpoint approve/reject hiện có. Frontend gọi `verify` **khi mở modal**.

### DTO (đặt tên/đóng gói theo convention dự án; đây là hình dạng yêu cầu)

```java
enum MatchLevel { HIGH, MEDIUM, LOW, SUSPECT }

record VerificationResult(
    MatchLevel matchLevel,
    BankTransaction matchedTx,           // null nếu không có
    ExtractedProof extracted,
    List<String> reasons,                // vì sao ra mức này (cho staff đọc)
    List<String> flags,                  // cờ đỏ chống gian lận
    String aiSuggestion                  // "APPROVE" | "REVIEW" | "REJECT" — CHỈ là gợi ý hiển thị
) {}

record ExtractedProof(
    BigDecimal amount, String orderCode, String transferTime,
    String senderName, String bankName,
    String imageQuality,                 // "clear" | "blurry" | "suspicious_edit"
    Map<String, FieldLocation> fieldLocations
) {}

record FieldLocation(String region, String description, double[] bbox) {} // bbox chuẩn hoá 0..1, có thể null

record BankTransaction(String txId, BigDecimal amount, String content,
                       Instant transactedAt, String senderName, String senderAccount) {}
```

> Lưu ý: `aiSuggestion` chỉ để hiển thị cho staff tham khảo. Không được dùng nó để tự động hoá bất kỳ hành động đổi trạng thái nào.

### Hành vi `verify(paymentId, refresh)`

1. Load payment + booking: mã đơn (order_code), số tiền kỳ vọng, tài khoản nhận, ảnh biên lai.
2. Nếu đã có `verification_log` gần đây cho payment này và `refresh=false` → trả cache.
3. Tra `payment-service` tìm giao dịch ngân hàng có **nội dung chứa order_code** của đơn.
4. Gọi Vision LLM đọc ảnh → `ExtractedProof` (field + vị trí).
5. Tính pHash ảnh, kiểm tra reuse với các ảnh đã dùng cho đơn khác.
6. `MatchScorer` chấm điểm (deterministic) → `VerificationResult`.
7. Ghi `verification_log`, lưu pHash. Trả kết quả. **KHÔNG đổi trạng thái payment.**

### MatchScorer — deterministic, bảng quy tắc

| Mức | Điều kiện | aiSuggestion |
|---|---|---|
| HIGH | Có bank tx khớp order_code + đúng số tiền, không cờ đỏ | APPROVE |
| MEDIUM | Có bank tx nhưng lệch nhẹ (số tiền lệch phí / tên gửi khác) hoặc ảnh có quirk | REVIEW |
| LOW | Chưa có bank tx, nhưng ảnh hợp lý (đúng tiền + đúng order_code, không sửa) | REVIEW |
| SUSPECT | Không khớp ngân hàng và/hoặc ảnh bất thường (lệch tiền, ảnh tái dùng, dấu hiệu sửa) | REJECT |

Cờ đỏ (`flags`): ảnh tái dùng (pHash trùng đơn khác), `imageQuality == "suspicious_edit"`, số tiền ảnh lệch đơn, order_code ảnh khác đơn, thời gian phi lý. **Flags là tín hiệu, không tự từ chối.**

### Vision LLM

- Anthropic Messages API. Model **cấu hình được** (mặc định `claude-sonnet-4-6`, cho phép `claude-haiku-4-5` để rẻ/nhanh). Có thể dùng official Java SDK `com.anthropic:anthropic-java` hoặc Spring `RestClient`.
- `ANTHROPIC_API_KEY` lấy từ biến môi trường, **không commit vào source**.
- Prompt ép **JSON thuần** (không markdown), trả field + `fieldLocations` (region/description/bbox), tuyệt đối không suy đoán giá trị không nhìn thấy (để null).
- Parse an toàn: try/catch, strip ```json, lỗi parse → coi như không trích xuất được (fallback, không crash).

### Frontend — modal "Chi tiết #N"

- Khi mở modal (Xem) → gọi `verify`, hiện loading, rồi render **thẻ kết quả** vào khu "Ảnh chuyển khoản": badge match level (màu theo mức), dòng giao dịch ngân hàng khớp để so trực tiếp, ảnh gốc + overlay/chú thích vị trí field (dùng `fieldLocations`), `reasons`, `flags`, nút **"Chạy lại đối soát"** (gọi `verify?refresh=true`).
- Nút **Xác nhận / Từ chối GIỮ NGUYÊN** hành vi hiện có. Chỉ staff bấm.

## Điều kiện tiên quyết cần xác minh (QUAN TRỌNG)

`booking` hiện là UUID dài (vd `3d437333-d80c-4b55-ac65-f607d00f7c54`). Để tra ngân hàng được, **nội dung chuyển khoản của user phải chứa một mã map về booking**. Hãy kiểm tra trong code/DB: hiện nội dung CK chứa gì? Có order_code ngắn không?

- Nếu **CÓ** mã ngắn → dùng nó làm order_code để tra.
- Nếu **KHÔNG** → đề xuất & triển khai sinh `order_code` ngắn (6–8 ký tự, vd base36 từ booking), hiển thị trong hướng dẫn CK / nhúng vào VietQR; `payment-service` tra theo mã ngắn này. **Nêu rõ thay đổi này trong kế hoạch và DỪNG xin xác nhận trước khi sửa schema/nội dung CK.**

Nếu nội dung CK không chứa mã nào map được → bước tra ngân hàng luôn rỗng → kết quả luôn LOW và tính năng coi như vô hiệu.

## Quy trình làm việc

1. **Khảo sát codebase**, tóm tắt hiện trạng liên quan (services, entity, endpoint approve/reject, cách lưu giao dịch ngân hàng, cách frontend gọi API).
2. **Trình KẾ HOẠCH**: file sẽ tạo/sửa, contract, thay đổi DB (migration theo tool dự án đang dùng), điểm tích hợp frontend, và mọi giả định/điều cần xác nhận — đặc biệt order_code. **Dừng xin xác nhận** nếu cần đổi schema booking hoặc nội dung CK.
3. **Triển khai** theo plan: backend trước (DTO → bank client → MatchScorer → service → endpoint → migration → config), rồi frontend.
4. **Viết test**:
   - Unit test `MatchScorer` cho **mọi nhánh** HIGH/MEDIUM/LOW/SUSPECT.
   - Test `verify()` là **read-only** (không đổi trạng thái payment ở bất kỳ nhánh nào).
   - Test cache (Xem lần 2 không gọi LLM; `refresh=true` thì gọi lại).
   - Test parse JSON lỗi → fallback an toàn.
   - Test pHash trùng → ra cờ đỏ + SUSPECT.
5. Chạy build + test, sửa tới khi xanh.

## Tiêu chí nghiệm thu

- Bấm "Xem" → thẻ AI hiện ra. AI **không** chạy ở thời điểm upload.
- `verify` **không bao giờ** đổi trạng thái payment; **không có** code path nào tự gọi approve.
- `HIGH` chỉ xuất hiện khi có giao dịch ngân hàng khớp thật.
- Xem lần 2 dùng cache; có nút chạy lại đối soát.
- pHash phát hiện ảnh tái dùng → cờ đỏ.
- `ANTHROPIC_API_KEY` không nằm trong source. Toàn bộ test xanh.

## Bảo mật, vận hành & chất lượng

- Không log API key; không đưa dữ liệu nhạy cảm vào URL/query.
- Không sửa các flow ngoài phạm vi. Tuân theo convention dự án (naming, error handling, Feign client, migration tool).
- Xử lý lỗi: bank API timeout → `LOW` + reason rõ ràng, route thủ công; LLM lỗi/parse fail → vẫn dùng kết quả bank, nếu bank rỗng → để staff xử lý tay. Không bao giờ tự nâng lên HIGH khi thiếu ground truth.
- Tối ưu chi phí: chỉ gọi LLM khi Xem + cache kết quả; model cấu hình được.

---

**Bắt đầu bằng bước 1 (khảo sát) và bước 2 (trình kế hoạch). Chưa code cho tới khi mình duyệt plan.**