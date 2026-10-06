# راهنمای جامع فیلترنویسی و کوئری‌ها (WebSocket Logger++ Query Guide)

اکستنشن **WebSocket Logger++** دارای یک موتور تجزیه و تحلیل کوئری (Query Engine) مستقل و پیشرفته است که امکان فیلتر کردن دقیق ترافیک وب‌سوکت را درست مانند اکستنشن محبوب *Logger++* برای وب‌سوکت فراهم می‌کند.

این سند، مرجع کامل قوانین نحوه نگارش، فیلدها، عملگرها و سناریوهای کاربردی در تست نفوذ و تحلیل وب‌سوکت است.

---

## فهرست مطالب
1. [مبانی و نحوه نگارش (Syntax Basics)](#۱-مبانی-و-نحوه-نگارش)
2. [جدول فیلدهای قابل استفاده (Available Fields)](#۲-جدول-فیلدها)
3. [عملگرهای مقایسه‌ای (Comparison Operators)](#۳-عملگرهای-مقایسه‌ای)
4. [عملگرهای منطقی و پرانتزگذاری (Logical Operators)](#۴-عملگرهای-منطقی-و-ترکیبی)
5. [جهت‌های ترافیک (Direction Aliases)](#۵-جهت‌های-ترافیک)
6. [سناریوهای پرتکرار و مثال‌های کاربردی (Real-world Scenarios)](#۶-سناریوهای-پرتکرار-و-مثال‌های-کاربردی)
7. [نکات کلیدی و عیب‌یابی (Tips & Troubleshooting)](#۷-نکات-کلیدی-و-عیب‌یابی)

---

## ۱. مبانی و نحوه نگارش

* **جستجوی آزاد (Free-text Search):**  
  اگر فقط یک عبارت ساده (بدون نام فیلد و عملگر) وارد کنید، کوئری به طور خودکار در تمام بخش‌های پیام (شامل متن Payload، هاست، مسیر، URL و کامنت‌ها) جستجو می‌کند:
  ```text
  admin
  ```
  یا برای عبارات چند کلمه‌ای:
  ```text
  "unauthorized user"
  ```

* **ساختار کوئری فیلدمحور:**  
  ```text
  <field> <operator> <value>
  ```
  مثال:
  ```text
  payload contains "token"
  ```

* **عدم حساسیت به حروف کوچک و بزرگ (Case-Insensitive):**  
  نام فیلدها، عملگرها و مقادیر متنی نسبت به بزرگی و کوچکی حروف حساس نیستند (`Payload CONTAINS "ADMIN"` دقیقاً معادل `payload contains "admin"` است).

* **کوتیشن‌ها:**  
  مقادیر رشته‌ای می‌توانند بین دابل‌کوتیشن `"` یا سینگل‌کوتیشن `'` قرار گیرند. اگر مقدار شامل کاراکترهای فاصله یا علائم نگارشی است، حتماً از کوتیشن استفاده کنید.

---

## ۲. جدول فیلدها

| فیلد اصلی | نام‌های مستعار (Aliases) | نوع داده | توضیحات |
|---|---|---|---|
| `payload` | `body`, `data`, `p` | متن (String) | محتوای متنی فریم وب‌سوکت |
| `dir` | `direction`, `d` | جهت (Direction) | جهت ارسال پیام (`client`, `server`, `to_server`, ...) |
| `host` | `h` | متن (String) | دامنه یا IP سرور مقصد |
| `path` | - | متن (String) | مسیر درخواست اتصال وب‌سوکت (مانند `/socket.io/` یا `/ws`) |
| `url` | - | متن (String) | آدرس کامل وب‌سوکت (شامل پروتکل و کوئری استرینگ) |
| `length` | `len`, `size` | عدد (Integer) | اندازه پیلود بر حسب بایت |
| `type` | - | متن (String) | نوع فریم (`Text` یا `Binary`) |
| `tool` | - | متن (String) | ابزار ارسال‌کننده در برپ (`Proxy`, `Repeater`, `Extensions`) |
| `id` | - | عدد (Integer) | شناسه منحصر‌به‌فرد فریم در لاگر |
| `port` | - | عدد (Integer) | پورت مقصد سرور (مانند `443` یا `80`) |
| `conn` | `connection` | عدد (Integer) | شناسه کانکشن وب‌سوکت |
| `comment`| - | متن (String) | متن یادداشت و کامنت نوشته‌شده برای سطر |

---

## ۳. عملگرهای مقایسه‌ای

### عملگرهای رشته‌ای (String Operators)
* `==` یا `=` : برابری دقیق
  ```text
  path == "/chat/room1"
  ```
* `!=` : نامساوی دقیق
  ```text
  type != "Binary"
  ```
* `contains` : شامل بودن متن (Substring match)
  ```text
  payload contains "Bearer"
  ```
* `!contains` : شامل نبودن متن
  ```text
  payload !contains "heartbeat"
  ```
* `startswith` : شروع شدن با مقدار مورد نظر
  ```text
  path startswith "/api/v2"
  ```
* `endswith` : خاتمه یافتن با مقدار مورد نظر
  ```text
  path endswith ".json"
  ```
* `matches` یا `regex` : تطابق بر اساس عبارت باقاعده (Regular Expression)
  ```text
  payload matches "user_id=[0-9]+"
  ```
* `!matches` یا `!regex` : عدم تطابق با Regex
  ```text
  payload !matches "^\{.*\}$"
  ```

### عملگرهای عددی (Numeric Operators)
برای فیلدهای `length`, `id`, `port`, `conn`:
* `>`, `<`, `>=`, `<=`, `==`, `!=`
  ```text
  len > 512
  length <= 64
  port == 8443
  id >= 100
  ```

---

## ۴. عملگرهای منطقی و ترکیبی

* **`AND` یا `&&` :** برقراری همزمان هر دو شرط
  ```text
  dir == client and length > 100
  ```
* **`OR` یا `||` :** برقراری حداقل یکی از شرط‌ها
  ```text
  dir == server or payload contains "error"
  ```
* **`NOT` یا `!` :** نقیض کردن شرط (معکوس‌سازی)
  ```text
  not (payload contains "ping")
  ```
* **پرانتز `( ... )` :** تعیین اولویت و گروه‌بندی شرط‌ها
  ```text
  (dir == client and len > 200) or (dir == server and payload contains "unauthorized")
  ```

---

## ۵. جهت‌های ترافیک (Direction Aliases)

برای فیلتر کردن جهت پیام با فیلد `dir` می‌توانید از تمام نام‌های استاندارد زیر استفاده کنید:

* **ترافیک کلاینت به سرور (Outgoing / To Server):**
  - `client`
  - `outgoing`
  - `out`
  - `c2s`
  - `"to server"`
  - `to_server`
  - `toserver`

* **ترافیک سرور به کلاینت (Incoming / To Client):**
  - `server`
  - `incoming`
  - `in`
  - `s2c`
  - `"to client"`
  - `to_client`
  - `toclient`

---

## ۶. سناریوهای پرتکرار و مثال‌های کاربردی

### ۱. حذف پینگ‌ها و هارت‌بیت‌های تکراری و نویز ترافیک
معمولاً کلاینت‌ها برای زنده نگه‌داشتن اتصال، پیلودهای خالی `{}` یا پینگ می‌فرستند و سرور آبجکت‌هایی با تایم متغیر برمی‌گرداند:

* **حذف `{}` از سمت کلاینت و حذف هارت‌بیت‌های سرور:**
  ```sql
  not (dir == client and payload == "{}") and not (dir == server and payload contains "arnstep")
  ```

* **حذف با Regex برای آبجکت‌هایی با فیلد time متغیر:**
  ```sql
  not (dir == client and payload == "{}") and not (payload matches '\{"id":0,"senderId":0.*"arnstep":0\}')
  ```

* **حذف پینگ/پانگ‌های عددی Socket.IO / Engine.io (مانند `2` و `3`):**
  ```sql
  payload != "2" and payload != "3"
  ```

---

### ۲. جستجو برای داده‌های حساس و توکن‌های احراز هویت
* **پیدا کردن توکن‌های Bearer یا JWT:**
  ```sql
  payload contains "Bearer" or payload matches "eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"
  ```

* **پیدا کردن پسورد یا کلیدهای امنیتی در کلاینت:**
  ```sql
  dir == client and (payload contains "password" or payload contains "apikey" or payload contains "secret")
  ```

---

### ۳. بررسی آسیب‌پذیری‌های وب‌سوکت (Web Pentesting)

* **یافتن پیام‌های خطا و استثناهای سرور (Information Disclosure):**
  ```sql
  dir == server and (payload contains "exception" or payload contains "traceback" or payload contains "syntax error")
  ```

* **شناسایی پاسخ‌های خطای احراز هویت یا دسترسی (401 / 403):**
  ```sql
  dir == server and (payload contains "unauthorized" or payload contains "forbidden" or payload contains "access denied")
  ```

* **جداسازی فقط پیام‌های ارسال شده از تب Repeater:**
  ```sql
  tool == "Repeater"
  ```

* **فیلتر کردن پیام‌های حجیم (احتمال وجود نشت داده یا فایل):**
  ```sql
  len > 2048 and dir == incoming
  ```

* **جستجوی پیام‌های باینری:**
  ```sql
  type == "Binary"
  ```

---

### ۴. تفکیک دامنه و مسیر (Multi-target Scope)
* **تمرکز روی یک هاست خاص به جز بقیه:**
  ```sql
  host == "chat.target.com" and path startswith "/ws"
  ```

* **مستثنی کردن دامنه‌های فرعی یا CDN:**
  ```sql
  host !contains "analytics" and host !contains "telemetry"
  ```

---

## ۷. نکات کلیدی و عیب‌یابی

1. **چراغ سبز و قرمز نوار کوئری (Live Validation):**
   - اگر کوئری تایپ‌شده دارای سینتکس صحیح باشد، عبارت `✓ Ready` یا `✓ Valid syntax` با رنگ سبز نمایش داده می‌شود.
   - اگر پرانتزی نبسته باشید یا عملگری ناقص باشد، بلافاصله ارور دقیق با رنگ قرمز نمایان می‌شود (مثلاً `✗ Missing value after contains`).

2. **کلیدهای میانبر:**
   - پس از نوشتن کوئری، فشردن کلید **Enter** بلافاصله کوئری را اعمال می‌کند.
   - دکمه **Clear** کوئری را خالی کرده و تمام ترافیک را نمایش می‌دهد.
   - دکمه **Syntax Help (?)** در داخل اکستنشن راهنمای سریع را به صورت پنجره Popup باز می‌کند.

3. **ترکیب با تیک‌های Quick Filter:**
   - تیک‌های بالای صفحه مانند **Outgoing (Client)**, **Incoming (Server)**, **Hide Heartbeats**, و **In Scope Only** به صورت خودکار با کوئری شما به صورت `AND` ترکیب می‌شوند.
   - اگر تیک *Hide Heartbeats* را فعال کنید، هارت‌بیت‌های استاندارد بدون نیاز به نوشتن کوئری پنهان خواهند شد.
