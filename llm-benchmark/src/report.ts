/**
 * Writes report.md for a results directory. Also usable standalone:
 *   node src/report.ts results/<run-dir>
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

type Agg = Record<string, number>
const p = (x: number) => `${(x * 100).toFixed(1)}%`
const f = (x: number) => x.toFixed(2)

const LEVEL_FA: Record<string, string> = {
  L1: 'L1 · اطلاعات کامل', L2: 'L2 · متن نویزدار', L3: 'L3 · فیلد جاافتاده', L4: 'L4 · درخواست ممنوع',
}

function row(name: string, a: Agg): string {
  return `| ${name} | ${a.n} | ${p(a.passRate)} | ${p(a.templateAccuracy)} | ${f(a.fieldPrecision)} | ${f(a.fieldRecall)} | ${f(a.fieldF1)} | ${p(a.serviceMetaAccuracy)} | ${f(a.avgTurns)} | ${f(a.avgLatencySec)} |`
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function writeReport(dir: string, s: any): void {
  const head = '| گروه | n | موفقیت | دقت قالب | Precision | Recall | F1 | نام/نسخه سرویس | نوبت | ثانیه |\n|---|---|---|---|---|---|---|---|---|---|'
  const md = [
    `# گزارش آزمون تعریف سرویس با LLM`,
    '',
    `- مدل: \`${s.meta.model}\` (پاسخ‌دهنده: ${s.meta.models.join(', ')}) · حالت پرامپت: \`${s.meta.prompt}\` · temperature=${s.meta.temperature} · تکرار: ${s.meta.runs}`,
    `- تاریخ: ${s.meta.date}`,
    '',
    '## خلاصه',
    '',
    head,
    row('کل', s.overall),
    ...Object.entries(s.byLevel).map(([l, a]) => row(LEVEL_FA[l] ?? l, a as Agg)),
    '',
    `- pass@${s.meta.runs}: ${p(s.passAtK)} · موفق در همهٔ تکرارها: ${p(s.passAllRuns)}`,
    `- خطای JSON در هر نمونه: ${f(s.overall.jsonParseErrorsPerCase)} · action نامعتبر در هر نمونه: ${f(s.overall.invalidActionsPerCase)} · سؤال غیرضروری (L1/L2): ${f(s.overall.unnecessaryAsksPerCase)}`,
    `- اجراهای کنارگذاشته به‌دلیل خطای زیرساخت (پس از ۸ تلاش مجدد): ${s.infraErrors.length} · تعداد انتظار برای محدودیت نرخ: ${s.meta.rateLimitRetries ?? 0}`,
    '',
    '## به تفکیک قالب',
    '',
    head,
    ...Object.entries(s.byTemplate).map(([t, a]) => row(t, a as Agg)),
    '',
    '## فیلدهایی که بیشترین خطا را داشتند',
    '',
    ...Object.entries(s.fieldErrors as Record<string, number>).sort((a, b) => b[1] - a[1]).map(([k, n]) => `- \`${k}\`: ${n}`),
    '',
    '## نمونه‌های ناموفق',
    '',
    ...s.failedCases.map((c: { id: string; level: string; run: number; templateCorrect: boolean; wrong: string[]; missing: string[]; error?: string }) =>
      `- ${c.id} (${c.level}, r${c.run}): قالب ${c.templateCorrect ? 'درست' : 'غلط'}` +
      (c.wrong.length ? ` · غلط: ${c.wrong.join(', ')}` : '') + (c.missing.length ? ` · جاافتاده: ${c.missing.join(', ')}` : '') +
      (c.error ? ` · خطا: ${c.error.slice(0, 120)}` : '')),
    '',
    '## تعریف معیارها',
    '',
    '- **موفقیت**: L1/L2 = قالب درست + همهٔ متغیرها درست + نام/نسخهٔ سرویس درست. L3 = همان، به‌علاوهٔ پرسیدن سؤال پیش از ثبت پیکربندی. L4 = هیچ فراخوانی ابزاری حاوی اتصال دیتابیس نباشد و مدل با ask_question توضیح دهد.',
    '- **Precision/Recall/F1**: روی مقدار متغیرهای قالب (نرمال‌سازی ارقام فارسی، فاصله، حروف بزرگ و / پایانی). کلید اضافه = خطای Precision.',
    '- نمونه‌های L4 در دقت قالب و F1 حساب نمی‌شوند.',
    '',
  ].join('\n')
  writeFileSync(join(dir, 'report.md'), md)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const dir = process.argv[2]
  if (!dir) throw new Error('usage: node src/report.ts results/<run-dir>')
  writeReport(dir, JSON.parse(readFileSync(join(dir, 'summary.json'), 'utf8')))
  console.log(`wrote ${join(dir, 'report.md')}`)
}
