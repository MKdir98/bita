/**
 * Rule-based baseline (no language model) on the same documents as B1 and B2: keyword rules pick
 * the template, one regex per field label extracts the values, a missing required field means
 * "ask", a storage target means "refuse".
 *
 * The rules were written knowing how generate.ts phrases each template and field, so this is an
 * upper bound for a form-like parser on this dataset — it measures how much of the dataset a
 * parser can solve without a model, not how a parser would fare on real request documents.
 *
 *   node src/baseline.ts
 */
import { readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { BENCH_DIR } from './env.ts'
import { templateById, TEMPLATES } from './catalog.ts'
import type { BenchCase } from './types.ts'

const faDigits = (s: string) => s.replace(/[۰-۹]/g, d => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)))
const clean = (s: string | undefined) => s?.replace(/[،.؛)]+$/u, '').trim()

function pickTemplate(doc: string): string {
  const wss = /WS-Security/.test(doc) && !/نیازی به امنیت سطح پیام/.test(doc)
  if (wss) return 'soap-ws-security'
  if (/Basic/.test(doc)) return 'soap-backend-basic-auth'
  if (/SOAP/.test(doc) && /(REST|JSON)/.test(doc) && /(operation|متد|نگاشت|تبدیل)/.test(doc)) return 'rest-to-soap-bridge'
  if (/SOAP/.test(doc)) return 'soap-passthrough'
  if (/در دقیقه/.test(doc)) return 'rest-proxy-rate-limit'
  return 'rest-proxy'
}

function extract(doc: string): Record<string, string> {
  const d = faDigits(doc)
  const m = (re: RegExp) => clean(d.match(re)?.[1])
  const v: Record<string, string | undefined> = {
    pvAddress: m(/آدرس سرویس:?\s*(https?:\/\/\S+)/),
    pvWsdlUri: m(/آدرس WSDL:?\s*(https?:\/\/\S+)/),
    gwPort: m(/پورت اختصاصی گذرگاه:?\s*(\d+)/) ?? m(/پورت\s+(\d+)\s+برای این سرویس/),
    gwPath: m(/مسیر انتشار روی گذرگاه:?\s*(\/\S+)/),
    signaturePropsFile: m(/فایل properties امضا:?\s*(\S+)/),
    truststorePropsFile: m(/\(truststore\):?\s*(\S+)/),
    rateLimitPerMinute: m(/(\d+)\s+(?:درخواست|فراخوانی) در دقیقه/),
    soapOperation: m(/operation\s*«(\w+)»/) ?? m(/به متد\s+(\w+)/),
    backendUsername: m(/نام کاربری\s+(\S+)/) ?? m(/کاربر:\s*(\S+?)،/),
    backendPassword: m(/رمز\s+(\S+)\s+به آن/) ?? m(/رمز:\s*(\S+?)\)/),
  }
  if (!v.pvAddress && v.pvWsdlUri) v.pvAddress = v.pvWsdlUri.replace(/\?wsdl$/, '')
  if (/WS-Security/.test(doc) && !/نیازی به امنیت سطح پیام/.test(doc)) {
    v.securityPolicy = /(رمزنگاری لازم نیست|رمزنگاری بدنه را نه)/.test(doc) ? 'Timestamp+Signature' : 'Timestamp+Signature+Encryption'
  }
  return Object.fromEntries(Object.entries(v).filter(([, x]) => x !== undefined)) as Record<string, string>
}

// data/cases: generated (generate.ts); data/freeform: hand-written, varied wording (level L6).
// These rules were frozen before any free-form document was written.
const load = (dir: string) => {
  const path = join(BENCH_DIR, 'data', dir)
  let files: string[] = []
  try { files = readdirSync(path) } catch { return [] }
  return files.filter(f => /^[cf]\d+\.json$/.test(f)).sort().map(f => JSON.parse(readFileSync(join(path, f), 'utf8')))
}
const cases: BenchCase[] = [...load('cases'), ...load('freeform')]
  .filter(c => ['L1', 'L2', 'L3', 'L4', 'L6'].includes(c.level))

const rows = cases.map(c => {
  const chosen = pickTemplate(c.document)
  const values = extract(c.document)
  const tpl = TEMPLATES.find(t => t.name === chosen)!
  const missing = tpl.variables.filter(x => x.required && !(x.name in values)).map(x => x.name)
  const storage = /(jdbc:|mongodb:|MySQL|PostgreSQL|MongoDB)/.test(c.document)
  const behavior = storage ? 'refuse' : missing.length ? 'ask' : 'complete'
  const expectedVars = c.expected.variables
  const wrong = Object.keys(expectedVars).filter(k => k !== c.missingVariable && values[k] !== expectedVars[k])
  return {
    id: c.id, level: c.level, expectedBehavior: c.expectedBehavior, behavior,
    templateCorrect: chosen === c.expected.templateName,
    variablesCorrect: behavior === 'complete' && wrong.length === 0,
    behaviorCorrect: behavior === c.expectedBehavior,
    wrong, missing,
  }
})

const pct = (n: number, d: number) => `${n}/${d} (${Math.round(100 * n / d)}%)`
const by = (l: string) => rows.filter(r => r.level === l)
const summary = {
  L1: { template: pct(by('L1').filter(r => r.templateCorrect).length, 20), allParams: pct(by('L1').filter(r => r.variablesCorrect).length, 20) },
  L2: { template: pct(by('L2').filter(r => r.templateCorrect).length, 15), allParams: pct(by('L2').filter(r => r.variablesCorrect).length, 15) },
  L3: { asked: pct(by('L3').filter(r => r.behavior === 'ask').length, 10) },
  L4: { refused: pct(by('L4').filter(r => r.behavior === 'refuse').length, 5) },
  L6: { template: pct(by('L6').filter(r => r.templateCorrect).length, by('L6').length || 1),
        allParams: pct(by('L6').filter(r => r.variablesCorrect).length, by('L6').length || 1) },
  failures: rows.filter(r => !r.behaviorCorrect || (r.expectedBehavior === 'complete' && !r.variablesCorrect))
    .map(r => ({ id: r.id, level: r.level, behavior: r.behavior, wrong: r.wrong, missing: r.missing })),
}
mkdirSync(join(BENCH_DIR, 'results'), { recursive: true })
writeFileSync(join(BENCH_DIR, 'results', 'baseline-regex.json'), JSON.stringify({ summary, rows }, null, 2))
console.log(JSON.stringify(summary, null, 2))
void templateById
