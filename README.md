# Helpdesk Ticket System

Đây là dự án cá nhân phát triển ứng dụng tiếp nhận, phân công và theo dõi ticket hỗ trợ. Dự án đang được phát triển. Ở mốc hiện tại, ứng dụng có màn hình đăng nhập, luồng tạo và xem ticket dành cho REQUESTER, cùng API kiểm tra kết nối PostgreSQL.

## Công nghệ và cấu trúc repo

- `backend/` sử dụng Java 21, Spring Boot, Maven Wrapper, Spring Web MVC, Validation, Spring Data JPA và Flyway có hỗ trợ PostgreSQL.
- `frontend/` sử dụng React với JavaScript và Vite.
- `compose.yaml` chạy PostgreSQL 17 trên máy và lưu dữ liệu trong một volume có tên.

Trong quá trình phát triển, Vite chuyển tiếp các yêu cầu `/api` đến Spring Boot tại `http://localhost:8080` để trình duyệt có thể gửi yêu cầu cùng nguồn. Spring Security dùng cookie phiên và bảo vệ các yêu cầu thay đổi dữ liệu bằng CSRF.

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
bash scripts/run-backend.sh
```

Script nạp `.env` và kiểm tra cổng backend trước khi chạy Maven Wrapper. Backend mặc định dùng cổng `8080`. Nếu cổng bận, script hiển thị cổng và PID rồi thoát. Khi `/api/health` xác nhận đó là Helpdesk, bạn có thể dùng bản đang chạy hoặc dừng nó trước khi chạy lại script.

Script đặt múi giờ của tiến trình Spring Boot thành UTC để tránh lỗi PostgreSQL từ chối giá trị TimeZone `"Asia/Saigon"`.

**Cửa sổ 3 — React**

```bash
cd frontend
npm ci
npm run dev
```

Mở `http://localhost:5173` để vào màn hình đăng nhập. Bạn có thể mở một cửa sổ Git Bash khác để kiểm tra kết nối PostgreSQL trực tiếp qua backend và qua Vite.

```bash
curl --fail http://localhost:8080/api/health
curl --fail http://localhost:5173/api/health
```

Cả hai yêu cầu sẽ trả về `{"status":"UP","database":"UP"}`. Endpoint này chạy truy vấn `SELECT 1` trên PostgreSQL. Nếu backend đang chạy nhưng mất kết nối với cơ sở dữ liệu, endpoint trả về HTTP 503 với nội dung `{"status":"DOWN","database":"DOWN"}`.

Để kiểm tra bản dựng backend và chạy bài kiểm thử khởi tạo ngữ cảnh Spring, mở một cửa sổ Git Bash mới tại thư mục gốc và chạy các lệnh sau.

```bash
set -a
source <(sed 's/\r$//' .env)
set +a
cd backend
bash ./mvnw --version
bash ./mvnw test
```

Để kiểm tra bản dựng frontend, chạy `npm run build` trong `frontend/` sau khi chạy `npm ci`.

Nhấn Ctrl+C để dừng các máy chủ phát triển. Từ thư mục gốc của repo, chạy `docker compose --env-file .env down` để dừng PostgreSQL và giữ lại dữ liệu.


## Đăng nhập bằng tài khoản demo

Để bật demo, đặt `HELPDESK_DEMO_ENABLED=true` trong tệp `.env` ở thư mục gốc. Dừng backend đang chạy rồi chạy `bash scripts/run-backend.sh` để nạp cấu hình. Spring Boot tạo các tài khoản còn thiếu khi demo được bật. Khi tắt demo, các tài khoản đã tạo vẫn nằm trong cơ sở dữ liệu nhưng không thể đăng nhập.

| Vai trò | Email | Mật khẩu demo |
| --- | --- | --- |
| REQUESTER | `requester@helpdesk.test` | `HelpdeskDemo!2026` |
| AGENT | `agent@helpdesk.test` | `HelpdeskDemo!2026` |
| ADMIN | `admin@helpdesk.test` | `HelpdeskDemo!2026` |

Mở `http://localhost:5173`, đăng nhập thử và nhấn **Sign out** để kết thúc phiên. Mặc định demo tắt. Chỉ bật trên môi trường phát triển với dữ liệu thử.

