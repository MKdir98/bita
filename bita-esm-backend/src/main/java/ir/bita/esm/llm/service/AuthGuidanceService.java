package ir.bita.esm.llm.service;

import org.springframework.stereotype.Service;

/**
 * Centralized standards for BITA authentication guidance in LLM prompts.
 */
@Service
public class AuthGuidanceService {

    public String promptSection() {
        return """
                ## استاندارد احراز هویت BITA
                
                - احراز هویت Caller باید مبتنی بر credentialهای BITA باشد (IP_ADDRESS, X509_CERTIFICATE, API_KEY, OAUTH2, BASIC_AUTH)
                - این احراز هویت در Route/Component (مثل CheckAccessProcessor) انجام می‌شود، نه با قراردادن مستقیم username/password در همه endpoint template ها
                - endpoint_template از نوع HTTP را به صورت generic بساز مگر کاربر explicit برای downstream auth مقدار بخواهد
                - اگر host مقصد مشخص نیست:
                  1) برای سرویس داخلی: اول request_data با data_type=list_services بگیر و k8sServiceName را بخوان
                  2) برای سرویس خارجی: با ask_question host/port/path را از کاربر بپرس
                - مقدار پیش‌فرض مقصد داخلی:
                  - host = k8sServiceName (مثال: payment-1-0-esb-svc)
                  - port = 8080
                - برای امنیت، از قرار دادن secret واقعی در خروجی خودداری کن؛ فقط placeholder یا schema بده
                """;
    }
}
