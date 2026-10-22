# AstraxQuest

Plugin nhiệm vụ cho **Paper 1.21.4** (Java 21): nhiệm vụ **ngày / tuần / cốt truyện / Guild / PvP / sự kiện**,
phần thưởng **tiền, Astrax Token, Quest EXP, vật phẩm, danh hiệu**, GUI trực quan, lưu dữ liệu **SQLite** (tùy chọn **MySQL**).

[![Build](../../actions/workflows/build.yml/badge.svg)](../../actions)

## Tính năng

| Nhóm | Chi tiết |
|---|---|
| Loại nhiệm vụ | Ngày, Tuần, Cốt truyện (chuỗi, theo chương), Guild (tiến độ chung), PvP, Sự kiện (theo thời gian) |
| Mục tiêu | Đào/đặt khối, thu hoạch cây chín, giết quái, giết người, câu cá, chế tạo, nung, nhân giống, phù phép, ăn uống, đi bộ, thời gian chơi (không AFK) và loại ngoài: BoxMine, chợ đấu giá, đóng góp Guild, CUSTOM |
| Phần thưởng | Tiền (Vault), Astrax Token, Quest EXP (có cấp độ), vật phẩm (tên/lore tùy chỉnh), lệnh console, danh hiệu |
| GUI | Menu chính, danh sách có phân trang, thanh tiến độ, trạng thái (đang làm / nhận được / đã nhận / khóa), "Nhận tất cả", menu danh hiệu |
| Cấu hình | Mọi nhiệm vụ, mục tiêu, thưởng, giờ reset, GUI, thông điệp đều trong file YAML |
| Dữ liệu | SQLite mặc định, MySQL tùy chọn, ghi bất đồng bộ, nhật ký giao dịch |
| Chống gian lận | Xem mục [Chống gian lận](#chống-gian-lận) |
| Tích hợp | Vault, PlaceholderAPI, Guild (qua PAPI hoặc API), BoxMine / chợ đấu giá / Guild bất kỳ (qua bridge cấu hình hoặc API) |

## Yêu cầu

- Paper **1.21.4** (hoặc Paper-fork tương thích), **Java 21**
- Tùy chọn: **Vault** + plugin kinh tế (thưởng tiền), **PlaceholderAPI** (placeholder, nhận diện Guild)
- Driver SQLite/MySQL đã được Paper đóng gói sẵn, không cần cài thêm.

## Biên dịch

```bash
mvn clean package
# -> target/AstraxQuest-1.0.0.jar
```

Hoặc đẩy mã lên GitHub: workflow `.github/workflows/build.yml` tự build và đính kèm file JAR ở tab *Actions → Artifacts*.

## Cài đặt nhanh

Xem chi tiết ở [docs/INSTALL.md](docs/INSTALL.md).

1. Chép `AstraxQuest-1.0.0.jar` vào `plugins/`.
2. (Khuyên dùng) cài Vault + plugin kinh tế, PlaceholderAPI.
3. Khởi động server → plugin tự tạo thư mục `plugins/AstraxQuest/` với các file mẫu.
4. Chỉnh `config.yml`, `quests/*.yml`... rồi `/quest admin reload`.

## Thêm nhiệm vụ / sửa phần thưởng trong 30 giây

Mặc định plugin bật 3 mục đơn giản: **Nhiệm vụ ngày** (đào block, di chuyển, diệt quái, hạ gục 3 người), **Nhiệm vụ tuần**, **PvP**. Không còn chọn ngẫu nhiên: nhiệm vụ nào trong file thì hiện nhiệm vụ đó.
Cốt truyện, Guild, Sự kiện vẫn có sẵn nhưng **tắt** (đổi `enabled: false` → `true` ở đầu file trong `quests/` để bật).

1. Mở `plugins/AstraxQuest/quests/daily.yml` (hoặc `weekly.yml`, `pvp.yml`).
2. **Sửa thưởng:** đổi dòng `rewards:` của nhiệm vụ đó, ví dụ `rewards: { money: 1500, tokens: 3, exp: 50, items: ["GOLDEN_APPLE:2"] }`.
3. **Thêm nhiệm vụ:** copy nguyên một khối, đổi tên id (không dấu, không khoảng trắng), `name`, `type`, `amount`:
   ```yaml
     kill_5_nguoi:
       name: "&cHạ gục 5 người"
       description: ["Giết 5 người chơi."]
       icon: DIAMOND_SWORD
       type: KILL_PLAYER
       amount: 5
       rewards: { money: 2500, tokens: 4, exp: 80 }
   ```
4. Trong game gõ `/quest admin reload` rồi mở `/quest` để xem.

Nếu nhiệm vụ cấu hình sai, console sẽ báo `[daily.yml:id] ...` và bỏ qua riêng nhiệm vụ đó.

## Cấu trúc file cấu hình

```
plugins/AstraxQuest/
├── config.yml          # database, mui gio, gio reset, cap do, chong gian lan, tich hop
├── messages.yml        # toan bo thong diep
├── gui.yml             # tieu de, vi tri o, icon, dong chu trong GUI
├── titles.yml          # danh hieu
├── events.yml          # su kien theo thoi gian (start/end)
├── integrations.yml    # bridge su kien plugin ngoai (BoxMine, cho dau gia, guild)
├── quests/
│   ├── daily.yml  weekly.yml  story.yml  guild.yml  pvp.yml  event.yml
└── data.db             # SQLite (neu dung SQLite)
```

### Thời gian làm mới (`config.yml`)

```yaml
timezone: "Asia/Ho_Chi_Minh"
reset:
  daily-hour: 0        # reset moi ngay luc 00:00
  weekly-day: MONDAY   # tuan bat dau Thu Hai
  weekly-hour: 0
  monthly-day: 1
```

Mỗi nhiệm vụ có `reset: DAILY | WEEKLY | MONTHLY | NEVER | EVENT` (mặc định theo file). Tiến độ của kỳ cũ được giữ trong DB, kỳ mới bắt đầu từ 0.

### Viết một nhiệm vụ

```yaml
quests:
  mine_diamond:
    name: "&b&lKim cương!"
    description: ["Đào quặng kim cương."]
    icon: DIAMOND
    group: hard                 # nhom de chon ngau nhien (xem 'pick')
    type: BREAK_BLOCK
    targets: [DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE]   # *_ORE = ky tu thay the; bo trong = bat ky
    amount: 8
    min-level: 0                # cap Quest toi thieu
    permission: ""              # quyen can co (tuy chon)
    worlds: []                  # gioi han the gioi (tuy chon)
    requires: [id_khac]         # phai NHAN THUONG nhiem vu nay truoc (chuoi cot truyen)
    auto-claim: false           # true: tu dong nhan khi xong
    rewards:
      money: 1500
      tokens: 4
      exp: 80
      title: anh_hung           # id trong titles.yml
      items:
        - "BREAD:8"             # rut gon
        - { material: DIAMOND, amount: 2, name: "&bKim cương thưởng", lore: ["..."], glow: true }
      commands: ["give {player} experience_bottle 16"]   # {player} {uuid} {quest}
```

**Chọn ngẫu nhiên theo kỳ** (`daily.yml`, `weekly.yml`): mục `settings.pick` cho biết mỗi kỳ lấy bao nhiêu nhiệm vụ từ mỗi `group`.
Kết quả ổn định trong kỳ (cùng seed) và giống nhau cho mọi người; đặt `per-player: true` để mỗi người một bộ. Xóa `pick` để hiện tất cả.

**Cốt truyện**: dùng `requires` để xếp chuỗi, `chapter` để gom chương, `hidden: true` để ẩn đến khi mở khóa.

**Guild**: tiến độ **dùng chung** cho cả guild; mỗi thành viên tự nhận thưởng 1 lần mỗi kỳ.

**Sự kiện**: khai báo thời gian trong `events.yml`; nhiệm vụ trong `quests/event.yml` trỏ tới sự kiện qua `event:`.
Chỉ ghi tiến độ và hiển thị trong thời gian sự kiện. Thêm sự kiện mới = thêm mục vào `events.yml` + nhiệm vụ tương ứng.

### Bảng loại mục tiêu

| `type` | Nguồn | `targets` |
|---|---|---|
| `BREAK_BLOCK` | đào khối (không tính khối người chơi tự đặt) | tên Material |
| `PLACE_BLOCK` | đặt khối | tên Material |
| `HARVEST` | phá cây trồng **đã chín** | WHEAT, CARROTS, ... |
| `KILL_MOB` | giết quái (không tính từ spawner/trứng) | tên EntityType |
| `KILL_PLAYER` | giết người chơi (có chống farm) | bỏ trống |
| `FISH` | câu được cá/đồ | tên Material vật phẩm câu được |
| `CRAFT` / `SMELT` | chế tạo / lấy đồ nung ra khỏi lò | Material kết quả |
| `BREED` | nhân giống | EntityType |
| `ENCHANT` | phù phép | Material vật phẩm |
| `CONSUME` | ăn/uống | Material |
| `WALK` | đi bộ (block, không phương tiện/AFK/teleport) | bỏ trống |
| `PLAYTIME` | phút chơi **không AFK** | bỏ trống |
| `OPEN_BOXMINE`, `AUCTION_SELL`, `AUCTION_BUY`, `GUILD_DONATE`, `CUSTOM` | plugin ngoài qua bridge/API | tùy plugin |

## Lệnh & quyền

| Lệnh | Mô tả |
|---|---|
| `/quest` (`/aq`, `/nhiemvu`, `/nv`) | Mở menu chính |
| `/quest <daily\|weekly\|story\|guild\|pvp\|event>` | Mở thẳng một mục |
| `/quest claim <id>` / `claimall [mục]` | Nhận thưởng |
| `/quest profile`, `tokens`, `top` | Hồ sơ, số token, bảng xếp hạng cấp |
| `/quest title [id\|off]` | Chọn danh hiệu |
| `/quest admin reload` | Nạp lại cấu hình |
| `/quest admin events` | Trạng thái sự kiện |
| `/quest admin reset <người> [all\|mục\|id]` | Đặt lại tiến độ |
| `/quest admin addprogress <người> <id> <số>` / `complete <người> <id>` | Cộng tiến độ / hoàn thành |
| `/quest admin trigger <người> <TYPE> <target> <số>` | Mô phỏng sự kiện ngoài (kiểm thử tích hợp) |
| `/quest admin token <give\|take> <người> <số>` / `exp <give\|take> ...` | Quản lý token, EXP |
| `/quest admin title <give\|take> <người> <id>` | Quản lý danh hiệu |
| `/quest admin audit <người> [số dòng]` | Nhật ký giao dịch |

| Quyền | Mặc định |
|---|---|
| `astraxquest.use` | mọi người |
| `astraxquest.top` | mọi người |
| `astraxquest.admin` | OP |

## Chống nhận thưởng trùng & nhật ký

- Nhận thưởng chạy trong **một giao dịch DB nguyên tử**: kiểm tra tiến độ ≥ yêu cầu → `INSERT` vào `aq_claims` có **khóa chính (uuid, quest, kỳ)** → cộng token/EXP/cấp → mở danh hiệu → ghi log. Hai lần bấm cùng lúc, hai server cùng MySQL, hay lag đều không thể nhận trùng.
- Ba lớp chặn: khóa `inflight` trên luồng chính → bộ nhớ đệm "đã nhận" → ràng buộc DB.
- Server **luôn tự xác nhận tiến độ trong DB**; nếu cấu hình `amount` được tăng, nhận thưởng sẽ kiểm tra lại theo giá trị mới.
- Bảng `aq_transactions` ghi mọi giao dịch (CLAIM, TOKEN_*, EXP_*, TITLE_*, ADMIN_*) với trạng thái `PENDING → DELIVERED / PARTIAL`. Xem bằng `/quest admin audit`.
- Vật phẩm không nhận được (túi đầy với `full-inventory: PENDING`, hoặc người chơi thoát giữa chừng) được lưu ở `aq_pending` và giao khi vào lại, mỗi dòng chỉ giao một lần.

## Chống gian lận

| Kiểu gian lận | Biện pháp |
|---|---|
| Đặt rồi phá khối lặp lại | Ghi nhớ khối do người chơi đặt (giới hạn kích thước + hạn dùng), không tính đào; đặt lại cùng chỗ quá nhanh không tính `PLACE_BLOCK` |
| Cây trồng chưa chín | Chỉ `HARVEST` khi đã chín hoàn toàn |
| Farm quái bằng spawner/trứng | Gắn cờ quái sinh từ nguồn trong `ignored-spawn-reasons` |
| Farm PvP | Hồi giữa 2 lần giết cùng một người, tối đa N lần/giờ, chặn cùng IP, nạn nhân phải chơi đủ phút |
| Treo máy | Không tính đi bộ/thời gian chơi khi đứng yên & không xoay camera quá `afk-seconds` |
| Đi bộ giả | Bỏ qua teleport, bay, lướt elytra, phương tiện, bước nhảy bất thường |
| Creative/Spectator | Không tính tiến độ (cấu hình được), có thể tắt theo thế giới |
| Cộng tiến độ ngoài ý muốn | Tiến độ chỉ do server tính, giới hạn ở `amount`, ghi theo kiểu cộng dồn |

## Tích hợp

### Kinh tế (Vault)
Tự dùng Vault nếu có. Không có Vault, đặt `integrations.economy.fallback-command` (ví dụ `eco give {player} {amount}`).

### PlaceholderAPI
`%astraxquest_level%`, `_exp`, `_exp_next`, `_exp_total`, `_tokens`, `_title` (có màu), `_title_plain`, `_title_id`, `_claimable`, `_claimed_total`,
`%astraxquest_{daily|weekly|story|guild|pvp|event}_{done|total|claimable|reset}%`, `%astraxquest_progress_<id>%`, `%astraxquest_percent_<id>%`.

Dùng `%astraxquest_title%` trong plugin chat/tab để hiện danh hiệu.

### Guild
- **Cách 1 (không cần code):** plugin Guild có expansion PlaceholderAPI → đặt `integrations.guild.placeholder` (mặc định `%guild_name%`).
- **Cách 2 (API):** `AstraxQuestAPI.setGuildProvider(player -> tenGuildHoacNull)`.

### BoxMine / chợ đấu giá / đóng góp Guild
Ba cách, chọn một:
1. **Bridge cấu hình** (`integrations.yml`): khai báo class Event của plugin đó và tên getter lấy người chơi/mục tiêu/số lượng; AstraxQuest đăng ký lắng nghe bằng reflection.
   Các class trong file mẫu chỉ là **ví dụ**: hãy thay bằng class thực tế của plugin bạn dùng.
2. **API** (plugin kia hoặc plugin cầu nối gọi): `AstraxQuestAPI.addProgress(player, QuestType.OPEN_BOXMINE, "ten_hop", 1);`
3. **Lệnh** (từ console/command block/plugin kia): `/quest admin trigger <người> OPEN_BOXMINE <hộp> 1`.

Thưởng BoxMine: đặt `commands: ["boxmine give {player} <loại> 1"]` theo lệnh của plugin đó.

### API cho lập trình viên
```java
AstraxQuestAPI.addProgress(Player, QuestType, String target, long amount);
AstraxQuestAPI.addTokens(UUID, name, delta, reason);     // CompletableFuture<Long>
AstraxQuestAPI.takeTokens(UUID, name, amount, reason);   // tra ve -1 neu khong du
AstraxQuestAPI.getTokens(UUID); AstraxQuestAPI.getLevel(UUID);
AstraxQuestAPI.setGuildProvider(Function<Player,String>);
// Events: QuestCompleteEvent, QuestClaimEvent
```

## Database

- `SQLITE` (mặc định): file `plugins/AstraxQuest/data.db`, WAL, một luồng ghi tuần tự.
- `MYSQL`: điền `database.mysql.*`. Tiến độ được **cộng dồn** (không ghi đè) nên nhiều server dùng chung DB vẫn đúng.
- Bảng: `aq_players`, `aq_progress`, `aq_claims`, `aq_titles`, `aq_pending`, `aq_transactions`. Bảng tạo tự động.
- Mọi truy vấn chạy bất đồng bộ, không chặn luồng chính; tiến độ gom lô theo `progress.flush-interval-seconds`, hoàn thành được ghi ngay.

## Kiểm thử

`mvn test` chạy các test đơn vị cho phần lõi: tính kỳ reset, cấp độ, so khớp mục tiêu, chọn ngẫu nhiên ổn định, chống gian lận (khối đặt, PvP).
Kiểm thử thủ công khuyến nghị trên server thử: xem danh sách ở [docs/INSTALL.md](docs/INSTALL.md#kiểm-tra-sau-khi-cài).

## Giới hạn đã biết

- Danh sách khối đã đặt nằm trong bộ nhớ, **mất khi restart** server.
- Nhiệm vụ Guild không ghi riêng đóng góp từng thành viên (ai cũng nhận thưởng khi guild đạt mục tiêu).
- Tiến độ Guild giữa nhiều server MySQL có thể trễ tối đa một chu kỳ ghi (30 giây mặc định).
- Chế tạo bằng Shift-click được ước lượng theo nguyên liệu/ô trống; hãy tránh đặt mục tiêu `CRAFT` cho vật phẩm có thể chế rồi tháo ngược liên tục (khối khoáng ↔ thỏi).
- Nếu việc giao thưởng (tiền/lệnh) lỗi **sau khi** DB đã ghi nhận, giao dịch được đánh dấu `PARTIAL` trong log để admin xử lý (`/quest admin audit`).
- Bridge cần tên class Event thật của plugin ngoài; plugin không có Event thì dùng API/lệnh.
