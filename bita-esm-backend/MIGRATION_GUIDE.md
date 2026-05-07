# راهنمای Migration دیتابیس

## تغییرات انجام شده

### 1. فعال‌سازی Flyway
Flyway برای مدیریت migration‌های دیتابیس فعال شده است.

### 2. تغییر `ddl-auto` از `update` به `validate`
- قبلاً: `spring.jpa.hibernate.ddl-auto: update`
- حالا: `spring.jpa.hibernate.ddl-auto: validate`

این تغییر باعث می‌شود که Hibernate فقط schema را validate کند و تغییری در آن ایجاد نکند. تمام تغییرات schema باید از طریق Flyway migration انجام شود.

### 3. اصلاح constraint جدول `user_otp`
Migration فایل `V1__fix_user_otp_constraint.sql` ایجاد شده که:
- تمام رکوردهای موجود در جدول `user_otp` را پاک می‌کند (چون موقتی هستند و منقضی می‌شوند)
- Constraint قدیمی `user_otp_otp_type_check` را حذف می‌کند
- Constraint جدید را با مقادیر `REGISTER` و `PASSWORD_RESET` اضافه می‌کند

## نحوه اجرا

### گام 0: پاک کردن migration ناموفق (اگر قبلاً اجرا کرده‌اید)
اگر قبلاً برنامه را restart کرده‌اید و خطا گرفته‌اید، ابتدا این دستور را اجرا کنید:

```bash
# روش 1: استفاده از اسکریپت
./fix_and_restart.sh

# روش 2: دستی
PGPASSWORD=bita123 psql -h localhost -p 5433 -U bita -d bita_db -c "DELETE FROM flyway_schema_history WHERE version = '1';"
```

### گام 1: Restart برنامه
حالا برنامه را restart کنید. Flyway به صورت خودکار:
1. جدول `flyway_schema_history` را ایجاد می‌کند (اگر وجود نداشته باشد)
2. Migration‌های اجرا نشده را شناسایی می‌کند
3. آنها را به ترتیب اجرا می‌کند

```bash
# اگر از Maven استفاده می‌کنید:
mvn spring-boot:run

# یا اگر JAR file دارید:
java -jar target/bita-esm-backend-1.0.0-SNAPSHOT.jar
```

### گام 2: بررسی لاگ‌ها
در لاگ‌های برنامه باید چیزی شبیه این ببینید:

```
INFO  o.f.c.i.database.base.BaseDatabaseType : Database: jdbc:postgresql://localhost:5432/bita_db (PostgreSQL 15.x)
INFO  o.f.core.internal.command.DbValidate   : Successfully validated 1 migration (execution time 00:00.012s)
INFO  o.f.c.i.s.JdbcTableSchemaHistory       : Creating Schema History table "public"."flyway_schema_history" ...
INFO  o.f.core.internal.command.DbMigrate    : Current version of schema "public": << Empty Schema >>
INFO  o.f.core.internal.command.DbMigrate    : Migrating schema "public" to version "1 - fix user otp constraint"
INFO  o.f.core.internal.command.DbMigrate    : Successfully applied 1 migration to schema "public" (execution time 00:00.045s)
```

### گام 3: تست
بعد از restart، باید بتوانید از قابلیت "فراموشی رمز عبور" استفاده کنید بدون اینکه خطای constraint بگیرید.

## نکات مهم

### برای محیط Development
اگر می‌خواهید در محیط development از `ddl-auto: update` استفاده کنید، می‌توانید در `application-local.yml` این تنظیم را override کنید:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
  flyway:
    enabled: false
```

### برای محیط Production
در production حتماً باید:
- `ddl-auto: validate` باشد
- Flyway فعال باشد
- قبل از deploy، migration‌ها را بررسی کنید

## Migration‌های آینده

برای ایجاد migration جدید:

1. فایل SQL جدید در `src/main/resources/db/migration/` بسازید
2. نام فایل باید به این فرمت باشد: `V{version}__{description}.sql`
   - مثال: `V2__add_user_profile_table.sql`
3. Migration به صورت خودکار در restart بعدی اجرا می‌شود

## عیب‌یابی

### اگر خطای "Migration checksum mismatch" گرفتید:
```sql
-- در دیتابیس اجرا کنید:
DELETE FROM flyway_schema_history WHERE version = '1';
```
سپس برنامه را restart کنید.

### اگر خواستید از اول شروع کنید:
```sql
-- تمام جداول را حذف کنید (فقط در development!)
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```
