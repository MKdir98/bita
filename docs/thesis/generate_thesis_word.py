#!/usr/bin/env python3
"""
اسکریپت تولید فایل Word از فصل اول پایان‌نامه (خلاصهٔ هم‌خوان با متن کامل).

سند یکپارچهٔ فصول ۱–۳ و منابع: `docs/thesis/thesis-chapters-1-3.md`
(تولید با `python3 merge_thesis.py`؛ خروجی Word با pandoc: `thesis-chapters-1-3.docx`).

نیاز به نصب: pip install python-docx
"""

from pathlib import Path

try:
    from docx import Document
    from docx.shared import Pt, Inches
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.style import WD_STYLE_TYPE
except ImportError:
    print("لطفاً ابتدا python-docx را نصب کنید:")
    print("  pip install python-docx")
    exit(1)


# محتوای رسمی فصل اول (هم‌خوان با بخش فصل اول در thesis-chapters-1-3.md)
CONTENT = {
    "title": "فصل اول: مقدمه",
    "sections": [
        {
            "heading": "۱.۱ موضوع کار",
            "subheading": "دروازه سرویس سازمانی نسل بعدی و چرخهٔ یکپارچهٔ عمر سرویس",
            "paragraphs": [
                "سازمان‌ها و نهادهای بزرگ برای تبادل اطلاعات بین سیستم‌های پراکنده به زیرساخت‌های یکپارچه‌سازی سازمانی تکیه می‌کنند. دروازه سرویس سازمانی و گذرگاه سرویس سازمانی (ESB) هنوز در بسیاری از بافت‌های حاکمیتی و مالی به‌عنوان هستهٔ معماری سرویس‌گرا (SOA) ظاهر می‌شوند [۱].",
                "در کنار این بستر کلاسیک، موج ابری‌شدن و خردسرویس‌ها الزامات تازه‌ای ایجاد کرده است؛ از سوی دیگر، مدل‌های زبانی بزرگ (LLM) به ابزاری برای تفسیر زبان طبیعی و فراخوانی ابزارهای نرم‌افزاری تبدیل شده‌اند [۲،۳].",
                "موضوع این پایان‌نامه طراحی و پیاده‌سازی بستری است که بر پایهٔ ESM/ESB موجود توسعه می‌یابد و بر یکپارچه‌سازی چرخهٔ عمر سرویس سازمانی از تعریف و تأیید پیکربندی تا استقرار روی Kubernetes و همگرایی کنترل‌شدهٔ پیکربندی بین چند نمونهٔ ESB تمرکز دارد. رابط گفت‌وگو مبتنی بر LLM تنها یکی از لایه‌های کاهش اصطکاک در مرحلهٔ طراحی است.",
            ],
        },
        {
            "heading": "۱.۲ شرح مسئله",
            "subheading": "چالش‌های سامانه‌های سنتی ESM/ESB",
            "paragraphs": [
                "ادبیات نقشه‌برداری دربارهٔ کاربردهای ESB بر درک هم‌زمان چالش‌های فنی و غیرفنی برای معماری بهبودیافته تأکید دارد [۱]. در عمل، آداپترهای اختصاصی، هزینهٔ نگهداری و گاه نادیده گرفتن امنیت و مدیریت‌پذیری دیده می‌شود [۴].",
                "WS-Security ۱.۱ پیچیدگی رمزنگاری و مدیریت گواهی را به فرآیند ثبت سرویس اضافه می‌کند [۵،۶]. از منظر جریان کاری، قطع‌وبصل بودن مراحل تعریف، استقرار و sync دستی سه پیامد دارد: طولانی شدن زمان رسیدن به حالت پایدار، دشواری ردیابی نسخهٔ پیکربندی روی نودها، و افزایش ریسک ناهماهنگی محیط‌ها.",
            ],
        },
        {
            "heading": "۱.۲ شرح مسئله (ادامه)",
            "subheading": "اهمیت موضوع",
            "paragraphs": [
                "۱. کاهش زمان چرخهٔ سرویس با خودکارسازی مراحل میانی و مکانیزم تأیید انسانی.",
                "۲. حفظ سرمایهٔ SOAP/WS-Security و امکان هم‌زیستی با احراز هویت مدرن.",
                "۳. مقیاس‌پذیری عملیاتی با استقرار خودکار روی Kubernetes.",
                "۴. همگام‌سازی بدون توقف کامل با اعلان یا همترازی متناسب با بستر نصب.",
                "۵. تجربهٔ نقش‌های غیرتخصصی با رابط چت (تمرکز اولیه بر فارسی) در صورت تبدیل خروجی LLM به ساختارهای قابل اعتبارسنجی [۲].",
            ],
        },
        {
            "heading": "۱.۲ شرح مسئله (ادامه)",
            "subheading": "جایگاه در ادبیات",
            "paragraphs": [
                "مرورهای اخیر LLM در مهندسی نرم‌افزار بیشتر بر تکالیف نقطه‌ای تمرکز دارند تا زنجیرهٔ کامل تعریف→استقرار→sync در بستر ESB سازمانی [۲]. پارادایم GitOps به تکرارپذیری استقرار کمک می‌کند اما ذاتاً با مدل ذهنی گفت‌وگویی کاربر نهایی سازمانی گره نخورده است [۷،۸].",
            ],
        },
        {
            "heading": "۱.۳ اهداف",
            "subheading": "هدف اصلی: یکپارچه‌سازی چرخهٔ عمر سرویس سازمانی",
            "paragraphs": [
                "یکپارچه‌سازی و تسهیل چرخهٔ عمر سرویس سازمانی از تعریف و تأیید پیکربندی در ESM تا استقرار عملیاتی روی Kubernetes و همگام‌سازی زندهٔ اجرا بین چند نمونهٔ ESB، با کاهش اصطکاک دستی، قابلیت ردیابی تغییرات، و رعایت الزامات امنیتی و سازگاری با سرویس‌های SOAP موجود.",
                "هدف فرعی ۱ — زبان طبیعی و LLM: بهره‌گیری از LLM و ابزارها و تأیید انسان برای تولید و پیشنهاد پیکربندی سرویس و مسیرهای SOAP/REST در ESM.",
                "هدف فرعی ۲ — استقرار و همگرایی پیکربندی: استقرار خودکار روی Kubernetes و همگرایی پیکربندی بین مرکز مدیریت و نمونه‌های ESB (مکانیزم متناسب با بستر).",
                "هدف فرعی ۳ — بستر اجرایی: معماری واکنش‌گرا با Vert.x و Apache Camel، حفظ WS-Security و SOAP، احراز هویت مدرن، و سازمان‌دهی DDD.",
            ],
        },
        {
            "heading": "۱.۴ سوالات تحقیق",
            "subheading": "سوالات (نمونهٔ هم‌راستا با اهداف)",
            "paragraphs": [
                "۱. چگونه نقاط تأیید و audit در چرخهٔ تعریف تا استقرار تا sync طوری طراحی شوند که سرعت و قابلیت ردیابی حفظ شود؟",
                "۲. چه معیارهایی برای سنجش موفقیت یکپارچگی چرخه در برابر فرآیند دستی legacy مناسب است؟",
                "۳. در صورت تعارض خروجی LLM با محدودیت‌های امنیتی، چه مکانیزم fallback لازم است؟",
                "۴. چه الگویی برای اتصال LLM به APIهای داخلی ESM از نظر صحت و ایمنی مناسب‌تر است؟",
                "۵. چگونه جریان چندمرحله‌ای تأیید قبل از اعمال پیکربندی حساس طراحی شود؟",
                "۶. چه الگویی برای تخصیص منابع و مسیریابی path-based در استقرار چندسرویسی پایدار است؟",
                "۷. چگونه رویدادهای همگام‌سازی idempotent و قابل بازسازی باشند؟",
                "۸. چه استراتژی‌ای برای هم‌زیستی WS-Security ۱.۱ با OAuth2/API Key در یک دروازه پیشنهاد می‌شود؟",
                "۹. چه ملاحظاتی برای stack واکنش‌گرا در کنار CXF برای بار ترکیبی SOAP/REST وجود دارد؟",
            ],
        },
        {
            "heading": "۱.۵ محدودیت‌ها و تمرکز کار",
            "subheading": "محدودیت‌ها",
            "paragraphs": [
                "۱. دامنهٔ پروتکل: SOAP با WS-Security ۱.۱ و REST؛ GraphQL و gRPC خارج از نسخهٔ نخست.",
                "۲. مدل زبانی: فرض دسترسی به مدل تجاری یا باز قابل استقرار؛ مقایسهٔ جامع همهٔ مدل‌ها خارج از محدوده.",
                "۳. زبان رابط: تمرکز اولیه بر فارسی.",
                "۴. محیط استقرار: Kubernetes هدف اصلی؛ سایر ارکستراتورها محدود یا دستی.",
                "۵. ارزیابی بار: محدود به آزمایشگاه/پیش‌تولید تا فصل نتایج.",
            ],
        },
        {
            "heading": "۱.۵ محدودیت‌ها و تمرکز کار (ادامه)",
            "subheading": "تمرکز کار",
            "paragraphs": [
                "هستهٔ معماری DDD با ماژول‌های ESM Backend، ESB Core و Frontend؛ یکپارچگی LLM با ابزارها و تأیید کاربر؛ استقرار خودکار Kubernetes؛ همگرایی پیکربندی؛ حفظ WS-Security ۱.۱ و credential مدرن.",
            ],
        },
        {
            "heading": "منابع",
            "subheading": None,
            "paragraphs": [
                "[۱] V. S. De Castro et al., Research trends in enterprise service bus (ESB) applications: A systematic mapping study, IEEE Access, 2019. DOI: 10.1109/ACCESS.2019.2962134",
                "[۲] Q. Zhang et al., A survey on large language models for software engineering, Science China Information Sciences, 2025. DOI: 10.1007/s11432-025-4670-0",
                "[۳] A. Fan et al., Large language models for software engineering: Survey and open problems, arXiv:2310.03533, 2023.",
                "[۴] M. J. Jones, Rethinking the ESB: building a secure bus with an SOA gateway, Computer Fraud & Security, 2012.",
                "[۵] OASIS, Web Services Security: SOAP Message Security 1.1, OASIS Standard, 2006.",
                "[۶] N. Gruschka et al., Using WS-Security—Not as easy as it seems, Technical Report, 2006.",
                "[۷] A. Sharma and D. Spinellis, Evaluating GitOps for large-scale cloud-native systems, IEEE Software, 2022.",
                "[۸] R. Shrestha and A. A. N. Ali, Configuration management in Kubernetes environments: A GitOps approach, Proc. IEEE/ACM UCC, 2024.",
            ],
        },
    ],
}


