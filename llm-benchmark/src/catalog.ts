/**
 * Groovy template fixtures shown to the model through request_data.
 * Modelled on the real scripts in bita-esb-core/src/test/resources
 * (rest-proxy-gw-route.groovy, ws-security/echo-gw-route.groovy), whose bindings are
 * pvAddress / pvWsdlUri / gwPort. ESM has no seeded templates, so the benchmark ships its own.
 */
export type VarType = 'STRING' | 'SECRET' | 'INT' | 'BOOLEAN' | 'PORT'

export interface TemplateVar {
  name: string
  label: string
  type: VarType
  required: boolean
  enum?: string[]
  defaultValue?: string
}

export interface GroovyTemplateFixture {
  id: number
  name: string
  description: string
  variables: TemplateVar[]
}

const gw: TemplateVar[] = [
  { name: 'gwPort', label: 'پورت گیت‌وی', type: 'PORT', required: true },
  { name: 'gwPath', label: 'مسیر Ingress', type: 'STRING', required: true },
]

export const TEMPLATES: GroovyTemplateFixture[] = [
  {
    id: 101, name: 'rest-proxy',
    description: 'پروکسی ساده REST به REST با کنترل دسترسی BITA',
    variables: [{ name: 'pvAddress', label: 'آدرس سرویس ارائه‌دهنده', type: 'STRING', required: true }, ...gw],
  },
  {
    id: 102, name: 'rest-proxy-rate-limit',
    description: 'پروکسی REST با محدودیت نرخ درخواست برای هر کلاینت',
    variables: [
      { name: 'pvAddress', label: 'آدرس سرویس ارائه‌دهنده', type: 'STRING', required: true }, ...gw,
      { name: 'rateLimitPerMinute', label: 'حداکثر درخواست در دقیقه', type: 'INT', required: true },
    ],
  },
  {
    id: 103, name: 'soap-passthrough',
    description: 'عبور مستقیم SOAP بدون WS-Security',
    variables: [
      { name: 'pvAddress', label: 'آدرس سرویس SOAP', type: 'STRING', required: true },
      { name: 'pvWsdlUri', label: 'آدرس WSDL', type: 'STRING', required: true }, ...gw,
    ],
  },
  {
    id: 104, name: 'soap-ws-security',
    description: 'سرویس SOAP با WS-Security 1.1 (امضا و در صورت نیاز رمزنگاری با گواهی X.509)',
    variables: [
      { name: 'pvAddress', label: 'آدرس سرویس SOAP', type: 'STRING', required: true },
      { name: 'pvWsdlUri', label: 'آدرس WSDL', type: 'STRING', required: true }, ...gw,
      { name: 'securityPolicy', label: 'سیاست امنیتی', type: 'STRING', required: true,
        enum: ['Timestamp+Signature', 'Timestamp+Signature+Encryption'] },
      { name: 'signaturePropsFile', label: 'فایل properties امضا', type: 'STRING', required: true },
      { name: 'truststorePropsFile', label: 'فایل properties truststore', type: 'STRING', required: true },
    ],
  },
  {
    id: 105, name: 'rest-to-soap-bridge',
    description: 'دریافت REST/JSON از کلاینت و فراخوانی یک operation در سرویس SOAP',
    variables: [
      { name: 'pvAddress', label: 'آدرس سرویس SOAP', type: 'STRING', required: true },
      { name: 'pvWsdlUri', label: 'آدرس WSDL', type: 'STRING', required: true },
      { name: 'soapOperation', label: 'نام operation', type: 'STRING', required: true }, ...gw,
    ],
  },
  {
    id: 106, name: 'soap-backend-basic-auth',
    description: 'سرویس SOAP که خود ارائه‌دهنده با Basic Auth محافظت شده است (اعتبارنامهٔ downstream)',
    variables: [
      { name: 'pvAddress', label: 'آدرس سرویس SOAP', type: 'STRING', required: true },
      { name: 'pvWsdlUri', label: 'آدرس WSDL', type: 'STRING', required: true }, ...gw,
      { name: 'backendUsername', label: 'نام کاربری ارائه‌دهنده', type: 'STRING', required: true },
      { name: 'backendPassword', label: 'رمز ارائه‌دهنده', type: 'SECRET', required: true },
    ],
  },
]

export const templateById = (id: number) => TEMPLATES.find(t => t.id === id)
