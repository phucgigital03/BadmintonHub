/**
 * UUID v4 chạy được cả trên http.
 *
 * `crypto.randomUUID()` bị gate `[SecureContext]` → **undefined** khi trang được phục vụ qua
 * http (ALB DNS của staging, hay khi mở dev server bằng IP LAN). Gọi thẳng vào là `TypeError`,
 * và ở `ChatThread.sendText` lời gọi nằm NGOÀI try/catch nên tin nhắn chết hoàn toàn im lặng.
 *
 * `crypto.getRandomValues()` thì KHÔNG bị gate secure-context — chỉ `randomUUID` và
 * `crypto.subtle` mới bị — nên trên http vẫn giữ được nguồn ngẫu nhiên mật mã.
 * `Math.random` chỉ là chốt chặn cuối cho runtime quá cũ.
 *
 * Dùng `Array.from` chứ không phải spread: tsconfig khai `lib: ["ES2023", "DOM"]` và
 * `Array.from` nhận `ArrayLike` nên đúng chắc chắn — `npm run build` là `tsc -b && vite build`,
 * lỗi type ở đây làm hỏng luôn image.
 */
export function uuid(): string {
  const c = globalThis.crypto;
  if (c?.randomUUID) return c.randomUUID();

  if (c?.getRandomValues) {
    const b = c.getRandomValues(new Uint8Array(16));
    b[6] = (b[6] & 0x0f) | 0x40; // version 4
    b[8] = (b[8] & 0x3f) | 0x80; // variant 10xx
    const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    return (ch === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
