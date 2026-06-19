# Prompt cho Claude Code — ai-service đối soát thanh toán (Python · FastAPI · LangGraph · agentic)

> Copy đưa cho Claude Code. Chạy **plan mode** trước, duyệt kế hoạch rồi mới cho thực thi.

---

## Vai trò & mục tiêu

Bạn là senior AI engineer trên dự án **BadmintonHub**. Hãy **LẬP KẾ HOẠCH rồi TRIỂN KHAI** tính năng "đối soát chứng từ chuyển khoản hỗ trợ AI" trong service **`ai-service`** (hiện đang trống — đây là một service **Python** mới).

Luồng: khi staff bấm **"Xem"** một payment ở tab Quản trị → Duyệt thanh toán, `ai-service` chạy một graph **agentic** đọc ảnh biên lai, tra giao dịch ngân hàng theo `order_code`, chấm điểm, và trả về **ảnh có tag vị trí thông tin + scorecard + match level gợi ý**. Staff luôn là người bấm **Xác nhận / Từ chối**.

## Stack bắt buộc (production-grade, Python)

- **FastAPI** + Uvicorn/Gunicorn
- **LangGraph** — orchestration graph, human-in-the-loop (interrupt), checkpoint
- **Pydantic v2** — structured output (LLM trả thẳng vào model, không parse JSON tay) + toàn bộ DTO
- **LLM provider qua config** — OpenAI (mặc định, theo định hướng dự án) hoặc Anthropic; chọn bằng env, đổi provider không sửa code lõi
- **httpx** (gọi payment-service), **SQLAlchemy + Alembic** (Postgres `ai_db` + migration), **redis-py** (cache), **Pillow + imagehash** (pHash)
- **Observability**: LangSmith hoặc Pydantic Logfire + OpenTelemetry export sang **Zipkin** (đã có trong hệ)
- **pydantic-settings** (config), **structlog** (log có cấu trúc)

## Kiến trúc graph (LangGraph) — agent đề xuất, policy quyết, người duyệt

**Nodes:**

1. **`agent_node`** (LLM, `temperature=0`) — một tool-calling agent. Đọc ảnh → trích xuất field + `field_locations` (tag vị trí) + **scorecard** (sub-score 0..1 cho mỗi tiêu chí quan sát được trên ảnh). Được trang bị 2 tool và **tự suy luận** trên kết quả:
   - `lookup_bank_transaction(order_code, amount)` → gọi `payment-service` qua httpx.
   - `check_image_reuse(phash)` → tra store pHash.
   - Suy luận: ảnh mờ thì thử đọc lại; nhiều giao dịch khớp thì chọn cái sát nhất và giải thích.
2. **`policy_node`** (CODE TẤT ĐỊNH — KHÔNG LLM) — nhận output agent + kết quả tool, áp:
   - **Cổng cứng `G`** = có bank tx khớp `order_code` + đúng `amount`.
   - `soft` = Σ trọng số · sub-score.
   - Map ra match level + suggestion theo bảng dưới. **Agent KHÔNG override được `G`.**
3. **`human_review`** (interrupt) — graph dừng, trả `VerificationResult` cho UI; resume khi staff Xác nhận/Từ chối.
4. **`finalize_node`** — cập nhật trạng thái payment; **chỉ chạy sau quyết định của người**.

**Bảng policy (4 case):**

| `G` | `soft` | cờ đỏ | → match level | suggestion |
|---|---|---|---|---|
| true | ≥ 85 | không | **HIGH** | APPROVE |
| true | 60–85 | — | **MEDIUM** | REVIEW |
| false | bất kỳ | — | **LOW** | REVIEW (không approve được) |
| — | — | reuse / sửa ảnh | **SUSPECT** | REJECT |

**Cả 4 case đều dẫn về `human_review`. Không case nào tự đổi trạng thái.**

## RÀNG BUỘC BẮT BUỘC (không vi phạm)

1. **AI chỉ chạy khi staff bấm "Xem"** (lazy). Không chạy lúc user upload.
2. **KHÔNG auto-confirm.** `verify` = read-only (chạy graph tới `human_review`, trả kết quả). `approve`/`reject` chỉ do staff bấm (resume graph hoặc endpoint riêng — dùng cơ chế hiện có). Không code path nào tự gọi approve.
3. **LLM/agent KHÔNG là thẩm quyền cuối trên money.** Quyết định mức + cổng cứng `G` do `policy_node` (code) làm. **HIGH bắt buộc `G = true`.**
4. **Ground truth = giao dịch ngân hàng.** Không tin ảnh một mình.
5. **Chống hallucination:** agent đọc ra mã/giao dịch mà tool bank không có → bỏ, theo bank.

## Contract

