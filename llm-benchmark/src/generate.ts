/**
 * Generates the benchmark dataset: 50 service-request documents (Persian) with ground truth.
 * Ground truth is sampled first and the document is rendered from it, so the expected
 * answer is never ambiguous. Deterministic for a given seed.
 *
 *   node src/generate.ts [--seed 1405] [--count 50]
 *
 * Then 10 L5 cases, generated after — and so without changing — the first 50: needs no catalog
 * template covers but the gateway can serve, each with a second, similar request that should
 * reuse the template the first one created. c51–c55 are the development split (seen while the
 * template path was built); c56–c60, the same five capabilities on other organisations, services
 * and values, are the test split, run only in the final measurement.
 */
import { mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { join } from 'node:path'
import { BENCH_DIR } from './env.ts'
import { TEMPLATES, templateById } from './catalog.ts'
import type { BenchCase, Capability, Level } from './types.ts'

const args = new Map(process.argv.slice(2).map((a, i, all) => [a, all[i + 1]]))
const SEED = Number(args.get('--seed') ?? 1405)
const COUNT = Number(args.get('--count') ?? 50)
// 50 = 20 complete · 15 noisy · 10 missing-field · 5 forbidden
const MIX: [Level, number][] = [['L1', 0.4], ['L2', 0.3], ['L3', 0.2], ['L4', 0.1]]

// mulberry32
let s = SEED >>> 0
const rnd = () => { s |= 0; s = (s + 0x6d2b79f5) | 0; let t = Math.imul(s ^ (s >>> 15), 1 | s); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296 }
const pick = <T>(xs: T[]): T => xs[Math.floor(rnd() * xs.length)]
const fa = (n: number | string) => String(n).replace(/\d/g, d => '۰۱۲۳۴۵۶۷۸۹'[Number(d)])

const ORGS = [
  ['بانک ملت', 'mellat'], ['بیمه ایران', 'bimeh-iran'], ['سازمان ثبت احوال', 'sabt'], ['گمرک ایران', 'gomrok'],
  ['سازمان تأمین اجتماعی', 'tamin'], ['شرکت ملی پست', 'post'], ['بانک سپه', 'sepah'], ['سازمان امور مالیاتی', 'tax'],
  ['شرکت ملی گاز', 'gas'], ['سازمان بهزیستی', 'behzisti'],
]
const SERVICES = [
  ['استعلام هویت', 'identity-inquiry', 'GetIdentity'], ['پرداخت قبض', 'bill-payment', 'PayBill'],
  ['استعلام وضعیت بیمه', 'insurance-status', 'GetInsuranceStatus'], ['رهگیری مرسوله', 'parcel-tracking', 'TrackParcel'],
  ['صدور گواهی', 'certificate-issue', 'IssueCertificate'], ['استعلام مالیاتی', 'tax-inquiry', 'GetTaxStatus'],
  ['ثبت اظهارنامه', 'declaration-submit', 'SubmitDeclaration'], ['انتقال وجه', 'fund-transfer', 'TransferFunds'],
  ['استعلام کد پستی', 'postal-code', 'GetPostalCode'], ['اعتبارسنجی شبا', 'iban-validate', 'ValidateIban'],
  ['استعلام سابقهٔ بیمه', 'insurance-history', 'GetInsuranceHistory'], ['استعلام قبض گاز', 'gas-bill', 'GetGasBill'],
]
const KIND: Record<number, (v: Record<string, string>) => string> = {
  101: () => pick(['سرویس از نوع REST است و فقط باید از طریق گذرگاه در اختیار سازمان‌های متقاضی قرار گیرد؛ محدودیت خاصی روی تعداد درخواست نداریم.',
    'این یک API مبتنی بر REST/JSON است که باید بدون تغییر از طریق بیتا منتشر شود.']),
  102: v => pick([`سرویس از نوع REST است. برای جلوگیری از فشار بار، هر کلاینت حداکثر ${v.rateLimitPerMinute} درخواست در دقیقه مجاز است.`,
    `API ما REST است و ظرفیت محدودی دارد؛ لطفاً سقف ${v.rateLimitPerMinute} فراخوانی در دقیقه برای هر سازمان مصرف‌کننده اعمال شود.`]),
  103: () => pick(['سرویس از نوع SOAP است و نیازی به امنیت سطح پیام (WS-Security) ندارد.',
    'وب‌سرویس ما SOAP است؛ امضا یا رمزنگاری پیام لازم نیست و فقط انتشار از طریق گذرگاه کافی است.']),
  104: v => v.securityPolicy === 'Timestamp+Signature'
    ? pick(['سرویس SOAP است و طبق الزامات امنیتی، پیام‌ها باید با WS-Security 1.1 امضا شوند (همراه با Timestamp)؛ رمزنگاری لازم نیست.',
        'وب‌سرویس SOAP ما امضای دیجیتال پیام با استاندارد WS-Security 1.1 و Timestamp را الزامی می‌داند، ولی رمزنگاری بدنه را نه.'])
    : pick(['سرویس SOAP است و طبق الزامات امنیتی، پیام‌ها باید با WS-Security 1.1 هم امضا و هم رمزنگاری شوند (همراه با Timestamp).',
        'به‌دلیل حساسیت داده، پیام‌های این وب‌سرویس SOAP باید طبق WS-Security 1.1 امضا، رمزنگاری و دارای Timestamp باشند.']),
  105: v => pick([`سامانه‌های مصرف‌کننده فقط REST/JSON پشتیبانی می‌کنند، اما سرویس ما SOAP است؛ گذرگاه باید درخواست REST را به operation «${v.soapOperation}» تبدیل کند.`,
    `کلاینت‌ها درخواست JSON می‌فرستند ولی پیاده‌سازی ما وب‌سرویس SOAP است؛ لطفاً درخواست‌ها به متد ${v.soapOperation} نگاشت شوند.`]),
  106: v => pick([`سرویس SOAP ما خودش با Basic Auth محافظت می‌شود؛ گذرگاه باید با نام کاربری ${v.backendUsername} و رمز ${v.backendPassword} به آن متصل شود.`,
    `دسترسی به وب‌سرویس SOAP ما نیازمند احراز هویت Basic است (کاربر: ${v.backendUsername}، رمز: ${v.backendPassword})؛ این اعتبارنامه را گذرگاه هنگام فراخوانی سرویس ما ارسال کند.`]),
}
const LABEL: Record<string, string> = {
  pvAddress: 'آدرس سرویس', pvWsdlUri: 'آدرس WSDL', gwPort: 'پورت اختصاصی گذرگاه', gwPath: 'مسیر انتشار روی گذرگاه',
  signaturePropsFile: 'فایل properties امضا', truststorePropsFile: 'فایل properties مخزن اعتماد (truststore)',
}
const NOISE = [
  'این سرویس در سال گذشته به‌صورت آزمایشی برای سه سازمان راه‌اندازی شده بود و اکنون قرار است رسمی شود.',
  'مستندات نسخهٔ قدیمی سرویس در http://docs.legacy.local/old-api موجود است که دیگر معتبر نیست و نباید استفاده شود.',
  'پیش‌بینی می‌شود روزانه حدود ۲۰۰۰ درخواست به این سرویس ارسال شود.',
  'برای هماهنگی با کارشناس فنی ما با شمارهٔ ۰۲۱-۸۸۷۷۶۶۵۵ تماس بگیرید.',
  'سرور آزمایشی ما روی 10.99.0.5:9090 است که فقط برای تست داخلی است و نباید منتشر شود.',
  'سطح توافق خدمت (SLA) این سرویس ۹۹.۵ درصد در ماه تعریف شده است.',
]
const FORBIDDEN = [
  (o: string) => `علاوه بر این، گذرگاه باید پاسخ هر درخواست را برای گزارش‌گیری مستقیم در دیتابیس MySQL ما به آدرس jdbc:mysql://10.40.1.20:3306/${o}_log ذخیره کند.`,
  () => 'این سرویس endpoint جداگانه ندارد؛ لطفاً گذرگاه مستقیم از جدول customers در PostgreSQL (jdbc:postgresql://10.40.1.21:5432/core) داده را بخواند و برگرداند.',
  (o: string) => `لطفاً تمام درخواست‌ها و پاسخ‌ها در یک دیتابیس MongoDB روی خود گذرگاه (mongodb://esb-local:27017/${o}) نگهداری شود تا بعداً تحلیل کنیم.`,
]

function values(tplId: number, org: string, svc: string, op: string, idx: number, ver: string): Record<string, string> {
  const host = `10.${20 + (idx % 30)}.${idx % 250}.${10 + (idx * 7) % 200}`
  const v: Record<string, string> = {
    pvAddress: tplId === 101 || tplId === 102 ? `http://${host}:8080/api/${svc}` : `http://${host}:8080/ws/${svc}`,
    gwPort: String(18000 + idx),
    gwPath: `/esb/${org}/${svc}/${ver}`,
  }
  if ([103, 104, 105, 106].includes(tplId)) v.pvWsdlUri = `http://${host}:8080/ws/${svc}?wsdl`
  if (tplId === 102) v.rateLimitPerMinute = String(pick([60, 120, 300, 600]))
  if (tplId === 104) {
    v.securityPolicy = pick(['Timestamp+Signature', 'Timestamp+Signature+Encryption'])
    v.signaturePropsFile = `${svc}-sig.properties`
    v.truststorePropsFile = `${svc}-trust.properties`
  }
  if (tplId === 105) v.soapOperation = op
  if (tplId === 106) { v.backendUsername = `${org}_esb`; v.backendPassword = `Pw${1000 + idx * 37}!x` }
  return v
}

function render(c: Omit<BenchCase, 'document' | 'followUp'>, orgFa: string, svcFa: string, noisy: boolean): string {
  const v = { ...c.expected.variables }
  const omit = c.missingVariable
  const fields = Object.keys(LABEL).filter(k => k in v && k !== omit)
  const lines: string[] = []
  lines.push(`موضوع: درخواست راه‌اندازی سرویس «${svcFa}» روی گذرگاه بیتا`, '', 'با سلام و احترام،')
  lines.push(`${orgFa} در نظر دارد سرویس «${svcFa}» را از طریق گذرگاه خدمات سازمانی بیتا در اختیار سازمان‌های متقاضی قرار دهد.`)
  if (noisy) lines.push(pick(NOISE.slice(0, 2)))
  lines.push(KIND[c.expected.templateId](v))
  const meta = `نام فنی سرویس ${c.expected.serviceName}، نسخهٔ ${c.expected.serviceVersion}، و مجموعهٔ ${c.expected.collectionName} است.`
  if (!noisy) {
    lines.push('', 'مشخصات فنی:', `- ${meta}`)
    for (const k of fields) lines.push(`- ${LABEL[k]}: ${v[k]}`)
  } else {
    // prose, Persian digits for the port, shuffled order, distractors in between
    const parts = fields.map(k => k === 'gwPort' ? `پورت ${fa(v[k])} برای این سرویس روی گذرگاه رزرو شده است.`
      : `${LABEL[k]} ${v[k]} است.`)
    parts.sort(() => rnd() - 0.5)
    parts.splice(1, 0, pick(NOISE.slice(2)))
    lines.push('', meta, ...parts)
  }
  if (c.level === 'L4') lines.push('', pick(FORBIDDEN)(c.expected.collectionName))
  lines.push('', 'با تشکر', `واحد فناوری اطلاعات ${orgFa}`)
  return lines.join('\n')
}

const out = join(BENCH_DIR, 'data', 'cases')
rmSync(out, { recursive: true, force: true })
mkdirSync(out, { recursive: true })

const levels: Level[] = MIX.flatMap(([l, share]) => Array(Math.round(share * COUNT)).fill(l))
const cases: BenchCase[] = []
levels.forEach((level, i) => {
  const tpl = TEMPLATES[i % TEMPLATES.length]
  const [orgFa, org] = ORGS[(i * 3) % ORGS.length]
  const [svcFa, svc, op] = SERVICES[(i * 5) % SERVICES.length]
  const ver = pick(['v1', 'v1', 'v2'])
  const vars = values(tpl.id, org, svc, op, i + 1, ver)
  // only fields that cannot be inferred from the rest of the document (pvAddress is derivable from the WSDL URL)
  const omittable = templateById(tpl.id)!.variables.map(x => x.name)
    .filter(n => ['gwPort', 'signaturePropsFile', 'truststorePropsFile'].includes(n) || (n === 'pvAddress' && !('pvWsdlUri' in vars)))
  const missing = level === 'L3' ? pick(omittable) : undefined
  const base = {
    id: `c${String(i + 1).padStart(2, '0')}`, level,
    expectedBehavior: level === 'L4' ? 'refuse' as const : level === 'L3' ? 'ask' as const : 'complete' as const,
    missingVariable: missing,
    expected: { templateId: tpl.id, templateName: tpl.name, serviceName: svc, serviceVersion: ver, collectionName: org, variables: vars },
  }
  const document = render(base, orgFa, svcFa, level === 'L2')
  const followUp = missing ? `${LABEL[missing]} ${vars[missing]} است.` : undefined
  const c: BenchCase = { ...base, document, followUp }
  cases.push(c)
  writeFileSync(join(out, `${c.id}.json`), JSON.stringify(c, null, 2))
})
// ── L5: no template fits, the gateway can do it ──────────────────────────────────────────────
const CAPABILITY: Record<Capability, (v: Record<string, string>) => string> = {
  'bearer-token': v => `سرویس ما REST است، ولی فقط فراخوانی‌هایی را می‌پذیرد که هدر Authorization: Bearer ${v.bearerToken} داشته باشند. `
    + 'سازمان‌های مصرف‌کننده این توکن را ندارند و نباید داشته باشند؛ گذرگاه باید خودش آن را به هر فراخوانی سرویس ما اضافه کند.',
  'json-to-xml': () => 'سرویس ما REST است و پاسخ را JSON برمی‌گرداند، اما سامانه‌های مصرف‌کننده فقط XML می‌فهمند. '
    + 'گذرگاه باید پاسخ را به XML تبدیل کند: یک عنصر ریشهٔ <response> و زیر آن برای هر فیلد JSON یک عنصر هم‌نام با همان مقدار، با Content-Type برابر application/xml.',
  'get-only': () => 'سرویس ما REST است و فقط برای خواندن است. گذرگاه باید فقط درخواست‌های GET را به سرویس ما برساند '
    + 'و هر متد دیگری (POST، PUT، DELETE و ...) را بدون فراخوانی سرویس ما با کد 405 رد کند.',
  'fan-out': v => `این سرویس از دو سرویس REST موجود ما ساخته می‌شود: سرویس اول در ${v.pvAddressFirst} و سرویس دوم در ${v.pvAddressSecond}. `
    + 'برای هر درخواست GET، گذرگاه باید هر دو را با همان مسیر فراخوانی کند و یک JSON ترکیبی برگرداند: {"first": پاسخ سرویس اول, "second": پاسخ سرویس دوم}.',
  'timeout': v => `سرویس ما REST است و گاهی کند می‌شود. اگر پاسخ سرویس ما بیش از ${v.timeoutMs} میلی‌ثانیه طول کشید، `
    + 'گذرگاه نباید منتظر بماند و باید به مصرف‌کننده کد 504 برگرداند؛ در غیر این صورت پاسخ را بدون تغییر برگرداند.',
}
function l5Values(cap: Capability, org: string, svc: string, idx: number, ver: string): Record<string, string> {
  const host = `10.${60 + (idx % 30)}.${idx % 250}.${10 + (idx * 7) % 200}`
  const v: Record<string, string> = { gwPort: String(18000 + idx), gwPath: `/esb/${org}/${svc}/${ver}` }
  if (cap === 'fan-out') {
    v.pvAddressFirst = `http://${host}:8080/api/${svc}-a`
    v.pvAddressSecond = `http://${host}:8081/api/${svc}-b`
  } else {
    v.pvAddress = `http://${host}:8080/api/${svc}`
  }
  if (cap === 'bearer-token') v.bearerToken = `tk-${Math.floor(rnd() * 0xffffffff).toString(16)}`
  if (cap === 'timeout') v.timeoutMs = String(pick([1000, 1500, 2000]))
  return v
}
function renderL5(cap: Capability, orgFa: string, svcFa: string, svc: string, ver: string, org: string, v: Record<string, string>): string {
  const lines = [`موضوع: درخواست راه‌اندازی سرویس «${svcFa}» روی گذرگاه بیتا`, '', 'با سلام و احترام،',
    `${orgFa} در نظر دارد سرویس «${svcFa}» را از طریق گذرگاه خدمات سازمانی بیتا در اختیار سازمان‌های متقاضی قرار دهد.`,
    CAPABILITY[cap](v), '', 'مشخصات فنی:', `- نام فنی سرویس ${svc}، نسخهٔ ${ver}، و مجموعهٔ ${org} است.`]
  if (v.pvAddress) lines.push(`- آدرس سرویس: ${v.pvAddress}`)
  lines.push(`- پورت اختصاصی گذرگاه: ${v.gwPort}`, `- مسیر انتشار روی گذرگاه: ${v.gwPath}`, '', 'با تشکر', `واحد فناوری اطلاعات ${orgFa}`)
  return lines.join('\n')
}
const CAPS: Capability[] = ['bearer-token', 'json-to-xml', 'get-only', 'fan-out', 'timeout']
const L5_SPLITS: ('dev' | 'test')[] = ['dev', 'test']
L5_SPLITS.flatMap(split => CAPS.map(cap => [split, cap] as const)).forEach(([split, cap]) => {
  const i = cases.length
  const [orgFa, org] = ORGS[(i * 3) % ORGS.length]
  const [svcFa, svc] = SERVICES[(i * 5) % SERVICES.length]
  const ver = pick(['v1', 'v1', 'v2'])
  const vars = l5Values(cap, org, svc, i + 1, ver)
  // the reuse request: another organisation, service and port, same capability
  const [rOrgFa, rOrg] = ORGS[(i * 3 + 5) % ORGS.length]
  const [rSvcFa, rSvc] = SERVICES[(i * 5 + 7) % SERVICES.length]
  const rVars = l5Values(cap, rOrg, rSvc, i + 11, 'v1')
  const c: BenchCase = {
    id: `c${i + 1}`, level: 'L5', expectedBehavior: 'create-template', capability: cap, split,
    expected: { templateId: 0, templateName: '-', serviceName: svc, serviceVersion: ver, collectionName: org, variables: vars },
    document: renderL5(cap, orgFa, svcFa, svc, ver, org, vars),
    reuse: { document: renderL5(cap, rOrgFa, rSvcFa, rSvc, 'v1', rOrg, rVars), serviceName: rSvc, collectionName: rOrg, variables: rVars },
  }
  cases.push(c)
  writeFileSync(join(out, `${c.id}.json`), JSON.stringify(c, null, 2))
})

writeFileSync(join(BENCH_DIR, 'data', 'manifest.json'), JSON.stringify({
  seed: SEED, count: cases.length,
  byLevel: Object.fromEntries([...MIX.map(([l]) => l), 'L5'].map(l => [l, cases.filter(c => c.level === l).length])),
  byTemplate: Object.fromEntries(TEMPLATES.map(t => [t.name, cases.filter(c => c.expected.templateId === t.id).length])),
  byCapability: Object.fromEntries(CAPS.map(cap => [cap, cases.filter(c => c.capability === cap).length])),
  l5Split: Object.fromEntries(L5_SPLITS.map(sp => [sp, cases.filter(c => c.split === sp).map(c => c.id)])),
}, null, 2))
console.log(`wrote ${cases.length} cases to ${out}`)
