# Prompt cho Claude Code — ai-service đối soát thanh toán (BẢN CHỐT)

> Stack: Python · FastAPI · LangGraph · Pydantic. Copy toàn bộ đưa cho Claude Code. Chạy **plan mode** trước; duyệt kế hoạch rồi mới cho thực thi.

---

## Vai trò & mục tiêu

Bạn là senior AI engineer trên dự án **BadmintonHub**. Hãy **LẬP KẾ HOẠCH rồi TRIỂN KHAI** tính năng "đối soát chứng từ chuyển khoản hỗ trợ AI" trong service **`ai-service`** (hiện đang trống — là một service **Python** mới).

Luồng: khi staff bấm **"Xem"** một payment ở tab Quản trị → Duyệt thanh toán, `ai-service` chạy một graph đọc ảnh biên lai, tra giao dịch ngân hàng theo `order_code`, chấm điểm, và trả về **ảnh có tag vị trí thông tin + scorecard + match level gợi ý**. Đây là **công cụ hỗ trợ staff** — staff luôn là người bấm Xác nhận / Từ chối.

## Kiến trúc đã chốt (đọc kỹ trước khi thiết kế)

Một **agent duy nhất** + tools + policy gate tất định + cổng người, điều phối bằng LangGraph:

```
Người dùng (chuyển khoản + upload ảnh) → PENDING
Staff bấm Xem  →  [ai-service · LangGraph]
   agent_node (1 LLM multimodal): ĐỌC ẢNH (native) + gọi 2 tool (bank, reuse) + suy luận → scorecard + tag
   → policy_node (CODE tất định): cổng cứng G + gộp điểm → HIGH / MEDIUM / LOW / SUSPECT
        · lỗi/timeout/ảnh hỏng → fallback thẳng tới human (thủ công), bỏ qua chấm điểm
   → human_review (interrupt): staff xem ảnh-tag + scorecard → Xác nhận/Từ chối
   → finalize: cập nhật trạng thái + báo user
```

## KỶ LUẬT PHẠM VI — đúng 1 agent, KHÔNG over-engineer (bắt buộc tuân thủ)

Đây là các quyết định đã chốt sau khi cân nhắc kỹ. Không tự ý mở rộng:

- **Chỉ MỘT agent LLM** (multimodal). KHÔNG tạo nhiều agent.
- **Đọc ảnh là năng lực native của chính agent đó** — KHÔNG tạo một "vision-agent" riêng, KHÔNG để một agent đọc ảnh rồi đưa cho agent khác. Đọc ảnh là hành động đầu tiên của agent.
- **Tools là hàm thường, KHÔNG phải agent.** Chỉ có 2 tool: `lookup_bank_transaction`, `check_image_reuse`.
- **KHÔNG thêm model/agent phát hiện ảnh giả (forgery).** Chống gian lận dựa vào: cổng cứng ngân hàng `G` + pHash chống ảnh tái dùng + cờ `imageQuality` của LLM + người duyệt. (Forgery detection trên screenshot không đáng tin và ROI thấp.)
- **Quyết định match level do CODE tất định (`policy_node`) làm, KHÔNG phải LLM.**

## Stack bắt buộc (production-grade, Python)

- **FastAPI** + Uvicorn/Gunicorn
- **LangGraph** — orchestration graph, human-in-the-loop (interrupt), checkpoint
- **Pydantic v2** — structured output (LLM trả thẳng vào model, KHÔNG parse JSON tay) + toàn bộ DTO
- **LLM provider qua config (provider-agnostic)** — mặc định **Google Gemini 2.5 Flash** (multimodal vision native, free tier không cần thẻ, đọc tiếng Việt tốt, structured output). Provider + model + API key đều từ `pydantic-settings` → đổi sang OpenAI / Anthropic / Ollama (local, riêng tư) **chỉ qua `.env`, KHÔNG sửa code lõi**. Dev dùng free tier; người dùng thật bật billing Gemini (~vài cent/tháng ở quy mô pilot) để có cam kết **không-train-trên-dữ-liệu** + rate limit ổn định. (LLM chỉ trích xuất + chấm sub-score; ground truth là giao dịch ngân hàng + cổng `G` tất định + staff — nên không phụ thuộc một model "hoàn hảo".)
- **httpx** (gọi payment-service), **SQLAlchemy + Alembic** (Postgres `ai_db` + migration), **redis-py** (cache), **Pillow + imagehash** (pHash)
- **Observability**: LangSmith hoặc Pydantic Logfire + OpenTelemetry export sang **Zipkin** (đã có trong hệ)
- **pydantic-settings** (config), **structlog** (log có cấu trúc)

