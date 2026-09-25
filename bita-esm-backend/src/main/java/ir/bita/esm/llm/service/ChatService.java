package ir.bita.esm.llm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.bita.esm.auth.entity.User;
import ir.bita.esm.auth.repository.UserRepository;
import ir.bita.esm.llm.dto.*;
import ir.bita.esm.llm.entity.*;
import ir.bita.esm.llm.provider.LlmProvider;
import ir.bita.esm.llm.provider.LlmProviderFactory;
import ir.bita.esm.llm.repository.ChatMessageRepository;
import ir.bita.esm.llm.repository.ChatSessionRepository;
import ir.bita.esm.llm.repository.ToolExecutionRepository;
import ir.bita.esm.llm.tool.ToolRegistry;
import ir.bita.esm.llm.validation.LlmResponseValidator;
import ir.bita.esm.llm.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    /** For work that must commit independently of the surrounding turn (see {@link #confirmTool}). */
    private org.springframework.transaction.support.TransactionTemplate ownTransaction;

    @org.springframework.beans.factory.annotation.Autowired
    void setTransactionManager(org.springframework.transaction.PlatformTransactionManager tm) {
        this.ownTransaction = new org.springframework.transaction.support.TransactionTemplate(tm);
        this.ownTransaction.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private static final String DEFAULT_SYSTEM_PROMPT = """
            شما یک دستیار هوشمند برای تعریف سرویس روی گذرگاه بیتا (BITA ESB) هستید. معماری فعلی
            بر پایهٔ اسکریپت‌های Groovy است، نه Camel YAML/route-instance قدیمی — فقط از action
            هایی که در ادامه فهرست شده استفاده کن.

            ## قوانین مهم

            1. **همیشه خروجی JSON بده**
               - هر پاسخ شما باید یک JSON با ساختار {"action": "...", "params": {...}} باشد
               - هیچ‌گاه متن آزاد یا کد ننویس، مگر داخل پارامتر groovyCode یا scriptText یک action

            2. **فقط از این action ها استفاده کن — هیچ نام دیگری معتبر نیست:**
               - ask_question: پرسیدن سوال از کاربر (نیاز به تایید ندارد)
               - request_data: دریافت لیست داده (list_clients, list_services, list_component_templates)
               - create_service: ساخت سرویس جدید (و در صورت نیاز مجموعهٔ آن). همیشه اولین قدم
                 برای تعریف هر سرویس جدید است.
               - service_groovy_config: انتساب یک GroovyTemplate **موجود** به سرویس و پر کردن
                 متغیرهای آن. کد جدید نمی‌نویسد، فقط یک قالب آماده را با مقادیر واقعی سرویس
                 (آدرس backend، پورت، مسیر و مشابه) پر می‌کند.
               - groovy_template: افزودن یک GroovyTemplate **تازه** به کاتالوگ — فقط وقتی هیچ قالب موجودی
                 با نیاز جور نیست (قانون ۳). بعد از تأیید کاربر، سرویس با service_groovy_config از
                 همین قالب تازه ساخته می‌شود.
               - component_template: ساخت/ویرایش یک قطعهٔ Groovy قابل استفاده مجدد (یک
                 processor یا bean کوچک، نه یک سرویس کامل) که داخل یک GroovyTemplate با
                 #نام درج می‌شود.
               - rollback_service_config: بازگرداندن سرویس به یک نسخهٔ فرعی قبلی پیکربندی (مثلاً از
                 ۱.۷ به ۱.۶) روی همان آدرس. هر service_groovy_config تأییدشده یک نسخهٔ فرعی جدید
                 می‌سازد؛ اگر کاربر گفت «برگرد به نسخهٔ قبل» یا نسخهٔ مشخصی را خواست، از این استفاده کن
                 (بدون version فهرست نسخه‌ها را برمی‌گرداند).
               - list_services, list_clients, get_client: کوئری مستقیم داده
               - complete: پایان کار

            3. **وقتی هیچ قالب موجودی جور نیست:** دو حالت دارد.
               - اگر نیاز با امکانات گذرگاه شدنی است (HTTP/REST، SOAP، تبدیل JSON/XML، افزودن
                 هدر یا اعتبارنامه به فراخوانی ارائه‌دهنده، ترکیب پاسخ چند ارائه‌دهنده، محدودیت متد،
                 مهلت زمانی و مانند این‌ها)، با groovy_template یک قالب تازه پیشنهاد بده — طبق
                 «راهنمای نوشتن GroovyTemplate» پایین‌تر — و بعد از تأیید، سرویس را با
                 service_groovy_config از همان قالب بساز. قالب را عمومی و پارامتری بنویس (آدرس‌ها،
                 پورت، مسیر و مقادیر خاص سرویس متغیر باشند، نه ثابت در کد) تا برای درخواست‌های مشابه
                 بعدی هم به کار برود.
               - اگر نیاز به پروتکل یا کتابخانه‌ای بیرون از فهرست کتابخانه‌های گذرگاه دارد (مثلاً MQTT،
                 AMQP، FTP) یا با قوانین امنیتی در تضاد است، قالب نساز؛ **ادعا نکن که سرویس را
                 راه‌انداختی** و با ask_question محدودیت را صادقانه توضیح بده.

            4. **workflow چندمرحله‌ای**
               - اول همیشه create_service (سرویس و در صورت نیاز مجموعه را می‌سازد)
               - بعد بررسی کن آیا یکی از GroovyTemplate های فهرست‌شدهٔ زیر دقیقاً مطابقت دارد
               - اگر مطابقت داشت → فقط service_groovy_config با groovyTemplateId همان قالب
               - اگر مطابقت نداشت → طبق قانون ۳: یا groovy_template و سپس service_groovy_config با
                 قالب تازه، یا ask_question با صداقت دربارهٔ محدودیت
               - اگر اطلاعات کافی نداری، ask_question کن
               - در آخر complete کن

            5. **validation نام‌ها**
               - نام‌ها فقط حروف انگلیسی کوچک، اعداد و - (dash)
               - مثال صحیح: iban-validate, rest-proxy
               - مثال غلط: iban validate, اعتبارسنجی شبا

            6. **رعایت قوانین امنیتی (بخش پایین‌تر)**
               - قبل از هر component_template یا groovy_template، خروجی را با این
                 قوانین چک کن
               - اگر درخواست کاربر با یکی از قوانین امنیتی در تضاد بود، اجرا نکن و با
                 ask_question دلیل را توضیح بده

            ## قوانین امنیتی (Security Policy) — الزامی، هیچ استثنایی ندارد

            این پلتفرم فقط یک لایه یکپارچه‌سازی (integration layer) است و **هیچ داده‌ای نباید داخل خود پروژه ذخیره شود**.
            داده باید همیشه نزد سیستم مبدا/مقصد اصلی بماند و از این پلتفرم فقط عبور کند (pass-through).

            **ممنوعیت‌های صریح (در groovyCode هر component_template و scriptText هر groovy_template):**
            - اتصال مستقیم به دیتابیس به هر شکل ممنوع است: jdbc:, mysql, postgresql, postgres, mongodb, mongo, oracle:, sqlserver, mssql, redis (به عنوان storage), cassandra, elasticsearch (به عنوان storage), h2, sqlite, jpa:, hibernate
            - نوشتن روی فایل‌سیستم محلی برای نگهداری داده (نه فایل موقت پردازشی) ممنوع است
            - ساخت component/processor که داده را در حافظه/فایل/دیتابیس داخل پروژه cache یا persist کند ممنوع است

            **مجاز:** component/processor هایی که فقط transform، validate یا route می‌کنند بدون نگهداری دائمی داده.

            **اگر کاربر درخواست کرد** یک component با یکی از موارد ممنوع بسازی (مثلاً "نتیجه رو تو mysql ذخیره کن")، اجرا نکن. با ask_question توضیح بده که این پلتفرم داده را در خودش ذخیره نمی‌کند.

            ## راهنمای نوشتن GroovyTemplate (برای groovy_template)

            اسکریپت داخل گذرگاه اجرا می‌شود و این‌ها را در اختیار دارد:
            - متغیرهای خود قالب، مستقیم با نامشان (مثلاً pvAddress، gwPort، gwPath)
            - vertxInstance (io.vertx.core.Vertx)، gatewayRouter (io.vertx.ext.web.Router)، accessService
            - کتابخانه‌ها فقط: Vert.x core/web/web-client، Apache Camel core، Apache CXF و WSS4J، groovy-json، groovy-xml
            قواعد:
            - سرور HTTP جدید باز نکن؛ handler ها را روی gatewayRouter ثبت کن (گذرگاه خودش روی gwPort گوش می‌دهد)
            - متغیرهای gwPort و gwPath همیشه جزو variables قالب باشند
            - کنترل دسترسی مصرف‌کننده مانند قالب‌های کاتالوگ الزامی است
            - اسکریپت با یک RouteBuilder تمام شود (اگر route ای در Camel ندارد، configure خالی بماند)
            - هدر X-API-Key مصرف‌کننده به ارائه‌دهنده فرستاده نشود؛ شناسهٔ مصرف‌کننده را با X-Client-Id بفرست
            مرجع دقیق کتابخانه (Vert.x 4.5) — دقیقاً همین امضاها را به کار ببر:
            - WebClient.create(vertx) — کلاینت وب؛ HttpClient هستهٔ Vert.x را به کار نبر
            - web.requestAbs(HttpMethod.GET, "http://host:port/path") با نشانی کامل؛ یا
              web.request(HttpMethod.GET, port, "host", "/path") — ترتیب: متد، درگاه (int)، میزبان، مسیر
            - متد درخواست ورودی: ctx.request().method() (هرگز null نیست)؛ مسیر: ctx.request().path()
            - روی درخواست: .putHeader(name, value)، .timeout(میلی‌ثانیه)، سپس .send() یا .sendBuffer(buffer) یا
              .sendJsonObject(json) — هر سه Future<HttpResponse<Buffer>> برمی‌گردانند
            - روی پاسخ: resp.statusCode()، resp.bodyAsString()، resp.bodyAsJsonObject()، resp.getHeader(name)
            - مهلت تمام‌شده: Future با java.util.concurrent.TimeoutException شکست می‌خورد (کلاس
              io.vertx.core.TimeoutException وجود ندارد) — err instanceof java.util.concurrent.TimeoutException
            - چند فراخوانی هم‌زمان: io.vertx.core.CompositeFuture.all(f1, f2) و در نتیجه cf.resultAt(0)، cf.resultAt(1)
            - JSON: io.vertx.core.json.JsonObject یا groovy.json.JsonSlurper / JsonOutput؛ XML: groovy.xml.MarkupBuilder
              با StringWriter (مقدارها را escape می‌کند)
            - هر Future را با .onSuccess { } و .onFailure { err -> ctx.response().setStatusCode(502).end(...) } ببند
              تا هیچ درخواستی بی‌پاسخ نماند
            قالب پیش از ثبت روی یک گذرگاه آزمایشی اجرا می‌شود (کامپایل، راه‌اندازی و یک فراخوانی بدون کلید و با کلید)؛
            اگر خطا بدهد، متن خطا برمی‌گردد تا اصلاحش کنی.
            اسکلت:
            ```
            import io.vertx.core.Vertx
            import io.vertx.ext.web.Router
            import io.vertx.ext.web.handler.BodyHandler
            import io.vertx.ext.web.client.WebClient
            import org.apache.camel.builder.RouteBuilder

            def vertx  = vertxInstance as Vertx
            def router = gatewayRouter as Router
            def acc    = accessService
            def pv     = new URL(pvAddress as String)
            def web    = WebClient.create(vertx)

            router.route().handler(BodyHandler.create())
            router.route("/*").handler { ctx ->
                def apiKey   = ctx.request().getHeader("X-API-Key")
                def clientId = apiKey ? acc.getClientIdByApiKey(apiKey) : null
                if (clientId == null) { ctx.response().setStatusCode(401).end(apiKey ? "Unknown API key" : "API key required"); return }
                if (!acc.hasAccess(clientId)) { ctx.response().setStatusCode(403).end("No access to this service"); return }
                // ... رفتار خاص این قالب؛ فراخوانی ارائه‌دهنده با web.requestAbs(...) و پاسخ با ctx.response()
                // خطای ارائه‌دهنده → ctx.response().setStatusCode(502).end("Bad Gateway: ...")
            }

            new RouteBuilder() { void configure() {} }
            ```

            فهرست GroovyTemplate های موجود بلافاصله بعد از این بخش، در ادامهٔ همین پیام سیستم، آمده است — همیشه قبل از هر تصمیمی آن را بررسی کن.

            ## داده‌های قابل درخواست (request_data)

            - list_clients: لیست سازمان‌ها
            - list_services: لیست سرویس‌ها
            - list_component_templates: لیست قطعات Groovy قابل استفاده مجدد (نه GroovyTemplate کامل — فهرست GroovyTemplate ها بالاتر آمده)

            ## مثال‌های واقعی workflow

            ### سناریو الف: قالب موجود دقیقاً مطابقت دارد

            **کاربر:** سرویس REST جدیدی معرفی می‌کند که باید بدون تغییر proxy شود، با آدرس backend و پورت گذرگاه مشخص.

            **مرحله ۱ — ساخت سرویس:**
            {"action": "create_service", "params": {"serviceName": "iban-validate", "serviceVersion": "v1", "collectionName": "bank", "collectionBasePath": "/esb/bank", "description": "اعتبارسنجی شبا"}}

            **مرحله ۲ — بعد از بررسی فهرست GroovyTemplate بالا، اگر «rest-proxy» مطابقت داشت، فقط پر کردن متغیرها (بدون نوشتن کد جدید):**
            {"action": "service_groovy_config", "params": {"serviceId": 42, "groovyTemplateId": 3, "variableValues": {"pvAddress": "http://10.5.5.5:8080/api/iban", "gwPort": "18095", "gwPath": "/esb/bank/iban-validate/v1"}}}

            **مرحله ۳ — اتمام:**
            {"action": "complete", "params": {"summary": "سرویس iban-validate با قالب rest-proxy موجود پیکربندی شد."}}

            ### سناریو ب: هیچ قالبی مطابقت ندارد

            **کاربر:** پروتکلی می‌خواهد که هیچ‌کدام از GroovyTemplate های فهرست‌شده پشتیبانی نمی‌کنند (مثلاً MQTT وقتی فقط rest-proxy/soap موجود است).

            **مرحله ۱ — ساخت سرویس (طبق قانون ۴، همیشه اول):**
            {"action": "create_service", "params": {"serviceName": "temp-alerts", "serviceVersion": "v1", "collectionName": "iot", "collectionBasePath": "/esb/iot"}}

            **مرحله ۲ — MQTT در کتابخانه‌های گذرگاه نیست، پس قالب نمی‌سازیم؛ صداقت دربارهٔ محدودیت (طبق قانون ۳):**
            {"action": "ask_question", "params": {"question": "هیچ‌کدام از قالب‌های موجود از این پروتکل پشتیبانی نمی‌کنند؛ این نیاز به افزودن یک GroovyTemplate جدید توسط اپراتور دارد. آیا بروکر امکان پل‌زدن به HTTP/REST را دارد تا از قالب‌های موجود استفاده کنیم؟"}}

            ### سناریو ج: قالبی جور نیست ولی با امکانات گذرگاه شدنی است

            **کاربر:** سرویس REST که ارائه‌دهنده‌اش هدر Authorization: Bearer با یک توکن ثابت می‌خواهد (هیچ قالب موجودی هدر به ارائه‌دهنده اضافه نمی‌کند).

            **مرحله ۱:** create_service مثل سناریو الف.

            **مرحله ۲ — قالب تازه و عمومی (توکن، آدرس، پورت و مسیر متغیرند):**
            {"action": "groovy_template", "params": {"name": "rest-proxy-bearer", "description": "REST proxy که توکن Bearer ثابت را به فراخوانی ارائه‌دهنده اضافه می‌کند", "variables": [{"name": "pvAddress", "label": "آدرس سرویس", "type": "STRING", "required": true}, {"name": "gwPort", "label": "پورت گذرگاه", "type": "PORT", "required": true}, {"name": "gwPath", "label": "مسیر انتشار", "type": "STRING", "required": true}, {"name": "bearerToken", "label": "توکن ارائه‌دهنده", "type": "SECRET", "required": true}], "scriptText": "...کد کامل طبق اسکلت راهنما..."}}

            **مرحله ۳ — بعد از تأیید، پر کردن متغیرهای قالب تازه (id را از نتیجهٔ groovy_template بردار):**
            {"action": "service_groovy_config", "params": {"serviceId": 42, "groovyTemplateId": 9, "variableValues": {"pvAddress": "...", "gwPort": "...", "gwPath": "...", "bearerToken": "..."}}}

            ## یادآوری‌های مهم

            - **همیشه JSON خروجی بده**
            - **نام‌ها را استاندارد کن** (فقط a-z, 0-9, -)
            - **همیشه اول GroovyTemplate های فهرست‌شده را با درخواست کاربر مقایسه کن، قبل از هر تصمیم دیگر**
            - **اگر قالبی مطابقت دارد، فقط service_groovy_config بزن — هرگز component_template برای یک سرویس کامل که قالبش موجود است**
            - **اگر قالبی مطابقت ندارد، طبق قانون ۳: قالب تازه با groovy_template، یا اعلام صادقانهٔ محدودیت — هرگز ادعای دروغ**
            - **اگر قالب تازه‌ای که قبلاً ساخته شده در فهرست هست و با نیاز جور است، همان را به کار ببر؛ قالب تکراری نساز**
            - **هر create_service یا service_groovy_config فقط یک پیشنهاد است و تا تأیید کاربر اجرا نمی‌شود؛ اگر کاربر رد کرد و تغییری خواست (مثلاً «پورت ۱۸۰۹۶ باشد»)، همان action را با مقادیر اصلاح‌شده دوباره پیشنهاد بده**
            - **از تاریخچه مکالمه استفاده کن** (داده‌های قبلی را دوباره درخواست نکن)
            - **در workflow های چندمرحله‌ای، هر مرحله یک action جداگانه است**
            - **هیچ‌گاه اتصال به دیتابیس یا ذخیره‌سازی دائمی داده داخل پلتفرم نساز — طبق بخش "قوانین امنیتی"**
            """;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final UserRepository userRepository;
    private final LlmProviderFactory providerFactory;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final LlmResponseValidator responseValidator;
    private final ActionHandler actionHandler;
    private final AuthGuidanceService authGuidanceService;
    private final ir.bita.esm.route.repository.GroovyTemplateRepository groovyTemplateRepository;

    @Transactional
    public ChatSessionResponse createSession(Long userId, CreateSessionRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ChatSession session = ChatSession.builder()
                .user(user)
                .title(request.getTitle() != null ? request.getTitle() : "گفتگوی جدید")
                .modelName(request.getModelName() != null ? request.getModelName() : "openai")
                .systemPrompt(request.getSystemPrompt() != null ? request.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT)
                .build();

        session = sessionRepository.save(session);
        return mapToResponse(session, false);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long sessionId, Long userId, SendMessageRequest request) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.isActive()) {
            throw new IllegalStateException("Session is closed");
        }

        // Check for pending tool confirmations
        if (toolExecutionRepository.existsByMessageSessionIdAndStatus(sessionId, ToolExecutionStatus.PENDING)) {
            throw new IllegalStateException("Please confirm or reject pending tool calls first");
        }

        // Save user message
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role(MessageRole.USER)
                .content(request.getContent())
                .build();
        messageRepository.save(userMessage);

        // Build LLM request. responseFormat=json_object used to be forced here, but some
        // providers (confirmed live: Groq family via FreeLLMAPI) reject json_object
        // combined with `tools` outright (400 "json mode cannot be combined with
        // tool/function calling"). The system prompt's own "همیشه خروجی JSON بده"
        // instruction is enough — confirmed live that the model produces valid JSON-action
        // output without the forced flag on later turns already; dropping it here makes
        // turn 1 behave the same way turn 2 always has, and stops excluding tool-calling
        // providers that don't support both at once.
        LlmRequest llmRequest = buildLlmRequest(session);

        // Call LLM
        LlmProvider provider = providerFactory.getProviderForModel(session.getModelName());
        LlmResponse llmResponse = provider.chat(llmRequest);

        return handleLlmResponse(session, llmResponse);
    }

    /**
     * Both response protocols are offered to the model on every turn (native {@code
     * tool_calls} via {@code buildLlmRequest}'s {@code tools}, and JSON-action-in-text via
     * the system prompt's own instructions) — so both must be checked on every turn. This
     * used to only run from {@link #sendMessage}; {@link #confirmTool}'s continuation went
     * straight to {@link #processLlmResponse}, which reads ONLY {@code toolCalls} and never
     * looks at plain content. Confirmed live: after confirming create_service, the model
     * correctly answered with a JSON-fenced {@code service_groovy_config} action —
     * naming the right GroovyTemplate id from the catalog and the right extracted variable
     * values — but confirmTool's turn had no code path to recognize it, so the turn was
     * silently dropped despite the model doing everything right.
     */
    private ChatMessageResponse handleLlmResponse(ChatSession session, LlmResponse llmResponse) {
        return handleLlmResponse(session, llmResponse, true, 0);
    }

    private ChatMessageResponse handleLlmResponse(ChatSession session, LlmResponse llmResponse, boolean mayRepair) {
        return handleLlmResponse(session, llmResponse, mayRepair, 0);
    }

    /** Tools that end the model's turn on purpose: a question to the user, or the end of the task. */
    private static final java.util.Set<String> TURN_ENDING = java.util.Set.of("ask_question", "complete");
    /** How many data lookups in a row the model may make before the turn goes back to the user. */
    private static final int MAX_LOOKUPS_PER_TURN = 3;

    /**
     * A tool that runs without confirmation and does not end the turn (request_data, list_services,
     * ...) is a lookup the model made for itself: its result goes back to the model, which then
     * continues — up to {@link #MAX_LOOKUPS_PER_TURN} in a row.
     */
    private ChatMessageResponse continueAfterLookup(ChatSession session, ChatMessageResponse handled, int lookups) {
        var calls = handled.getToolCalls();
        if (handled.getPendingToolExecution() != null || calls == null || calls.size() != 1
                || TURN_ENDING.contains(calls.get(0).getToolName()) || lookups >= MAX_LOOKUPS_PER_TURN) {
            return handled;
        }
        var call = calls.get(0);
        Object content = call.getErrorMessage() != null
                ? Map.of("error", true, "message", call.getErrorMessage())
                : call.getResult();
        messageRepository.save(ChatMessage.builder()
                .session(session)
                .role(MessageRole.TOOL)
                .content(objectMapper.valueToTree(content == null ? Map.of() : content).toString())
                .toolCallId(call.getId())
                .build());
        LlmResponse next = providerFactory.getProviderForModel(session.getModelName()).chat(buildLlmRequest(session));
        return handleLlmResponse(session, next, true, lookups + 1);
    }

    /**
     * {@code mayRepair}: a reply that is an action in JSON but breaks the action format (e.g. no
     * "action" field) is sent back to the model once, with the validator's message, instead of
     * being shown to the user as text. Only the format is at issue — the model re-sends its own
     * decision — and only once, so a model that cannot produce the format still ends in plain text.
     */
    private ChatMessageResponse handleLlmResponse(ChatSession session, LlmResponse llmResponse, boolean mayRepair,
                                                  int lookups) {
        String normalizedJson = responseValidator.extractJsonObject(llmResponse.getContent());
        if (normalizedJson != null) {
            try {
                ActionResponse action = responseValidator.validate(normalizedJson);
                ChatMessageResponse handled = actionHandler.handle(session, action);
                handled.setFormatRepaired(!mayRepair);
                return continueAfterLookup(session, handled, lookups);
            } catch (ValidationException e) {
                boolean nativeToolCall = llmResponse.getToolCalls() != null && !llmResponse.getToolCalls().isEmpty();
                if (mayRepair && !nativeToolCall) {
                    log.warn("Action format invalid, asking the model once to re-send it: {}", e.getMessage());
                    return handleLlmResponse(session, repairFormat(session, llmResponse.getContent(), e.getMessage()),
                            false, lookups);
                }
                log.warn("JSON validation failed, falling back to tool calling: {}", e.getMessage());
                // Fall back to traditional tool calling if JSON validation fails
            }
        }

        // Fallback: Process as traditional tool calling response
        return continueAfterLookup(session, processLlmResponse(session, llmResponse), lookups);
    }

    /** One extra model call: the conversation so far, the malformed reply, and what was wrong with it. */
    private LlmResponse repairFormat(ChatSession session, String malformed, String problem) {
        LlmRequest request = buildLlmRequest(session);
        List<LlmRequest.Message> messages = new ArrayList<>(request.getMessages());
        messages.add(LlmRequest.Message.builder().role("assistant").content(malformed).build());
        messages.add(LlmRequest.Message.builder().role("user").content(
                "پاسخ قبلی قالب درستی نداشت: " + problem + ". همان تصمیم را بدون تغییر، فقط در قالب JSON "
                        + "تعریف‌شده (با فیلد action) دوباره بفرست.").build());
        request.setMessages(messages);
        return providerFactory.getProviderForModel(session.getModelName()).chat(request);
    }

    /**
     * The tool's outcome is committed on its own, before the model is asked to continue: a model
     * call that fails or times out must not roll back a tool that already ran (its effects, e.g. a
     * created service, are committed by the tool itself). Confirming the same call again then
     * resumes the conversation instead of running the tool a second time.
     */
    @Transactional
    public ChatMessageResponse confirmTool(Long sessionId, Long userId, ConfirmToolRequest request) {
        boolean decidedNow = Boolean.TRUE.equals(
                ownTransaction.execute(tx -> recordDecision(sessionId, userId, request)));

        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        ToolExecution execution = toolExecutionRepository.findByToolCallId(request.getToolCallId())
                .orElseThrow(() -> new IllegalArgumentException("Tool call not found"));
        List<ChatMessage> messages = session.getMessages();
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        boolean awaitingModel = (execution.getStatus() == ToolExecutionStatus.EXECUTED
                || execution.getStatus() == ToolExecutionStatus.FAILED)
                && lastMessage.getRole() == MessageRole.TOOL
                && execution.getToolCallId().equals(lastMessage.getToolCallId());

        if (!decidedNow && !awaitingModel) {
            throw new IllegalStateException("Tool call is not pending");
        }
        if (awaitingModel) {
            // Continue conversation with tool results
            LlmRequest llmRequest = buildLlmRequest(session);
            LlmProvider provider = providerFactory.getProviderForModel(session.getModelName());
            LlmResponse llmResponse = provider.chat(llmRequest);
            return handleLlmResponse(session, llmResponse);
        }

        // Return current state
        return mapMessageToResponse(lastMessage);
    }

    /** Runs or rejects a pending tool call and stores the outcome; false if it was not pending. */
    private boolean recordDecision(Long sessionId, Long userId, ConfirmToolRequest request) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ToolExecution execution = toolExecutionRepository.findByToolCallId(request.getToolCallId())
                .orElseThrow(() -> new IllegalArgumentException("Tool call not found"));

        if (execution.getStatus() != ToolExecutionStatus.PENDING) {
            return false;
        }

        if (request.isConfirmed()) {
            // Execute the tool
            execution.confirm();
            Map<String, Object> result = toolRegistry.executeTool(execution.getToolName(), execution.getArguments());

            if (result.containsKey("error") && (Boolean) result.get("error")) {
                execution.markFailed((String) result.get("message"));
            } else {
                execution.markExecuted(result);
            }
        } else {
            execution.reject();
        }

        toolExecutionRepository.save(execution);

        // Add the tool's result — or why it refused — as a message, so the model continues from it:
        // a refused call (validation, storage policy) is explained to the model, which can correct
        // its proposal (the corrected one again needs the user's confirmation) or tell the user
        if (request.isConfirmed()) {
            Object content = execution.getStatus() == ToolExecutionStatus.EXECUTED
                    ? execution.getResult()
                    : Map.of("error", true, "message", String.valueOf(execution.getErrorMessage()));
            ChatMessage toolMessage = ChatMessage.builder()
                    .session(session)
                    .role(MessageRole.TOOL)
                    .content(objectMapper.valueToTree(content).toString())
                    .toolCallId(execution.getToolCallId())
                    .build();
            messageRepository.save(toolMessage);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Page<ChatSessionResponse> getUserSessions(Long userId, Pageable pageable) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(s -> mapToResponse(s, false));
    }

    @Transactional(readOnly = true)
    public ChatSessionResponse getSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdWithMessages(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Access denied");
        }

        return mapToResponse(session, true);
    }

    @Transactional
    public void closeSession(Long sessionId, Long userId) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.close();
        sessionRepository.save(session);
    }

    /**
     * There is currently no tool through which the model can list GroovyTemplates
     * ({@code RequestDataTool}'s only {@code list_component_templates} branch queries the
     * unrelated, pre-Groovy-migration {@code ComponentTemplate} entity instead). Without
     * this, the model has no way to know a matching template already exists before
     * deciding whether to reuse it (service_groovy_config) or ask/build something new
     * (component_template) — confirmed live: it repeated {@code create_service} instead of
     * ever reaching service_groovy_config. Injecting the live catalog directly into the
     * first turn's context is the fix until a real discovery tool exists.
     */
    private String groovyTemplateCatalogSection() {
        var templates = groovyTemplateRepository.findByDeletedFalse(
                org.springframework.data.domain.Pageable.unpaged());
        if (templates.isEmpty()) {
            return "## قالب‌های Groovy موجود (GroovyTemplate)\n\nهیچ قالبی هنوز ثبت نشده است.";
        }
        StringBuilder sb = new StringBuilder("## قالب‌های Groovy موجود (GroovyTemplate)\n\n");
        sb.append("قبل از groovy_template یا component_template، همیشه اول بررسی کن که یکی از این قالب‌های موجود با درخواست کاربر همخوانی دارد یا نه؛ اگر همخوانی داشت، فقط service_groovy_config را با groovyTemplateId همان قالب صدا بزن، کد جدید ننویس.\n\n");
        for (var t : templates) {
            sb.append("- id=").append(t.getId())
                    .append(", name=\"").append(t.getName()).append("\"")
                    .append(": ").append(t.getDescription() != null ? t.getDescription() : "(بدون توضیح)")
                    .append("\n");
            // without the variable names the model can only guess the keys of variableValues
            if (t.getVariables() != null) {
                for (var v : t.getVariables()) {
                    sb.append("    - متغیر ").append(v.getName())
                            .append(" (").append(v.getType()).append(v.isRequired() ? "، اجباری" : "، اختیاری").append(")")
                            .append(v.getLabel() != null ? ": " + v.getLabel() : "")
                            .append("\n");
                }
            }
        }
        return sb.toString();
    }

    private LlmRequest buildLlmRequest(ChatSession session) {
        List<LlmRequest.Message> messages = new ArrayList<>();

        // Chat history
        List<ChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        
        // Add system prompt as part of the first user message
        boolean isFirstUserMessage = true;
        for (ChatMessage msg : history) {
            String messageContent = msg.getContent();
            
            // Prepend system prompt to first user message
            if (isFirstUserMessage && msg.getRole() == MessageRole.USER) {
                messageContent = "# دستورالعمل‌های سیستم\n\n"
                        + session.getSystemPrompt()
                        + "\n\n"
                        + authGuidanceService.promptSection()
                        + "\n\n"
                        + groovyTemplateCatalogSection()
                        + "\n\n---\n\n# درخواست کاربر\n\n"
                        + messageContent;
                isFirstUserMessage = false;
            }
            
            LlmRequest.Message.MessageBuilder msgBuilder = LlmRequest.Message.builder()
                    .role(msg.getRole().name().toLowerCase())
                    .content(messageContent);

            if (msg.getToolCalls() != null) {
                List<LlmRequest.ToolCall> toolCalls = msg.getToolCalls().stream()
                        .map(tc -> LlmRequest.ToolCall.builder()
                                .id((String) tc.get("id"))
                                .type("function")
                                .function(LlmRequest.FunctionCall.builder()
                                        .name((String) ((Map<String, Object>) tc.get("function")).get("name"))
                                        .arguments((String) ((Map<String, Object>) tc.get("function")).get("arguments"))
                                        .build())
                                .build())
                        .collect(Collectors.toList());
                msgBuilder.toolCalls(toolCalls);
            }

            if (msg.getToolCallId() != null) {
                msgBuilder.toolCallId(msg.getToolCallId());
                if (msg.getRole() == MessageRole.TOOL) {
                    toolExecutionRepository.findByToolCallId(msg.getToolCallId())
                            .ifPresent(e -> msgBuilder.name(e.getToolName()));
                }
            }

            messages.add(msgBuilder.build());
        }

        return LlmRequest.builder()
                .model(session.getModelName())
                .messages(messages)
                .tools(toolRegistry.getToolsForLlm())
                .temperature(0.7)
                .build();
    }

    private ChatMessageResponse processLlmResponse(ChatSession session, LlmResponse response) {
        String content = response.getContent();
        List<LlmResponse.ToolCall> toolCalls = response.getToolCalls();

        ChatMessage.ChatMessageBuilder messageBuilder = ChatMessage.builder()
                .session(session)
                .role(MessageRole.ASSISTANT)
                .content(content != null ? content : "");

        if (response.getUsage() != null) {
            messageBuilder.tokenCount(response.getUsage().getTotalTokens());
        }

        // Handle tool calls
        List<Map<String, Object>> toolCallData = null;
        List<ToolExecution> executions = new ArrayList<>();

        if (toolCalls != null && !toolCalls.isEmpty()) {
            toolCallData = new ArrayList<>();
            for (LlmResponse.ToolCall tc : toolCalls) {
                Map<String, Object> tcMap = Map.of(
                        "id", tc.getId(),
                        "type", "function",
                        "function", Map.of(
                                "name", tc.getFunction().getName(),
                                "arguments", tc.getFunction().getArguments()
                        )
                );
                toolCallData.add(tcMap);
            }
            messageBuilder.toolCalls(toolCallData);
        }

        ChatMessage assistantMessage = messageBuilder.build();
        assistantMessage = messageRepository.save(assistantMessage);

        // Create tool executions
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (LlmResponse.ToolCall tc : toolCalls) {
                Map<String, Object> args;
                try {
                    args = objectMapper.readValue(tc.getFunction().getArguments(), new TypeReference<>() {});
                } catch (Exception e) {
                    args = Map.of();
                }

                // A hallucinated tool name (e.g. a stale pre-migration action the model
                // picked up from somewhere) used to reach ToolRegistry.requiresConfirmation
                // -> getTool -> an uncaught IllegalArgumentException, which crashed this
                // whole @Transactional turn with an opaque UnexpectedRollbackException
                // instead of a clean, visible failure. Confirmed live against
                // gemini-3.5-flash-lite. Treat an unknown tool name as a failed execution,
                // not a fatal error.
                boolean toolExists = toolRegistry.getToolNames().contains(tc.getFunction().getName());
                boolean requiresConfirmation = toolExists && toolRegistry.requiresConfirmation(tc.getFunction().getName());

                ToolExecution execution = ToolExecution.builder()
                        .message(assistantMessage)
                        .toolCallId(tc.getId())
                        .toolName(tc.getFunction().getName())
                        .arguments(args)
                        .requiresConfirmation(requiresConfirmation)
                        .status(requiresConfirmation ? ToolExecutionStatus.PENDING : ToolExecutionStatus.CONFIRMED)
                        .build();

                if (!toolExists) {
                    execution.markFailed("ابزار نامعتبر: " + tc.getFunction().getName());
                } else if (!requiresConfirmation) {
                    // Auto-execute if no confirmation needed
                    Map<String, Object> result = toolRegistry.executeTool(tc.getFunction().getName(), args);
                    if (result.containsKey("error") && (Boolean) result.get("error")) {
                        execution.markFailed((String) result.get("message"));
                    } else {
                        execution.markExecuted(result);
                    }
                }

                executions.add(toolExecutionRepository.save(execution));
            }
        }

        return mapMessageToResponse(assistantMessage, executions);
    }

    private ChatSessionResponse mapToResponse(ChatSession session, boolean includeMessages) {
        ChatSessionResponse.ChatSessionResponseBuilder builder = ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .modelName(session.getModelName())
                .active(session.isActive())
                .messageCount(session.getMessages().size())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt());

        if (includeMessages) {
            builder.messages(session.getMessages().stream()
                    .map(this::mapMessageToResponse)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    private ChatMessageResponse mapMessageToResponse(ChatMessage message) {
        List<ToolExecution> executions = message.getToolExecutions();
        return mapMessageToResponse(message, executions != null ? executions : List.of());
    }

    private ChatMessageResponse mapMessageToResponse(ChatMessage message, List<ToolExecution> executions) {
        List<ChatMessageResponse.ToolCallResponse> toolCallResponses = executions.stream()
                .map(e -> ChatMessageResponse.ToolCallResponse.builder()
                        .id(e.getToolCallId())
                        .toolName(e.getToolName())
                        .arguments(e.getArguments())
                        .status(e.getStatus().name())
                        .requiresConfirmation(e.isRequiresConfirmation())
                        .result(e.getResult())
                        .errorMessage(e.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        ChatMessageResponse.ChatMessageResponseBuilder builder = ChatMessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .toolCalls(toolCallResponses.isEmpty() ? null : toolCallResponses)
                .createdAt(message.getCreatedAt());

        // Include pendingToolExecution when there's a PENDING tool requiring confirmation
        executions.stream()
                .filter(e -> e.getStatus() == ToolExecutionStatus.PENDING && e.isRequiresConfirmation())
                .findFirst()
                .ifPresent(pending -> builder.pendingToolExecution(ChatMessageResponse.ToolCallResponse.builder()
                        .id(pending.getToolCallId())
                        .toolName(pending.getToolName())
                        .arguments(pending.getArguments())
                        .status(pending.getStatus().name())
                        .requiresConfirmation(pending.isRequiresConfirmation())
                        .result(pending.getResult())
                        .errorMessage(pending.getErrorMessage())
                        .build()));

        return builder.build();
    }
}
