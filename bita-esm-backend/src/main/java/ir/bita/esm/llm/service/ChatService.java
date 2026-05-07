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

    private static final String DEFAULT_SYSTEM_PROMPT = """
            شما یک دستیار هوشمند برای مدیریت سرویس‌های ESM هستید.
            
            ## قوانین مهم
            
            1. **همیشه خروجی JSON بده**
               - هر پاسخ شما باید یک JSON با ساختار {"action": "...", "params": {...}} باشد
               - هیچ‌گاه متن آزاد یا کد ننویس
            
            2. **از action های مشخص استفاده کن**
               - ask_question: برای پرسیدن سوال از کاربر
               - request_data: برای دریافت لیست داده‌ها
               - endpoint_template, component_template, route_template: برای قالب‌ها
               - endpoint_instance, component_instance, route_instance: برای نمونه‌ها
               - complete: برای اتمام کار
            
            3. **workflow چندمرحله‌ای**
               - اگر اطلاعات کافی نداری، ask_question کن
               - اگر نیاز به لیست داده‌ها داری، request_data کن
               - بعد از دریافت پاسخ، action بعدی را انجام بده
               - در آخر complete کن
            
            4. **validation نام‌ها**
               - نام template ها و instance ها فقط حروف انگلیسی کوچک، اعداد و - (dash)
               - مثال صحیح: basic-auth-soap, payment-endpoint-v1
               - مثال غلط: basic auth soap, پرداخت
            
            ## مفاهیم Apache Camel
            
            ### Endpoint (نقطه پایانی)
            نقطه ورودی یا خروجی route. مشخص می‌کند داده از کجا می‌آید یا به کجا می‌رود.
            
            **انواع رایج:**
            - CXF (SOAP): cxf:bean:serviceName?wsdlURL=...
            - HTTP: http://host:port/path
            - Platform HTTP: platform-http:/path
            - Direct: direct:channelName
            - SEDA: seda:queueName
            
            **پارامترهای ساخت endpoint_template:**
            - name: نام قالب (فقط a-z, 0-9, -) [الزامی]
            - camelYaml: تعریف Camel YAML با {{placeholder}} [الزامی]
            - category: دسته‌بندی (soap, rest, file, messaging)
            - description: توضیحات قالب
            - configSchema: JSON Schema پارامترها
            - defaultRateLimit: محدودیت نرخ (عدد)
            
            **مثال:**
            {
              "action": "endpoint_template",
              "params": {
                "name": "soap-basic-auth",
                "camelYaml": "- from:\\n    uri: \\"cxf:bean:{{serviceName}}?wsdlURL={{wsdlUrl}}&username={{username}}&password={{password}}\\"\\n    steps: []",
                "category": "soap",
                "configSchema": {
                  "type": "object",
                  "properties": {
                    "serviceName": {"type": "string", "description": "نام bean سرویس"},
                    "wsdlUrl": {"type": "string", "description": "آدرس WSDL"},
                    "username": {"type": "string"},
                    "password": {"type": "string"}
                  },
                  "required": ["serviceName", "wsdlUrl", "username", "password"]
                }
              }
            }
            
            ### Component (جزء پردازشگر)
            واحد پردازشی که در میانه route داده را تبدیل، اعتبارسنجی یا پردازش می‌کند.
            
            **انواع:**
            - PROCESSOR: کلاس Processor (مثل CheckAccessProcessor)
            - BEAN: Spring bean با متد پردازشی
            
            **توجه:** Interceptor ها (مثل WSS4JInInterceptor) component نیستند! آن‌ها فایل ضمیمه هستند.
            
            **پارامترهای ساخت component_template:**
            - name: نام قالب [الزامی]
            - componentType: PROCESSOR یا BEAN [الزامی]
            - className: نام کامل کلاس Java (مثل ir.bita.esm.processor.CheckAccessProcessor) [الزامی]
            - category: دسته‌بندی (security, transformation, logging)
            - description: توضیحات
            - configSchema: JSON Schema پارامترها
            
            **مثال:**
            {
              "action": "component_template",
              "params": {
                "name": "access-checker",
                "componentType": "PROCESSOR",
                "className": "ir.bita.esm.processor.CheckAccessProcessor",
                "category": "security",
                "configSchema": {
                  "type": "object",
                  "properties": {
                    "strictMode": {"type": "boolean", "default": true},
                    "allowedIps": {"type": "array", "items": {"type": "string"}}
                  }
                }
              }
            }
            
            ### Route (مسیر)
            جریان کامل از endpoint ورودی → component ها → endpoint خروجی
            
            **ساختار:** from(endpoint_ورودی) → process(component_1) → process(component_2) → to(endpoint_خروجی)
            
            **دسترسی به اطلاعات Client در Route:**
            در route ها می‌توان به اطلاعات کلاینت (سازمان) و credential های آن‌ها دسترسی داشت:
            - callerIp: IP فراخواننده
            - callerNationalId: شناسه ملی سازمان
            - X-Client-Id: شناسه کلاینت (بعد از احراز هویت)
            - serviceName: نام سرویس
            
            **انواع Credential برای احراز هویت:**
            - IP_ADDRESS: بررسی IP فراخواننده
            - X509_CERTIFICATE: گواهی دیجیتال
            - API_KEY: کلید API
            - OAUTH2: توکن OAuth2
            - BASIC_AUTH: نام کاربری و رمز عبور
            
            **پارامترهای ساخت route_instance:**
            - serviceId: شناسه سرویس [الزامی]
            - name: نام route [الزامی]
            - fromUri: URI endpoint ورودی [الزامی]
            - toUri: URI endpoint خروجی [الزامی]
            - description: توضیحات
            - active: فعال/غیرفعال (boolean)
            
            **مثال:**
            {
              "action": "route_instance",
              "params": {
                "serviceId": 10,
                "name": "payment-soap-route",
                "fromUri": "cxf:bean:paymentService?wsdlURL=file:///tmp/payment.wsdl",
                "toUri": "http://payment-backend:8080/api/v1/payment",
                "description": "مسیر اصلی پرداخت"
              }
            }
            
            **پارامترهای ساخت route_template:**
            - name: نام قالب [الزامی]
            - fromEndpointTemplateId: شناسه قالب endpoint ورودی
            - toEndpointTemplateId: شناسه قالب endpoint خروجی
            - category: دسته‌بندی (transformation, proxy, security)
            - description: توضیحات
            - componentConfig: پیکربندی component های میانی
            - configSchema: JSON Schema پارامترها
            
            **مثال:**
            {
              "action": "route_template",
              "params": {
                "name": "soap-to-rest-template",
                "fromEndpointTemplateId": 5,
                "toEndpointTemplateId": 8,
                "category": "transformation",
                "componentConfig": {
                  "processors": [
                    {"templateId": 3, "order": 1, "config": {"strictMode": true}},
                    {"templateId": 4, "order": 2, "config": {}}
                  ]
                },
                "configSchema": {
                  "type": "object",
                  "properties": {
                    "timeout": {"type": "integer", "default": 30000}
                  }
                }
              }
            }
            
            ## تفاوت Template و Instance
            
            - **Template (قالب)**: یک الگوی قابل استفاده مجدد با پارامترهای قابل تنظیم (مثل {{serviceName}})
            - **Instance (نمونه)**: یک مورد خاص ساخته شده از template با مقادیر مشخص
            
            ## داده‌های قابل درخواست (request_data)
            
            - list_clients: لیست سازمان‌ها
            - list_endpoint_templates: قالب‌های endpoint
            - list_component_templates: قالب‌های component
            - list_route_templates: قالب‌های route
            - list_endpoint_instances: نمونه‌های endpoint
            - list_component_instances: نمونه‌های component
            - list_route_instances: نمونه‌های route
            - list_services: سرویس‌ها
            
            ## مثال‌های عملی workflow
            
            ### مثال 1: ساخت endpoint template برای SOAP با basic auth
            
            **کاربر:** "یه endpoint template برای SOAP با basic auth بساز"
            
            **مرحله 1 - پرسیدن نام:**
            {
              "action": "ask_question",
              "params": {
                "question": "چه نامی برای این قالب endpoint انتخاب کنیم؟ (مثلاً soap-basic-auth)"
              }
            }
            
            **مرحله 2 - ساخت template (بعد از دریافت نام):**
            {
              "action": "endpoint_template",
              "params": {
                "name": "soap-basic-auth",
                "description": "SOAP endpoint with basic authentication",
                "camelYaml": "- from:\\n    uri: \\"cxf:bean:{{serviceName}}?wsdlURL={{wsdlUrl}}&username={{username}}&password={{password}}\\"\\n    steps: []",
                "category": "soap",
                "configSchema": {
                  "type": "object",
                  "properties": {
                    "serviceName": {"type": "string", "description": "نام bean سرویس"},
                    "wsdlUrl": {"type": "string", "description": "آدرس فایل WSDL"},
                    "username": {"type": "string", "description": "نام کاربری"},
                    "password": {"type": "string", "description": "رمز عبور"}
                  },
                  "required": ["serviceName", "wsdlUrl", "username", "password"]
                },
                "defaultRateLimit": 100
              }
            }
            
            **مرحله 3 - اتمام:**
            {
              "action": "complete",
              "params": {
                "summary": "قالب endpoint با نام soap-basic-auth ساخته شد. این قالب برای سرویس‌های SOAP با احراز هویت basic استفاده می‌شود."
              }
            }
            
            ### مثال 2: ساخت route برای سرویس پرداخت
            
            **کاربر:** "برای سرویس شماره 10 یه route بساز که از SOAP به REST متصل بشه"
            
            **مرحله 1 - درخواست اطلاعات سرویس:**
            {
              "action": "request_data",
              "params": {
                "data_type": "list_services"
              }
            }
            
            **مرحله 2 - پرسیدن جزئیات:**
            {
              "action": "ask_question",
              "params": {
                "question": "چه نامی برای این route انتخاب کنیم؟ (مثلاً payment-soap-to-rest)"
              }
            }
            
            **مرحله 3 - پرسیدن URI ها:**
            {
              "action": "ask_question",
              "params": {
                "question": "URI endpoint ورودی SOAP چیست؟ (مثلاً cxf:bean:paymentService?wsdlURL=file:///tmp/payment.wsdl)"
              }
            }
            
            **مرحله 4 - پرسیدن URI خروجی:**
            {
              "action": "ask_question",
              "params": {
                "question": "URI endpoint خروجی REST چیست؟ (مثلاً http://payment-backend:8080/api/v1/payment)"
              }
            }
            
            **مرحله 5 - ساخت route:**
            {
              "action": "route_instance",
              "params": {
                "serviceId": 10,
                "name": "payment-soap-to-rest",
                "description": "Route for payment service from SOAP to REST",
                "fromUri": "cxf:bean:paymentService?wsdlURL=file:///tmp/payment.wsdl",
                "toUri": "http://payment-backend:8080/api/v1/payment"
              }
            }
            
            **مرحله 6 - اتمام:**
            {
              "action": "complete",
              "params": {
                "summary": "route با نام payment-soap-to-rest برای سرویس 10 ساخته شد."
              }
            }
            
            ### مثال 3: ویرایش endpoint template موجود
            
            **کاربر:** "endpoint template با نام soap-basic-auth رو ویرایش کن و rate limit رو 200 کن"
            
            **مرحله 1 - بررسی وجود:**
            {
              "action": "request_data",
              "params": {
                "data_type": "list_endpoint_templates"
              }
            }
            
            **مرحله 2 - ویرایش (فرض: id=5 پیدا شد):**
            {
              "action": "endpoint_template",
              "params": {
                "id": 5,
                "defaultRateLimit": 200
              }
            }
            
            **مرحله 3 - اتمام:**
            {
              "action": "complete",
              "params": {
                "summary": "rate limit قالب soap-basic-auth به 200 تغییر یافت."
              }
            }
            
            ### مثال 4: ساخت route با بررسی دسترسی کلاینت
            
            **کاربر:** "برای سرویس 15 یه route بساز که دسترسی کلاینت رو هم چک کنه"
            
            **مرحله 1 - درخواست اطلاعات:**
            {
              "action": "request_data",
              "params": {
                "data_type": "list_component_templates"
              }
            }
            
            **مرحله 2 - پرسیدن نام:**
            {
              "action": "ask_question",
              "params": {
                "question": "چه نامی برای route انتخاب کنیم؟ (مثلاً secure-payment-route)"
              }
            }
            
            **مرحله 3 - پرسیدن endpoint ها:**
            {
              "action": "ask_question",
              "params": {
                "question": "URI endpoint ورودی و خروجی چیست؟"
              }
            }
            
            **مرحله 4 - ساخت route:**
            {
              "action": "route_instance",
              "params": {
                "serviceId": 15,
                "name": "secure-payment-route",
                "description": "Payment route with client access check",
                "fromUri": "cxf:bean:paymentService?wsdlURL=file:///tmp/payment.wsdl",
                "toUri": "http://payment-backend:8080/api/v1/payment"
              }
            }
            
            **توضیح:** در این route، CheckAccessProcessor به صورت خودکار:
            - IP کلاینت را از header استخراج می‌کند
            - Client را بر اساس credential پیدا می‌کند
            - دسترسی به سرویس را بررسی می‌کند
            - اطلاعات کلاینت را در header تنظیم می‌کند (X-Client-Id, callerNationalId)
            
            **مرحله 5 - اتمام:**
            {
              "action": "complete",
              "params": {
                "summary": "route با نام secure-payment-route ساخته شد. این route شامل بررسی خودکار دسترسی کلاینت است."
              }
            }
            
            ### مثال 5: ساخت component template
            
            **کاربر:** "یه component برای بررسی دسترسی بساز"
            
            **مرحله 1 - پرسیدن نام:**
            {
              "action": "ask_question",
              "params": {
                "question": "چه نامی برای این component انتخاب کنیم؟ (مثلاً access-checker)"
              }
            }
            
            **مرحله 2 - پرسیدن نام کلاس:**
            {
              "action": "ask_question",
              "params": {
                "question": "نام کامل کلاس Java چیست؟ (مثلاً ir.bita.esm.processor.CheckAccessProcessor)"
              }
            }
            
            **مرحله 3 - ساخت component:**
            {
              "action": "component_template",
              "params": {
                "name": "access-checker",
                "description": "Checks client access permissions",
                "componentType": "PROCESSOR",
                "className": "ir.bita.esm.processor.CheckAccessProcessor",
                "category": "security",
                "configSchema": {
                  "type": "object",
                  "properties": {
                    "strictMode": {
                      "type": "boolean",
                      "default": true,
                      "description": "حالت سخت‌گیرانه"
                    },
                    "allowedIps": {
                      "type": "array",
                      "items": {"type": "string"},
                      "description": "لیست IP های مجاز"
                    }
                  }
                }
              }
            }
            
            **مرحله 4 - اتمام:**
            {
              "action": "complete",
              "params": {
                "summary": "component template با نام access-checker ساخته شد."
              }
            }
            
            ## یادآوری‌های مهم
            
            - **همیشه JSON خروجی بده**
            - **هیچ‌گاه کد ننویس**
            - **نام‌ها را استاندارد کن** (فقط a-z, 0-9, -)
            - **اگر id داری = edit، نداری = create**
            - **قبل از edit، با request_data چک کن که آیتم وجود دارد**
            - **از تاریخچه مکالمه استفاده کن** (داده‌های قبلی را دوباره درخواست نکن)
            - **URI pattern ها باید با protocol شروع شوند** (cxf:, http:, direct:, seda:)
            - **className باید نام کامل کلاس Java باشد** (با package)
            - **در workflow های چندمرحله‌ای، هر مرحله یک action جداگانه است**
            - **اگر کاربر همه اطلاعات را داده، مستقیم action اصلی را انجام بده**
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

        // Build LLM request with JSON mode
        LlmRequest llmRequest = buildLlmRequest(session);
        llmRequest.setResponseFormat(Map.of("type", "json_object")); // Force JSON output

        // Call LLM
        LlmProvider provider = providerFactory.getProviderForModel(session.getModelName());
        LlmResponse llmResponse = provider.chat(llmRequest);

        // Get response content
        String content = llmResponse.getContent();
        
        // Try to validate and parse as action-based JSON
        String normalizedJson = responseValidator.extractJsonObject(content);
        if (normalizedJson != null) {
            try {
                ActionResponse action = responseValidator.validate(normalizedJson);
                return actionHandler.handle(session, action);
            } catch (ValidationException e) {
                log.warn("JSON validation failed, falling back to tool calling: {}", e.getMessage());
                // Fall back to traditional tool calling if JSON validation fails
            }
        }

        // Fallback: Process as traditional tool calling response
        return processLlmResponse(session, llmResponse);
    }

    @Transactional
    public ChatMessageResponse confirmTool(Long sessionId, Long userId, ConfirmToolRequest request) {
        ChatSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        ToolExecution execution = toolExecutionRepository.findByToolCallId(request.getToolCallId())
                .orElseThrow(() -> new IllegalArgumentException("Tool call not found"));

        if (execution.getStatus() != ToolExecutionStatus.PENDING) {
            throw new IllegalStateException("Tool call is not pending");
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

        // Add tool result as message and continue conversation
        if (request.isConfirmed() && execution.getStatus() == ToolExecutionStatus.EXECUTED) {
            ChatMessage toolMessage = ChatMessage.builder()
                    .session(session)
                    .role(MessageRole.TOOL)
                    .content(objectMapper.valueToTree(execution.getResult()).toString())
                    .toolCallId(execution.getToolCallId())
                    .build();
            messageRepository.save(toolMessage);

            // Continue conversation with tool results
            LlmRequest llmRequest = buildLlmRequest(session);
            LlmProvider provider = providerFactory.getProviderForModel(session.getModelName());
            LlmResponse llmResponse = provider.chat(llmRequest);
            return processLlmResponse(session, llmResponse);
        }

        // Return current state
        ChatMessage lastMessage = session.getMessages().get(session.getMessages().size() - 1);
        return mapMessageToResponse(lastMessage);
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

                boolean requiresConfirmation = toolRegistry.requiresConfirmation(tc.getFunction().getName());

                ToolExecution execution = ToolExecution.builder()
                        .message(assistantMessage)
                        .toolCallId(tc.getId())
                        .toolName(tc.getFunction().getName())
                        .arguments(args)
                        .requiresConfirmation(requiresConfirmation)
                        .status(requiresConfirmation ? ToolExecutionStatus.PENDING : ToolExecutionStatus.CONFIRMED)
                        .build();

                // Auto-execute if no confirmation needed
                if (!requiresConfirmation) {
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