## RÀNG BUỘC BẮT BUỘC

1. **AI chỉ chạy khi staff bấm "Xem"** (lazy). KHÔNG chạy lúc user upload.
2. **KHÔNG auto-confirm.** `verify` = read-only (chạy graph tới `human_review`, trả kết quả). `approve`/`reject` chỉ do staff bấm (resume graph hoặc endpoint riêng, dùng cơ chế hiện có). Không code path nào tự gọi approve.
3. **LLM/agent KHÔNG là thẩm quyền cuối trên money.** Quyết định mức + cổng cứng `G` do `policy_node` (code) làm. **HIGH bắt buộc `G = true`.**
4. **Ground truth = giao dịch ngân hàng.** Không tin ảnh một mình.
5. **Chống hallucination:** agent đọc ra mã/giao dịch mà tool bank không có → bỏ, theo bank.

## Graph nodes (LangGraph)

1. **`agent_node`** (LLM multimodal, `temperature=0`): đọc ảnh native → trích xuất field + `field_locations` (tag vị trí) + **scorecard** (sub-score 0..1 mỗi tiêu chí quan sát được trên ảnh). Được trang bị 2 tool, tự suy luận trên kết quả (ảnh mờ thì thử đọc lại; nhiều giao dịch khớp thì chọn cái sát nhất, giải thích):
   - `lookup_bank_transaction(order_code, amount)` → gọi `payment-service` qua httpx.
   - `check_image_reuse(phash)` → tra store pHash.
2. **`policy_node`** (CODE tất định): nhận output agent + kết quả tool, áp cổng cứng `G` + gộp điểm → match level (bảng dưới). Agent KHÔNG override được `G`. Khi tool lỗi/timeout hoặc ảnh không đọc được → **fallback**: đặt trạng thái "cần xử lý tay" và đi thẳng `human_review`, không chấm điểm bừa.
3. **`human_review`** (interrupt): graph dừng, trả `VerificationResult` cho UI; resume khi staff Xác nhận/Từ chối.
4. **`finalize_node`**: cập nhật trạng thái payment; chỉ chạy **sau** quyết định của người.

### Bảng policy (4 case + fallback)

| `G` | `soft` | cờ đỏ | → match level | suggestion |
|---|---|---|---|---|
| true | ≥ 85 | không | **HIGH** | APPROVE |
| true | 60–85 | — | **MEDIUM** | REVIEW |
| false (chưa có giao dịch) | bất kỳ | — | **LOW** | REVIEW (không approve được) |
| — | — | reuse / sửa ảnh | **SUSPECT** | REJECT |
| (lỗi/timeout/ảnh hỏng) | — | — | **ERROR → thủ công** | REVIEW (bỏ qua chấm điểm) |

`soft` = Σ trọng số · sub-score của agent. **Cả 5 nhánh đều dẫn về `human_review`; không nhánh nào tự đổi trạng thái.**

## Contract

**Endpoints** (`ai-service`, lộ qua gateway):
- `POST /api/ai/payment-proofs/{paymentId}/verify[?refresh=true]` → `VerificationResult`. Read-only, có cache; chạy graph tới interrupt `human_review`.
- Resume/approve/reject: dùng cơ chế approve/reject hiện có; nếu cần endpoint resume graph thì tách riêng, **chỉ gọi từ click staff**.

**Pydantic models:** `MatchLevel` (enum HIGH/MEDIUM/LOW/SUSPECT/ERROR), `VerificationResult` (matchLevel, matchedTx, extracted, reasons, flags, aiSuggestion — *aiSuggestion chỉ để hiển thị, không tự động hoá*), `ExtractedProof` (amount, orderCode, senderName, bankName, imageQuality, `field_locations`, `criteria` scorecard), `CriterionCheck` (name, verdict, confidence), `FieldLocation` (region, description, bbox 0..1 nullable), `BankTransaction`.

## Điều kiện tiên quyết cần xác minh (DỪNG xin xác nhận nếu phải đổi)

