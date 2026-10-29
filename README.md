# SpendWise

**SpendWise** is a standalone personal-finance desktop application built from scratch in
**Java (Swing)** with **SQLite** persistence and **Maven** as the build system. It is a native
desktop program: no web frontend, no server process, no internet connection required at runtime.

The interface is fully in **Vietnamese** and uses **VND** currency formatting by default.

---

## Features

- **Tài khoản & đăng nhập** – đăng ký tài khoản cục bộ ở lần chạy đầu, đăng nhập bằng mật
  khẩu; mật khẩu chỉ được lưu dưới dạng băm **PBKDF2-HMAC-SHA256** (muối ngẫu nhiên 16 byte,
  120.000 vòng, so sánh constant-time) — không bao giờ lưu dạng plain text. Đăng xuất từ sidebar.
- **Dashboard** – period income/expense/summary cards, an all-time net balance, a 6-month
  comparison chart, expense distribution donut, budget usage overview and the latest
  transactions. Everything is recomputed from the database on every refresh.
- **Transactions** – add / edit / delete (with confirmation) income and expense records,
  keyword search across notes and category names, filters by type, category, date range and
  amount range, sorting by date / amount / category, live totals of the current filter, and
  CSV export of the current view. Ngày chọn bằng **lịch popup** (chọn ngày, đổi tháng,
  nút "Hôm nay"); thanh bộ lọc tự xếp dòng nên không bao giờ bị mất ô.
- **Categories** – create / edit / delete income and expense categories with color choice,
  per-category transaction counts and totals, a drill-down view of a category's transactions.
  Deleting a category that still has transactions requires moving them to another category;
  its budgets are removed in the same database transaction.
- **Ngân sách tháng** – lập kế hoạch theo tháng dựa mặc định trên **tổng thu nhập đã ghi nhận
  trong tháng đang lập**; thu nhập trung bình 6 tháng trước (chỉ tính các tháng có giao dịch
  thu — tháng trống không bị tính là 0) chỉ là gợi ý và có thể chép vào ô thu nhập. Có thể
  chỉnh thu nhập tính ngân sách thủ công. Chọn **tỷ lệ tiết kiệm** bằng thanh trượt
  (0–100%, mặc định 20%) → tiết kiệm mục tiêu = thu nhập tính ngân sách × tỷ lệ, ngân sách
  chi tổng = thu nhập tính ngân sách − tiết kiệm; mọi con số cập nhật **thời gian thực** khi kéo thanh trượt hoặc
  nhập. Phân bổ theo từng danh mục chi, hiển thị **Số tiền còn lại** ngay khi gõ, cảnh báo
  rõ khi tổng vượt giới hạn (chặn lưu kèm số tiền vượt, giữ nguyên dữ liệu đã nhập).
  Theo dõi chi tiêu thực tế theo ngân sách từng danh mục với thanh tiến trình và trạng thái
  **Bình thường / Sắp hết (≥80%) / Đã vượt (≥100%)** — kèm chữ mô tả và số tiền vượt chính xác.
  Mỗi tài khoản chỉ có một ngân sách/Tháng (lưu lại sẽ cập nhật, không tạo bản ghi trùng);
  dữ liệu lưu bền vững và tách biệt theo tài khoản.
- **Budgets (theo giai đoạn)** – limit per (expense) category over an inclusive date range,
  with real spending, remaining amount and status (normal / near limit ≥80% / exceeded).
  Overlapping budgets for the same category are rejected.
- **Reports** – four tabs from real records: overview (adaptive daily/monthly/yearly time
  series + range summary), category distribution (donut + table), monthly breakdown
  (income, expense, net, saving rate) and budget performance, each exportable to CSV, plus a
  transaction CSV export for the selected range.
- **Settings** – profile name, currency symbol, date format, default dashboard period and CSV
  delimiter — all persisted in SQLite. Storage info (path, size, record counts), open the data
  folder, and a double-confirmed "delete all data" action that keeps the preferences.
- **First run** – creates the database, seeds 12 sensible default categories and opens the
  register screen for the first local account. No fake login screen: authentication is real
  (PBKDF2-hashed credentials in the `users` table).

## Technology

| Component | Choice |
|-----------|--------|
| Language  | Java (compiled with `--release 21`) |
| GUI       | Java Swing + FlatLaf look-and-feel |
| Database  | SQLite via JDBC (`org.xerial:sqlite-jdbc`) |
| Build     | Maven (shade plugin → runnable fat JAR) |
| Tests     | JUnit 5 |

All money is stored as **integer VND units (`long`)** — no floating point rounding errors.
Dates are stored as `YYYY-MM-DD` text and timestamps as `YYYY-MM-DD HH:mm:ss`.

## Project structure

