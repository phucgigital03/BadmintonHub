"""PII masking (§11.6) — no raw phone reaches logs or the audit sink."""

from app.security.pii import mask_phone, redact_pii, scrub


def test_mask_phone_masks_vn_numbers_keeps_last_two():
    out = mask_phone("Liên hệ 0901234567 nhé")
    assert "0901234567" not in out
    assert out.endswith("67 nhé")
    assert mask_phone("gọi +84901234567").count("*") >= 8


def test_mask_phone_leaves_short_numbers_and_prices():
    # a 6-digit price / order code is not a phone → untouched
    assert mask_phone("tổng 999000đ, mã #184") == "tổng 999000đ, mã #184"


def test_scrub_redacts_pii_keys_and_masks_nested_strings():
    blob = {
        "customer_phone": "0901234567",
        "customerName": "Nguyễn Văn A",
        "note": "gọi 0912345678 giúp",
        "items": [{"slotId": "x"}],
    }
    out = scrub(blob)
    assert out["customer_phone"] == "***"
    assert out["customerName"] == "***"
    assert "0912345678" not in out["note"]
    assert out["items"] == [{"slotId": "x"}]  # non-PII passthrough


def test_redact_pii_processor():
    ev = redact_pii(None, "info", {"event": "hold", "customer_phone": "0901234567"})
    assert ev["customer_phone"] == "***"
    ev2 = redact_pii(None, "error", {"event": "err", "error": "failed for 0901234567"})
    assert "0901234567" not in ev2["error"]