**Endpoints** (`ai-service`, lộ qua gateway):
- `POST /api/ai/payment-proofs/{paymentId}/verify[?refresh=true]` → `VerificationResult`. Read-only, có cache; chạy graph tới interrupt `human_review`.
- Resume/approve/reject: dùng cơ chế approve/reject hiện có; nếu cần endpoint resume graph thì tách riêng, **chỉ gọi từ click staff**.

**Pydantic models:** `MatchLevel` (enum HIGH/MEDIUM/LOW/SUSPECT), `VerificationResult` (matchLevel, matchedTx, extracted, reasons, flags, aiSuggestion — *aiSuggestion chỉ để hiển thị*), `ExtractedProof` (field + `field_locations` + `criteria` scorecard), `FieldLocation` (region, description, bbox 0..1 nullable), `BankTransaction`, `CriterionCheck` (name, verdict, confidence).

## Điều kiện tiên quyết cần xác minh (DỪNG xin xác nhận nếu phải đổi)

- `booking` là UUID dài → cần **`order_code` ngắn (6–8 ký tự)** trong nội dung CK để tool bank tra được. Kiểm tra hiện có chưa; nếu chưa → đề xuất sinh mã ngắn + nhúng VietQR + `payment-service` tra theo mã ngắn.
- Xác minh **ảnh biên lai lưu ở đâu** (DB / filesystem / object storage) để agent + pHash lấy bytes.
- Xác minh **nguồn giao dịch ngân hàng**: `payment-service` tự lưu hay dịch vụ ngoài (Casso/SePay).

## Tích hợp với hệ Java (Eureka + Spring Cloud Gateway)

`ai-service` Python cần lộ qua gateway: hoặc đăng ký Eureka bằng `py-eureka-client`, hoặc cấu hình **route tĩnh** ở gateway tới `ai-service`. Đề xuất cách hợp với hạ tầng hiện tại trong kế hoạch.

## Production-grade (bắt buộc)

- `temperature = 0` cho agent.
- **Snapshot vào `verification_log` mỗi lần chạy:** output agent (scorecard, field_locations), kết quả tool, match level, **model + prompt version** → tái lập & audit được từng quyết định.
- **Unit test:** mọi nhánh policy (HIGH/MEDIUM/LOW/SUSPECT); test "**agent không thể ra HIGH khi `G = false`**"; test `verify` read-only (không đổi trạng thái ở bất kỳ nhánh nào); test cache (`refresh=true` mới chạy lại); test structured-output lỗi → fallback an toàn; test pHash trùng → SUSPECT.
- **Eval harness** trên tập ảnh có nhãn để đo độ chính xác agent trước khi tin.
- Observability: trace mỗi run + mỗi tool call.
- Secrets (API key) qua env, không commit.

## Frontend (React 18 + TypeScript + Vite + Tailwind + React Query)

- Modal "Chi tiết #N": khi mở (Xem) gọi `verify` qua React Query (loading, cache). Render: **badge match level** (màu), **ảnh gốc + overlay tag** từ `field_locations`, **scorecard** (sub-score + lý do), dòng giao dịch ngân hàng khớp, nút **"Chạy lại đối soát"** (`refresh=true`).
- Nút **Xác nhận / Từ chối giữ nguyên**, chỉ staff bấm.

## Quy trình làm việc

1. **Khảo sát codebase** + tóm tắt hiện trạng (services, payment/booking, endpoint approve/reject, nguồn giao dịch ngân hàng, nơi lưu ảnh, cách frontend gọi API).
2. **Trình KẾ HOẠCH**: file tạo/sửa, Pydantic models, graph nodes, migration Alembic, điểm tích hợp gateway, phần frontend. **DỪNG xin xác nhận** ba thứ: `order_code`, **provider LLM (OpenAI/Anthropic)**, cách lộ qua gateway.
3. **Triển khai backend** (models → tools → `agent_node` → `policy_node` → graph + interrupt → endpoint → migration → config) rồi **frontend**.
4. **Test + eval**, chạy tới khi xanh.

## Tiêu chí nghiệm thu

- Bấm "Xem" mới chạy; không chạy lúc upload.
- `verify` không đổi trạng thái; không path nào tự approve.
- **HIGH chỉ khi `G = true`** (có test chứng minh agent không vượt được cổng cứng).
- Cả 4 case đều dẫn về `human_review`; chỉ click người mới đổi trạng thái.
- pHash trùng → SUSPECT.
- Mỗi run có snapshot audit + model/prompt version. Secrets không trong source. Test + eval xanh.

---

**Bắt đầu bằng khảo sát + kế hoạch. Chưa code tới khi mình duyệt plan. Khi hỏi, hỏi gọn 3 thứ: order_code, provider, cách lộ qua gateway.**
