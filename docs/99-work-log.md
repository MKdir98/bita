# Work Log

گزارش کارهای انجام شده توسط AI Assistant

---

## 2026-02-04

### 14:30 - Maven Build Test
- **عملیات**: اجرای `mvn clean install`
- **وضعیت**: ✅ موفق

### 15:00 - Development Scripts
- **عملیات**: ایجاد اسکریپت‌های راه‌اندازی محلی
- **فایل‌های ایجاد شده**:
  - `start-dev.sh` - راه‌اندازی کامل محیط توسعه
  - `stop-dev.sh` - توقف تمام سرویس‌ها
- **قابلیت‌ها**:
  - بررسی پیش‌نیازها (Docker, Java, Maven, Node)
  - راه‌اندازی Docker infrastructure (PostgreSQL, Redis, Kafka, Elasticsearch)
  - Build پروژه Maven
  - راه‌اندازی Backend روی پورت 8081
  - راه‌اندازی Frontend روی پورت 5173

### 15:30 - Environment Setup Fixes
- **مشکلات برطرف شده**:
  - پورت PostgreSQL از 5432 به 5433 تغییر کرد (تداخل با postgres موجود)
  - Docker images به‌روزرسانی شدند برای استفاده از images محلی
  - spring-boot-maven-plugin اصلاح شد برای ساخت executable JAR
- **وضعیت نهایی**: ✅ محیط توسعه با موفقیت راه‌اندازی شد
  - Backend: http://localhost:8081 (Health: UP)
  - Frontend: http://localhost:5173

---

## 2026-02-05

### 16:00 - Frontend Routing Fix
- **مشکل**: کلیک روی سرویس‌ها، مسیرها و چت در داشبورد کار نمی‌کرد
- **علت**: Route های مربوطه در `App.tsx` تعریف نشده بودند
- **راه‌حل**: اضافه کردن route definitions برای:
  - `/services/*` - لیست، جزئیات، و فرم سرویس‌ها
  - `/routes/*` - لیست، جزئیات، و فرم مسیرها
  - `/chat` - صفحه چت با LLM
  - `/templates/*` - قالب‌های route، component، endpoint
  - `/users/*` - مدیریت کاربران
- **فایل تغییر یافته**: `bita-esm-frontend/src/App.tsx`
- **وضعیت**: ✅ تکمیل شد

### 16:15 - G4F Provider Integration
- **عملیات**: اضافه کردن GPT4Free (g4f) به عنوان provider جدید LLM
- **فایل‌های ایجاد/تغییر یافته**:
  - `bita-esm-backend/src/main/java/ir/bita/esm/llm/provider/G4fProvider.java` (جدید)
  - `application.yml` - اضافه کردن تنظیمات g4f
  - `application-local.yml` - فعال‌سازی g4f برای محیط local
- **مدل‌های پشتیبانی شده**:
  - gpt-4, gpt-4o, gpt-4o-mini, gpt-4-turbo, gpt-3.5-turbo
  - claude-3-opus, claude-3-sonnet, claude-3-haiku
  - gemini-pro, gemini-1.5-pro
  - deepseek-v3, deepseek-chat
  - llama-3.1-70b, llama-3.1-8b, mixtral-8x7b
- **نحوه استفاده**:
  ```bash
  pip install -U g4f[all]
  python -m g4f --port 1337 --debug
  ```
- **وضعیت**: ✅ تکمیل شد

### 17:00 - UI Fixes and Pollinations Integration
- **مشکلات رفع شده**:
  1. **صفحه تنظیمات**: صفحه Settings ایجاد شد (`/settings`)
  2. **شناسه ملی اختیاری**: اعتبارسنجی nationalId در فرم سازمان به اختیاری تغییر کرد
  3. **دکمه ایجاد برای Viewer**: دکمه‌های ایجاد/ویرایش/حذف برای Viewer مخفی شد
  4. **نمایش صفحه کاربران**: مقایسه نقش‌ها به case-insensitive تغییر کرد
  5. **Pollinations Provider**: جایگزین g4f برای دسترسی آنلاین به LLM
  6. **System Prompt**: پرامپت سیستم چت بهبود یافت با راهنمای ابزارها
- **فایل‌های تغییر یافته**:
  - `SettingsPage.tsx` (جدید)
  - `App.tsx` - اضافه کردن route تنظیمات
  - `ClientFormPage.tsx` - nationalId اختیاری
  - `ClientListPage.tsx` - مخفی کردن دکمه‌ها برای Viewer
  - `authStore.ts` - مقایسه نقش case-insensitive
  - `Sidebar.tsx` - لاگ دیباگ برای نقش‌ها
  - `PollinationsProvider.java` (جدید)
  - `ChatService.java` - بهبود system prompt
  - `application.yml` و `application-local.yml` - تنظیمات Pollinations
- **Pollinations AI**:
  - URL: `https://gen.pollinations.ai/v1/chat/completions`
  - بدون نیاز به نصب یا راه‌اندازی سرور
  - مدل‌های پشتیبانی: openai, claude, gemini, deepseek, llama, mistral
- **وضعیت**: ✅ تکمیل شد

