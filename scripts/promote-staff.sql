-- ============================================================================
-- promote-staff.sql — cấp quyền STAFF cho một user ĐÃ ĐĂNG KÝ
-- ----------------------------------------------------------------------------
-- BadmintonHub không có seeder tạo sẵn STAFF/ADMIN (user mới luôn chỉ có role USER).
-- Để test các thao tác STAFF (duyệt / từ chối / hoàn tiền thanh toán) cần nâng
-- quyền 1 tài khoản bằng tay.
--
-- SQL THUẦN (không có lệnh \ của psql) → chạy được ở CẢ DataGrip, psql, docker exec.
--
-- CÁCH DÙNG
--   0. Đăng ký trước 1 tài khoản qua FE (/register), vd email: staff@test.local
--   1. SỬA email ở 2 chỗ bên dưới (mặc định 'staff@test.local')
--   2. Chạy 1 trong 3 cách:
--      • DataGrip : mở file (hoặc dán SQL) vào console nối user_db@localhost → chọn hết → Run
--      • psql CLI : (từ gốc repo)
--          psql "postgresql://postgres:postgres@localhost:5441/user_db" -f scripts/promote-staff.sql
--      • docker   : (không cần psql cài máy)
--          docker exec -i postgres-user psql -U postgres -d user_db < scripts/promote-staff.sql
--   3. Tài khoản đó ĐĂNG XUẤT rồi ĐĂNG NHẬP LẠI để JWT mới mang role STAFF → /admin mở được.
--
-- Idempotent: chạy nhiều lần an toàn (ON CONFLICT DO NOTHING).
-- Schema tham chiếu: Role.java (roles.name = enum RoleName, UNIQUE) +
--                    User.java (@JoinTable user_roles(user_id, role_id), PK kép).
-- ============================================================================

-- 1) Đảm bảo role STAFF tồn tại (created_at/updated_at NOT NULL nên phải set tay)
INSERT INTO roles (id, name, created_at, updated_at)
VALUES (gen_random_uuid(), 'STAFF', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- 2) Gắn role STAFF cho user theo email  ← SỬA email ở đây
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'staff@test.local'
  AND r.name = 'STAFF'
ON CONFLICT DO NOTHING;

-- 3) Kiểm tra kết quả — phải thấy {STAFF,USER}  ← SỬA email ở đây
SELECT u.email, array_agg(r.name ORDER BY r.name) AS roles
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r       ON r.id = ur.role_id
WHERE u.email = 'staff@test.local'
GROUP BY u.email;
