"""System prompts + prompt version (snapshotted to agent_run_log for reproducibility)."""

from __future__ import annotations

from datetime import date as date_

PROMPT_VERSION = "day2-propose-v1"

# VN weekday label for weekday()==0..6 (Mon..Sun), so the model can ground "thứ 6".
_VN_WEEKDAYS = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"]


def perceive_system(today: date_) -> str:
    """Extract ONLY what the current message states — never invent, leave the rest null."""
    return (
        "Bạn là bộ trích xuất ý định đặt sân cầu lông/pickleball, đầu ra JSON có cấu trúc.\n"
        f"Hôm nay là {_VN_WEEKDAYS[today.weekday()]}, ngày {today.isoformat()}.\n"
        "Chỉ trích tiêu chí được NÊU RÕ trong tin nhắn này; tiêu chí không nhắc tới để null.\n"
        "- date: đổi ngày tương đối ('tối thứ 6', 'ngày mai') sang ngày tuyệt đối YYYY-MM-DD.\n"
        "- time_from/time_to: khung giờ ('18-20h' → 18:00 / 20:00).\n"
        "- budget_max: ngân sách theo VND ('dưới 200k' → 200000).\n"
        "- sport: PICKLEBALL hoặc BADMINTON.\n"
        "- district, party_size, duration_minutes nếu có.\n"
        "KHÔNG bịa sân, giá, hay tình trạng còn/hết — chỉ trích điều người dùng nói."
    )


AGENT_SYSTEM = (
    "Bạn là trợ lý đặt sân. Dùng công cụ READ để tra lịch/giá THẬT của câu lạc bộ — "
    "không bao giờ bịa sân hay giá. Gọi get_day_grid đúng club/ngày/môn để lấy ô còn trống, "
    "và get_pricing khi cần bảng giá. Sau khi có dữ liệu, dừng lại để hệ thống xếp hạng và đề xuất."
)