def create_document():
    doc = Document()
    
    # تنظیمات صفحه برای RTL
    section = doc.sections[0]
    
    # عنوان اصلی
    title = doc.add_heading(CONTENT["title"], 0)
    title.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    
    for section_data in CONTENT["sections"]:
        # عنوان بخش
        doc.add_heading(section_data["heading"], level=1)
        
        if section_data["subheading"]:
            doc.add_heading(section_data["subheading"], level=2)
        
        # پاراگراف‌ها
        for para_text in section_data["paragraphs"]:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
            run = p.add_run(para_text)
            run.font.size = Pt(12)
            run.font.name = "B Nazanin"  # یا Tahoma اگر نصب نبود
    
    return doc


def main():
    script_dir = Path(__file__).parent
    output_path = script_dir / "فصل-اول-مقدمه.docx"
    
    print("در حال ایجاد فایل Word...")
    doc = create_document()
    doc.save(str(output_path))
    print(f"فایل ذخیره شد: {output_path}")
    print("\nتوجه: اگر فونت B Nazanin روی سیستم نصب نباشد، Word از فونت پیش‌فرض استفاده می‌کند.")
    print("می‌توانید بعد از باز کردن فایل، فونت را به Tahoma یا IRANSans تغییر دهید.")


if __name__ == "__main__":
    main()