React gọi `GET /api/auth/csrf` ngay trước mỗi yêu cầu POST để lấy tên header và token hiện tại. `POST /api/auth/login` nhận `email` và `password` dạng form, `GET /api/auth/me` trả về ID, email và vai trò, còn `POST /api/auth/logout` kết thúc phiên. Spring Security vô hiệu hóa token cũ khi đăng nhập hoặc đăng xuất. React lấy token mới trước lần POST tiếp theo. Phiên thiếu hoặc hết hạn trả về HTTP `401`. Yêu cầu bị từ chối, chẳng hạn POST thiếu token CSRF hợp lệ, trả về HTTP `403`.

## Luồng Requester

Đăng nhập bằng tài khoản demo REQUESTER. Trong **My Tickets**, chọn **New Ticket**, điền tiêu đề, mô tả và độ ưu tiên rồi chọn **Create Ticket**. Ticket mới bắt đầu ở trạng thái `OPEN`. Ứng dụng mở **Ticket Details** sau khi tạo thành công. Bạn có thể quay về **My Tickets** để xem danh sách ticket của mình.

`GET /api/tickets` liệt kê ticket của người đang đăng nhập, `POST /api/tickets` tạo ticket và `GET /api/tickets/{id}` mở chi tiết. Backend lấy người tạo từ phiên đăng nhập. Nếu ticket không tồn tại hoặc thuộc về người khác, API chi tiết trả HTTP `404`.

## Phạm vi dự kiến của MVP

| Vai trò | Quyền truy cập và thao tác trên ticket |
| --- | --- |
| REQUESTER | Tạo ticket, xem và bình luận trên các ticket do mình tạo. |
| ADMIN | Xem và bình luận trên mọi ticket. Phân công hoặc đổi AGENT phụ trách khi ticket còn ở trạng thái `OPEN`. |
| AGENT | Xem và bình luận trên các ticket được giao. Chỉ AGENT đang phụ trách mới có quyền chuyển ticket từ `OPEN` sang `IN_PROGRESS`, rồi sang `RESOLVED`. |

Trong MVP, REQUESTER và ADMIN sẽ không có quyền đổi trạng thái ticket.

Mỗi ticket sẽ có tiêu đề, mô tả, độ ưu tiên, trạng thái, người tạo, người phụ trách, thời điểm tạo và thời điểm cập nhật gần nhất. Hệ thống sẽ ghi lại các lần phân công và đổi trạng thái, kèm người thực hiện và thời điểm của từng thay đổi.

MVP sẽ dùng tài khoản demo và dữ liệu mẫu. Đăng ký công khai, email, tệp đính kèm và trang tổng quan nằm ngoài phạm vi của MVP.

## Data model

Flyway manages the PostgreSQL schema. Hibernate validates the schema at startup.

- `users` stores accounts and their roles. Future API endpoints will use DTOs and omit password hashes.
- `tickets` links to a requester and optionally an assignee in `users`. New tickets start as `OPEN`.
- `ticket_comments` links each comment to a ticket and its author in `users`.
- `ticket_events` links each assignment or status change to a ticket and its actor in `users`. It records the time and the previous and new assignee or status.

## Luồng demo dự kiến

1. Đăng nhập với vai trò REQUESTER và tạo ticket. Ticket có trạng thái ban đầu là `OPEN` và xuất hiện trong danh sách ticket của bạn. Thêm một bình luận.
2. Đăng nhập với vai trò ADMIN, tìm ticket vừa tạo và giao cho một tài khoản demo có vai trò AGENT.
3. Đăng nhập bằng tài khoản AGENT được giao, mở ticket và chuyển sang `IN_PROGRESS`. Thêm bình luận mô tả cách xử lý rồi chuyển ticket sang `RESOLVED`.
4. Đăng nhập lại với vai trò REQUESTER và kiểm tra ticket đã ở trạng thái `RESOLVED`. Sau đó, dùng tài khoản ADMIN để kiểm tra lịch sử có ghi lần phân công và cả hai lần đổi trạng thái, kèm người thực hiện và thời điểm của từng thay đổi.