```
SpendWise-New/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/spendwise/
    │   ├── Main.java              # single entry point (login → main window)
    │   ├── app/                   # wiring: AppContext, DataChangeBus
    │   ├── model/                 # Transaction, Category, Budget, User, AppSettings, ...
    │   ├── repository/            # JDBC data access + DatabaseManager (schema v2 migrations)
    │   ├── service/               # business rules, validation, auth, reports
    │   ├── ui/                    # MainFrame, screens/, dialogs/, auth/, components/
    │   └── util/                  # MoneyUtil, DateUtil, PasswordHasher, CsvExporter, ...
    ├── main/resources/            # (empty; icons are drawn programmatically)
    └── test/java/com/spendwise/   # 44 JUnit tests against temporary SQLite databases
```

## Requirements

- **JDK 21** or newer (the build machine used JDK 26 with `--release 21`)
- **Maven 3.9+** (only needed to build; the produced JAR runs with a normal `java`)
- First Maven build downloads `sqlite-jdbc` and `flatlaf` from Maven Central; everything
  else afterwards works fully offline.

## Build, test, run

```bash
# run the automated test suite (51 tests, temporary databases only)
mvn clean test

# build the runnable fat JAR
mvn clean package

# run the application
java -jar target/spendwise.jar
```

**From an IDE (IntelliJ IDEA / VS Code):** open this folder as a Maven project and simply run
`com.spendwise.Main` — the database is created automatically and the main window opens; no
other class or process has to be started first.

**Custom database location:**

```bash
java -Dspendwise.db.path=C:\data\my-finance.db -jar target/spendwise.jar
```

Default location without the property: `%USERPROFILE%\SpendWise\spendwise.db`
(the directory and file are created on first launch; nothing is deleted or rebuilt afterwards).

## Data safety

- The schema is created once (versioned with `PRAGMA user_version`); updates are applied as
  stepwise migrations and existing data is never wiped on startup.
- `PRAGMA foreign_keys=ON` protects references; a category with transactions cannot be
  deleted until its records are reassigned to another category of the same type.
- Multi-step operations (category deletion with reassignment, full data wipe) run inside
  a single SQLite transaction with rollback on failure.
- Recommended backup: copy the `SpendWise` data folder while the app is closed
  (or export transactions to CSV first).

## CSV exports

- Menu on the *Giao dịch* screen exports the currently filtered list; the *Báo cáo* screen
  exports the transactions of the selected range plus the category, monthly and budget
  report tables.
- Files are **UTF-8 with BOM**, use the configured delimiter (`,` or `;`, set in Settings)
  and quote fields when required — Microsoft Excel opens Vietnamese text correctly.

## Verification results (actual runs)

- `mvn clean test` → **51/51 tests, 0 failures, 0 errors** (checks password hashing,
  register/login rules + per-user seeding, schema migration **v2→v3→v4** từ DB cũ thật
  (thêm owner, bảng monthly_budgets, rebuild UNIQUE danh mục theo owner, nhận dữ liệu mồ côi
  khi đăng nhập), transaction validation, filters/sorting, sums, monthly grouping, category
  rules (per-owner unique, safe delete), legacy budget overlaps/status, **ngân sách tháng:
  TB thu nhập bỏ qua tháng trống, công thức 20tr × 30% = 6tr/14tr, còn lại 9tr→7tr,
  chặn lưu khi vượt kèm số vượt chính xác, 80%/100%/106% + sửa/xoá giao dịch cập nhật ngay,
  tách biệt dữ liệu giữa hai tài khoản**, report mathematics, persistence across close/reopen,
  CSV escaping/BOM).
- `mvn clean package` → **BUILD SUCCESS**, `target/spendwise.jar` (~14.9 MB, shaded).
- Headless smoke run of the packaged JAR (`java -Djava.awt.headless=true -Dspendwise.db.path=…
  -jar target/spendwise.jar`) → `SPENDWISE_SMOKE_OK`, database auto-created with 12 default
  categories. Note: on a JDK newer than 21, sqlite-jdbc may print a harmless
  "restricted method" (JNI) warning; on Java 21 no warning appears.

## Known limitations

- Local multi-user accounts (register/login) — đăng nhập mỗi lần mở ứng dụng; nếu quên mật
  khẩu chỉ có thể tạo tài khoản mới (không có cơ chế khôi phục lấy lại mật khẩu cũ).
- One currency (VND) at a time; the symbol is a display preference, amounts are whole VND.
- Dashboard/chart sizes are tuned for desktop windows ≥1120×700 (the app enforces this minimum).
- CSV import is not implemented (export only); ngân sách tháng chưa có mẫu lặp lại tự động
  cho các tháng sau (mỗi tháng lập riêng, có thể sao chép nhanh bằng cách nhập lại).
- The window/taskbar icon comes from the app (drawn wallet mark); actual Windows taskbar
  rendering of custom icons was not asserted automatically.

---

*Built from scratch for this project — no code, schema or design was copied from any other
SpendWise implementation.*
