# Hướng dẫn cài đặt AstraxQuest

## 1. Chuẩn bị
- Server **Paper 1.21.4** chạy **Java 21** (`java -version`).
- (Khuyên dùng) [Vault](https://www.spigotmc.org/resources/vault.34315/) + một plugin kinh tế (EssentialsX, CMI, XConomy...).
- (Khuyên dùng) [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).

## 2. Cài plugin
1. Lấy file JAR: tự build (`mvn clean package`) hoặc tải từ tab **Actions → Artifacts** của repo GitHub.
2. Chép `AstraxQuest-1.0.0.jar` vào thư mục `plugins/`.
3. Khởi động server. Kiểm tra console có dòng:
   ```
   [AstraxQuest] Co so du lieu: SQLite san sang.
   [AstraxQuest] Da nap 22 nhiem vu, 2 su kien, 6 danh hieu.
   ```
4. Dừng/khởi động lại nếu bạn muốn sửa `database` trong `config.yml`.

## 3. Cấu hình cơ bản
- `config.yml`: múi giờ, giờ reset, cấp độ, chống gian lận.
- `quests/daily.yml`, `weekly.yml`, `pvp.yml`: sửa thưởng / thêm nhiệm vụ (xem mục *Thêm nhiệm vụ / sửa phần thưởng trong 30 giây* trong README). Cốt truyện, Guild, Sự kiện đang tắt, bật bằng `enabled: true`.
- `/quest admin reload` để áp dụng (riêng `database` cần restart).

## 4. Dùng MySQL (tùy chọn)
```yaml
database:
  type: MYSQL
  mysql: { host: 127.0.0.1, port: 3306, database: astraxquest, username: user, password: "mat_khau" }
```
Tạo sẵn database rỗng `astraxquest` (utf8mb4). Plugin tự tạo bảng. Nhiều server có thể dùng chung một database.
Dữ liệu SQLite cũ **không** tự chuyển sang MySQL.

## 5. Kết nối Guild / BoxMine / chợ đấu giá
Xem mục *Tích hợp* trong README. Kiểm tra nhanh một tích hợp bằng lệnh:
```
/quest admin trigger <ten_ban> OPEN_BOXMINE basic 1
```
nếu bạn đã thêm một nhiệm vụ `type: OPEN_BOXMINE` và tiến độ của nó tăng thì phần nhiệm vụ đã sẵn sàng, chỉ còn nối sự kiện của plugin kia.

## Kiểm tra sau khi cài
1. `/quest` mở được menu; vào từng mục thấy nhiệm vụ.
2. `/quest admin addprogress <ban> dao_block 100` (hoặc đào thật) → hiện thông báo hoàn thành, nút nhiệm vụ sáng.
3. Nhấp để nhận → nhận tiền/token/EXP/vật phẩm; nhấp lần hai → báo "đã nhận".
4. `/quest admin audit <ban>` thấy dòng `CLAIM ... [DELIVERED]`.
5. Restart server → tiến độ, token, cấp, danh hiệu còn nguyên.
6. Đặt khối rồi phá khối đó (nhiệm vụ đào) → không tăng tiến độ.
7. Dùng `/quest admin reset <ban> daily` → nhận lại được.
8. (Chỉ khi bạn bật `quests/guild.yml`) Guild: hai thành viên cùng guild cùng đóng góp → tiến độ chung; mỗi người nhận thưởng một lần.

## Gỡ lỗi
- `The embedded resource '...yml' cannot be found in ...jar` / `File JAR thieu tai nguyen`: file JAR build thiếu tài nguyên. Kiểm tra bằng `unzip -l AstraxQuest-1.0.0.jar | grep yml` (phải thấy `config.yml`, `messages.yml`, `gui.yml`, `quest-daily.yml`...). Hãy build bằng `mvn clean package` và dùng đúng `target/AstraxQuest-1.0.0.jar` (không dùng `original-...jar`).
- Console cảnh báo `[daily.yml:id] ...` = nhiệm vụ cấu hình sai và bị bỏ qua.
- `Khong tim thay JDBC driver`: dùng đúng Paper 1.21.4 chính thức.
- Thưởng tiền không vào: kiểm tra Vault + plugin kinh tế, hoặc đặt `fallback-command`.
- Guild báo "Cần gia nhập Guild": kiểm tra `/papi parse <ten_ban> %guild_name%` trả đúng tên guild.
