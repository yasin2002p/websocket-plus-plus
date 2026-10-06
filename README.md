# WebSocket Logger++ (Burp Suite Extension)

یک اکستنشن قدرتمند برای **Burp Suite** (بر پایه **Montoya API**) الهام‌گرفته از اکستنشن مشهور **Logger++**، اما به صورت اختصاصی برای **ترافیک‌های وب‌سوکت (WebSocket)**.

این اکستنشن به شما امکان می‌دهد تمام پیام‌های وب‌سوکت رد و بدل شده (Client ➔ Server و Server ➔ Client) را به شکل زنده لاگ کرده و با **موتور کوئری پیشرفته** شبیه به Logger++ روی آن‌ها فیلتر و جستجو انجام دهید.

---

## قابلیت‌های کلیدی (Features)

1. **لاگ کامل فریم‌ها و پیام‌های وب‌سوکت (Live WebSocket Logging):**
   - شنود زنده پیام‌های Text و Binary در تمام ابزارهای Burp (شامل Proxy، Repeater و ...).
   - بارگذاری خودکار تاریخچه وب‌سوکت‌های قبلی موجود در Proxy هنگام لود شدن اکستنشن.
   - نمایش جهت پیام با نشانگرهای رنگی (`⬆ Outgoing / Client -> Server` و `⬇ Incoming / Server -> Client`).
   - ثبت متادیتای دقیق: شناسه فریم (#)، زمان (HH:mm:ss.SSS)، Connection ID، ابزار مبدا (Tool)، هاست، پورت، مسیر (Path)، نوع (Type)، طول بر حسب بایت (Length) و پیش‌نمایش (Preview).

2. **موتور کوئری و فیلتر پیشرفته (Logger++ Query Engine):**
   - پشتیبانی از فیلدهای کلیدی: `payload`, `dir`, `host`, `path`, `url`, `length`, `type`, `tool`, `id`, `comment`, `color`
   - عملگرهای مقایسه‌ای: `==`, `!=`, `contains`, `!contains`, `matches` (Regex), `startswith`, `endswith`, `>`, `<`, `>=`, `<=`
   - عملگرهای منطقی: `AND` (`&&`), `OR` (`||`), `NOT` (`!`) و پرانتزگذاری `( ... )`
   - جستجوی آزاد متنی (Free-text search): اگر فقط یک کلمه تایپ کنید، در تمام فیلدها جستجو می‌شود.
   - اعتبارسنجی زنده (Live Syntax Validation) روی نوار کوئری با رنگ سبز/قرمز.

3. **فیلترهای سریع (Quick Filters):**
   - چک‌باکس ترافیک خروجی (`Outgoing / Client`)
   - چک‌باکس ترافیک ورودی (`Incoming / Server`)
   - گزینه **Hide Heartbeats** (حذف خودکار فریم‌های پینگ/پانگ و هارت‌بیت‌ها مثل `2`, `3`, `ping`, `pong`, `{"type":"ping"}`)
   - گزینه **In Scope Only** (نمایش فقط اهداف داخل Scope برپ)

4. **ویرایشگر و نمایشگر نیتیو برپ (Burp Native Inspectors):**
   - نمایش پیام وب‌سوکت با ادیتور نیتیو Burp (`WebSocketMessageEditor` با تب‌های Raw, Hex, Inspector)
   - نمایش ریکوئست ارتقا و دست‌دهی اولیه‌ی HTTP (`Handshake Upgrade Request`) برای بررسی هدرها، کوکی‌ها و توکن‌های اتصال
   - تب متادیتای ساختاریافته (Message Details)

5. **امکانات مدیریتی و خروجی:**
   - هایلایت کردن ردیف‌ها با رنگ‌های استاندارد برپ (Red, Orange, Yellow, Green, Cyan, Blue, Pink, Magenta, Gray)
   - یادداشت‌گذاری و ویرایش کامنت (Comment) برای هر پیام
   - توقف موقت لاگ‌گیری (Pause / Resume)
   - اسکرول خودکار (Auto Scroll)
   - شمارنده زنده آمار: Total, Shown, Outgoing, Incoming
   - خروجی گرفتن در قالب‌های استاندارد **CSV** و **JSON**
   - کلیک‌راست برای کپی کردن Payload، URL و Handshake Request

---

## راهنمای ساختار کوئری (Query Syntax Reference)

### ۱. فیلدهای قابل جستجو:
| فیلد | نام‌های مستعار | توضیحات |
|---|---|---|
| `payload` | `body`, `data`, `p` | متن پیام وب‌سوکت |
| `dir` | `direction`, `d` | جهت پیام (`client`, `server`, `outgoing`, `incoming`) |
| `host` | `h` | هاست هدف |
| `path` | - | مسیر وب‌سوکت (مثلاً `/socket.io/` یا `/ws/chat`) |
| `url` | - | آدرس کامل وب‌سوکت |
| `length` | `len`, `size` | طول پیام بر حسب بایت |
| `type` | - | نوع پیام (`Text` یا `Binary`) |
| `tool` | - | ابزار فرستنده در برپ (`Proxy`, `Repeater`) |
| `id` | - | شماره ترتیبی پیام |
| `comment` | - | کامنت ثبت شده توسط کاربر |

### ۲. عملگرها:
- `==` یا `=` : برابری دقیق (Case-Insensitive)
- `!=` : نامساوی
- `contains` : شامل بودن زیررشته
- `!contains` : شامل نبودن
- `matches` یا `regex` : تطابق بر اساس Regex
- `startswith` / `endswith` : شروع یا پایان با عبارت مشخص
- `>`, `<`, `>=`, `<=` : مقایسه‌های عددی (برای `length` و `id`)
- `AND` (`&&`), `OR` (`||`), `NOT` (`!`) : ترکیب عبارات شرطی
- `( ... )` : اولویت‌بندی شرط‌ها با پرانتز

### ۳. مثال‌های کاربردی:
```text
payload contains "token"
dir == client and length > 50
host contains "api" and path == "/ws"
payload matches '.*"action":\s*"login".*'
(dir == server or len > 200) and not payload contains "ping"
payload !contains "heartbeat" and dir == outgoing
admin
```

> 📖 **راهنمای کامل با سناریوهای پیشرفته و Regex:**  
> برای مشاهده تمام فیلدها، عملگرها و مثال‌های تست نفوذ و دیباگینگ، مستند **[راهنمای جامع فیلترنویسی (QUERY_GUIDE.md)](QUERY_GUIDE.md)** را مطالعه کنید.

---

## نحوه نصب در Burp Suite

فایل از پیش کامپایل شده در مسیر پروژه آماده است:
`WebSocketLogger-1.0.0.jar`

مراحل لود کردن در برپ:
1. برنامه **Burp Suite** را باز کنید.
2. به تب **Extensions** بروید.
3. در زیرتب **Installed**، روی دکمه **Add** کلیک کنید.
4. در پنجره باز شده:
   - گزینه **Extension type** را روی **Java** بگذارید.
   - در قسمت **Extension file (.jar)**، فایل [WebSocketLogger-1.0.0.jar](file:///c:/Users/stockland/Desktop/Web-socket%20logger/WebSocketLogger-1.0.0.jar) را انتخاب کنید.
5. روی **Next** کلیک کنید.
6. تب جدیدی با نام **WebSocket Logger++** به منوی اصلی Burp اضافه می‌شود و تمام ترافیک‌های وب‌سوکت را لاگ می‌کند.

---

## نحوه کامپایل مجدد (Build from Source)

اگر تغییری در کدهای جاوا دادید، به یکی از روش‌های زیر می‌توانید پروژه را مجدداً کامپایل و بسته بندی کنید:

### روش ۱: با استفاده از اسکریپت آماده PowerShell (سریع‌ترین حالت):
```powershell
.\build.ps1
```

### روش ۲: با استفاده از Gradle:
```powershell
gradle build
```

### روش ۳: با استفاده از Maven:
```powershell
mvn clean package
```
