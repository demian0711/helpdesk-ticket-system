# Helpdesk Ticket System

Đây là dự án cá nhân phát triển ứng dụng tiếp nhận, phân công và theo dõi ticket hỗ trợ. Dự án đang được phát triển. Ở mốc hiện tại, ứng dụng có màn hình kiểm tra kết nối giữa React, Spring Boot và PostgreSQL.

## Công nghệ và cấu trúc repo

- `backend/` sử dụng Java 21, Spring Boot, Maven Wrapper, Spring Web MVC, Validation, Spring Data JPA và Flyway có hỗ trợ PostgreSQL.
- `frontend/` sử dụng React với JavaScript và Vite.
- `compose.yaml` chạy PostgreSQL 17 trên máy và lưu dữ liệu trong một volume có tên.

Trong quá trình phát triển, Vite chuyển tiếp các yêu cầu `/api` đến Spring Boot tại `http://localhost:8080` để trình duyệt có thể gửi yêu cầu cùng nguồn. Ở mốc xác thực, dự án sẽ bổ sung Spring Security với cookie phiên và cơ chế bảo vệ CSRF.

## Chạy trên máy bằng Git Bash

Cài JDK 21, Node.js 22.12 trở lên trong dòng 22.x (hoặc một bản LTS mới hơn được hỗ trợ) và Docker Desktop có plugin Docker Compose. Khởi động Docker Desktop rồi mở ba cửa sổ Git Bash tại thư mục gốc của repo.

**Cửa sổ 1 — PostgreSQL**

```bash
[ -f .env ] || cp .env.example .env
docker compose --env-file .env up -d --wait postgres
docker compose ps
```

**Cửa sổ 2 — Spring Boot**

Chạy khối lệnh sau từ thư mục gốc của repo.

```bash
set -a
source <(sed 's/\r$//' .env)
set +a
cd backend
bash ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"
```

Tùy chọn này đặt múi giờ của tiến trình Spring Boot thành UTC để tránh lỗi PostgreSQL từ chối giá trị TimeZone `"Asia/Saigon"`.

**Cửa sổ 3 — React**

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173`. Khi kết nối thành công, trang sẽ hiển thị **Backend and PostgreSQL are connected.** Bạn cũng có thể mở một cửa sổ Git Bash khác để gọi API trực tiếp và qua Vite.

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:5173/api/health
```

Cả hai yêu cầu sẽ trả về `{"status":"UP","database":"UP"}`. Endpoint này chạy truy vấn `SELECT 1` trên PostgreSQL. Nếu backend đang chạy nhưng mất kết nối với cơ sở dữ liệu, endpoint trả về HTTP 503 với nội dung `{"status":"DOWN","database":"DOWN"}`.

Để kiểm tra bản dựng backend và chạy bài kiểm thử khởi tạo ngữ cảnh Spring, mở một cửa sổ Git Bash mới, nạp biến môi trường cơ sở dữ liệu và vào thư mục `backend/` như ở cửa sổ 2. Sau đó chạy `bash ./mvnw --version` và `bash ./mvnw test`. Để kiểm tra bản dựng frontend, chạy `npm run build` trong `frontend/` sau khi chạy `npm ci`.

Nhấn Ctrl+C để dừng các máy chủ phát triển. Từ thư mục gốc của repo, chạy `docker compose --env-file .env down` để dừng PostgreSQL và giữ lại dữ liệu.


## Phạm vi dự kiến của MVP

| Vai trò | Quyền truy cập và thao tác trên ticket |
| --- | --- |
| REQUESTER | Tạo ticket, xem và bình luận trên các ticket do mình tạo. |
| ADMIN | Xem và bình luận trên mọi ticket. Phân công hoặc đổi AGENT phụ trách khi ticket còn ở trạng thái `OPEN`. |
| AGENT | Xem và bình luận trên các ticket được giao. Chỉ AGENT đang phụ trách mới có quyền chuyển ticket từ `OPEN` sang `IN_PROGRESS`, rồi sang `RESOLVED`. |

Trong MVP, REQUESTER và ADMIN sẽ không có quyền đổi trạng thái ticket.

Mỗi ticket sẽ có tiêu đề, mô tả, độ ưu tiên, trạng thái, người tạo, người phụ trách, thời điểm tạo và thời điểm cập nhật gần nhất. Hệ thống sẽ ghi lại các lần phân công và đổi trạng thái, kèm người thực hiện và thời điểm của từng thay đổi.

MVP sẽ dùng tài khoản demo và dữ liệu mẫu. Đăng ký công khai, email, tệp đính kèm và trang tổng quan nằm ngoài phạm vi của MVP.

## Luồng demo dự kiến

1. Đăng nhập với vai trò REQUESTER và tạo ticket. Ticket có trạng thái ban đầu là `OPEN` và xuất hiện trong danh sách ticket của bạn. Thêm một bình luận.
2. Đăng nhập với vai trò ADMIN, tìm ticket vừa tạo và giao cho một tài khoản demo có vai trò AGENT.
3. Đăng nhập bằng tài khoản AGENT được giao, mở ticket và chuyển sang `IN_PROGRESS`. Thêm bình luận mô tả cách xử lý rồi chuyển ticket sang `RESOLVED`.
4. Đăng nhập lại với vai trò REQUESTER và kiểm tra ticket đã ở trạng thái `RESOLVED`. Sau đó, dùng tài khoản ADMIN để kiểm tra lịch sử có ghi lần phân công và cả hai lần đổi trạng thái, kèm người thực hiện và thời điểm của từng thay đổi.