- `booking` là UUID dài → cần **`order_code` ngắn (6–8 ký tự)** trong nội dung CK để tool bank tra được. Kiểm tra hiện có chưa; nếu chưa → đề xuất sinh mã ngắn + nhúng VietQR + `payment-service` tra theo mã ngắn.
- **Ảnh biên lai lưu ở đâu** (DB / filesystem / object storage) để agent + pHash lấy bytes.
- **Nguồn giao dịch ngân hàng**: `payment-service` tự lưu hay dịch vụ ngoài (Casso/SePay).

## Tích hợp với hệ Java (Eureka + Spring Cloud Gateway)

`ai-service` Python cần lộ qua gateway: hoặc đăng ký Eureka bằng `py-eureka-client`, hoặc cấu hình **route tĩnh** ở gateway. Đề xuất cách hợp với hạ tầng hiện tại trong kế hoạch.

## Production-grade (bắt buộc)

- `temperature = 0` cho agent.
- **Snapshot vào `verification_log` mỗi lần chạy:** output agent (scorecard, field_locations), kết quả tool, match level, **model + prompt version** → tái lập & audit từng quyết định.
- **Unit test:** mọi nhánh policy (HIGH/MEDIUM/LOW/SUSPECT/ERROR); test "**agent không thể ra HIGH khi `G = false`**"; test `verify` read-only (không đổi trạng thái ở bất kỳ nhánh nào); test cache (`refresh=true` mới chạy lại); test structured-output lỗi → fallback; test pHash trùng → SUSPECT; test tool timeout → ERROR/thủ công.
- **Eval harness** trên tập ảnh có nhãn để đo độ chính xác agent trước khi tin.
- Observability: trace mỗi run + mỗi tool call.
- Secrets (API key) qua env, không commit.
- Xử lý độ trễ ngân hàng: tiền có thể về trễ → đừng nhầm "bank trễ" (→ LOW, vẫn chấm) với "lỗi kỹ thuật" (→ ERROR/thủ công).

## Frontend (React 18 + TypeScript + Vite + Tailwind + React Query)

- Modal "Chi tiết #N": khi mở (Xem) gọi `verify` qua React Query (loading, cache). Render: **badge match level** (màu), **ảnh gốc + overlay tag** từ `field_locations`, **scorecard** (sub-score + lý do), dòng giao dịch ngân hàng khớp, nút **"Chạy lại đối soát"** (`refresh=true`).
- Nút **Xác nhận / Từ chối giữ nguyên**, chỉ staff bấm.

## Quy trình làm việc

1. **Khảo sát codebase** + tóm tắt hiện trạng (services, payment/booking, endpoint approve/reject, nguồn giao dịch ngân hàng, nơi lưu ảnh, cách frontend gọi API).
2. **Trình KẾ HOẠCH**: file tạo/sửa, Pydantic models, graph nodes, migration Alembic, điểm tích hợp gateway, frontend. **DỪNG xin xác nhận**: (1) `order_code` ngắn — dùng cái có sẵn hay sinh mới; (2) **provider LLM đã chốt: Google Gemini 2.5 Flash** (provider-agnostic, đổi qua `.env`) — chỉ cần xác nhận có `GEMINI_API_KEY` + chạy free-tier hay bật billing; (3) cách lộ qua gateway — Eureka hay route tĩnh.
3. **Triển khai backend** (models → tools → `agent_node` → `policy_node` → graph + interrupt → endpoint → migration → config) rồi **frontend**.
4. **Test + eval**, chạy tới khi xanh.

## Tiêu chí nghiệm thu

- Bấm "Xem" mới chạy; không chạy lúc upload.
- Đúng **1 agent LLM**; không có vision-agent riêng, không model forgery.
- `verify` không đổi trạng thái; không path nào tự approve.
- **HIGH chỉ khi `G = true`** (có test chứng minh agent không vượt cổng cứng).
- Cả 5 nhánh (HIGH/MEDIUM/LOW/SUSPECT/ERROR) đều về `human_review`; chỉ click người mới đổi trạng thái.
- pHash trùng → SUSPECT; tool lỗi/timeout → ERROR/thủ công.
- Mỗi run có snapshot audit + model/prompt version. Secrets không trong source. Test + eval xanh.

---

**Bắt đầu bằng khảo sát + kế hoạch. Chưa code tới khi mình duyệt plan. Khi hỏi, hỏi gọn: order_code và cách lộ qua gateway (provider đã chốt = Google Gemini 2.5 Flash, provider-agnostic qua `.env`).**