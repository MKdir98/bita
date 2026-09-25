/**
 * Runs every case through the LLM with the ESM prompt and a simulated ESM tool layer,
 * then scores the result against ground truth.
 *
 *   node src/run.ts [--model auto] [--runs 1] [--limit N] [--only c01,c07]
 *                   [--prompt esm+tools|esm] [--concurrency 3] [--temperature 0.7] [--max-turns 10]
 */
import { readdirSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { BENCH_DIR, loadEnv, requireEnv } from './env.ts'
import { FreeLLMApiClient, type FreeLLMChatMessage } from './freellm-client.ts'
import { TEMPLATES } from './catalog.ts'
import { firstUserMessage, type PromptMode } from './prompt.ts'
import { scoreRun, summarize } from './score.ts'
import { writeReport } from './report.ts'
import type { BenchCase, RunRecord, ToolCall } from './types.ts'

loadEnv()
const argv = process.argv.slice(2)
const opt = (k: string, d: string) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : d }
// pin one model: 'auto' lets the router switch models between requests, which makes results unreportable
const MODEL = opt('--model', process.env.BENCH_MODEL ?? 'gpt-oss-120b')
const RUNS = Number(opt('--runs', '1'))
const LIMIT = Number(opt('--limit', '0'))
const ONLY = opt('--only', '').split(',').filter(Boolean)
const MODE = opt('--prompt', 'esm+tools') as PromptMode
const CONC = Number(opt('--concurrency', '2'))
const TEMP = Number(opt('--temperature', '0.7')) // ChatService default
const MAX_TURNS = Number(opt('--max-turns', '10'))

const client = new FreeLLMApiClient({ apiKey: requireEnv('FREELLMAPI_KEY'), baseUrl: requireEnv('FREELLMAPI_URL') })

const KNOWN = new Set(['ask_question', 'request_data', 'complete', 'create_service', 'component_template',
  'service_groovy_config', 'list_services', 'list_clients', 'get_client']) // ToolRegistry names

function extractJson(text: string): Record<string, unknown> | null {
  const start = text.indexOf('{')
  if (start < 0) return null
  let depth = 0, inStr = false, esc = false
  for (let i = start; i < text.length; i++) {
    const ch = text[i]
    if (inStr) { if (esc) esc = false; else if (ch === '\\') esc = true; else if (ch === '"') inStr = false; continue }
    if (ch === '"') inStr = true
    else if (ch === '{') depth++
    else if (ch === '}' && --depth === 0) {
      try { return JSON.parse(text.slice(start, i + 1)) } catch { return null }
    }
  }
  return null
}

const sleep = (ms: number) => new Promise(r => setTimeout(r, ms))
let retries = 0

/** Rate limits (429 / "models exhausted") are infrastructure, not model errors: wait and retry. */
async function chatWithRetry(messages: FreeLLMChatMessage[]) {
  for (let attempt = 0; ; attempt++) {
    try {
      return await client.chat(messages, { model: MODEL, temperature: TEMP, jsonMode: true })
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      const transient = /429|rate limit|exhausted|timeout|ECONNRESET|5\d\d/i.test(msg)
      if (!transient || attempt >= 8) throw e
      const hinted = Number(msg.match(/retry in (\d+)\s*s/i)?.[1] ?? 0)
      const wait = Math.max(hinted * 1000, Math.min(15_000 * 2 ** attempt, 180_000))
      retries++
      console.log(`  … ${msg.slice(0, 70)} — retry ${attempt + 1} in ${Math.round(wait / 1000)}s`)
      await sleep(wait)
    }
  }
}

async function runCase(c: BenchCase, run: number): Promise<RunRecord> {
  const rec: RunRecord = { caseId: c.id, run, model: MODEL, calls: [], asks: 0, parseErrors: 0, invalidActions: 0,
    turns: 0, finished: false, latencyMs: 0, tokens: 0, transcript: [] }
  const messages: FreeLLMChatMessage[] = [{ role: 'user', content: firstUserMessage(MODE, c.document) }]
  let answered = false
  let serviceId: number | null = null
  const t0 = Date.now()
  try {
    while (rec.turns < MAX_TURNS && !rec.finished) {
      rec.turns++
      const res = await chatWithRetry(messages)
      rec.tokens += res.usage.totalTokens
      rec.model = res.model
      messages.push({ role: 'assistant', content: res.content })
      const obj = extractJson(res.content)
      const action = typeof obj?.action === 'string' ? obj.action : null
      if (!obj || !action) {
        rec.parseErrors++
        messages.push({ role: 'user', content: 'خروجی باید فقط یک JSON معتبر با ساختار {"action": "...", "params": {...}} باشد.' })
        continue
      }
      const params = (obj.params && typeof obj.params === 'object' ? obj.params : {}) as Record<string, unknown>
      const call: ToolCall = { turn: rec.turns, action, params }
      rec.calls.push(call)

      let reply: string
      if (!KNOWN.has(action)) {
        rec.invalidActions++
        reply = JSON.stringify({ error: true, message: `ابزار نامعتبر: ${action}` })
      } else if (action === 'ask_question') {
        rec.asks++
        if (c.followUp && !answered) { reply = c.followUp; answered = true }
        else if (c.level === 'L4') reply = 'متوجه شدم. در این صورت چیزی نساز و کار را تمام کن.'
        else reply = 'همهٔ اطلاعات لازم در متن درخواست آمده است؛ لطفاً ادامه بده.'
        messages.push({ role: 'user', content: reply })
        continue
      } else if (action === 'complete') {
        rec.finished = true
        break
      } else if (action === 'request_data' || action === 'list_services' || action === 'list_clients') {
        const dt = String(params.data_type ?? action)
        reply = JSON.stringify(dt === 'list_component_templates' ? { groovyTemplates: TEMPLATES, componentTemplates: [], total: TEMPLATES.length }
          : dt === 'list_services' ? { services: serviceId ? [{ id: serviceId, name: c.expected.serviceName }] : [], total: serviceId ? 1 : 0 }
          : dt === 'list_clients' ? { clients: [{ id: 7, name: c.expected.collectionName }], total: 1 }
          : { error: true, message: 'نوع داده نامعتبر است' })
      } else if (action === 'create_service') {
        serviceId = 5000 + Number(c.id.slice(1))
        reply = JSON.stringify({ success: true, serviceId, serviceName: params.serviceName, fullPath: `/${params.collectionBasePath ?? ''}` })
      } else if (action === 'service_groovy_config') {
        reply = JSON.stringify({ success: true, serviceId: params.serviceId, groovyTemplateId: params.groovyTemplateId, message: 'تنظیمات Groovy سرویس ساخته شد' })
      } else {
        reply = JSON.stringify({ success: true })
      }
      // confirmable tools are auto-confirmed; results go back as in ChatService (tool message)
      messages.push({ role: 'user', content: `نتیجهٔ اجرای ${action}:\n${reply}` })
    }
  } catch (e) {
    rec.error = e instanceof Error ? e.message : String(e)
  }
  rec.latencyMs = Date.now() - t0
  rec.transcript = messages.map(m => ({ role: m.role, content: m.content }))
  rec.transcript[0] = { role: 'user', content: '[system prompt omitted]\n\n' + c.document }
  return rec
}

async function pool<T, R>(items: T[], n: number, fn: (x: T) => Promise<R>): Promise<R[]> {
  const out: R[] = new Array(items.length)
  let next = 0
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, async () => {
    while (next < items.length) { const i = next++; out[i] = await fn(items[i]) }
  }))
  return out
}

const dir = join(BENCH_DIR, 'data', 'cases')
let cases: BenchCase[] = readdirSync(dir).filter(f => f.endsWith('.json')).sort()
  .map(f => JSON.parse(readFileSync(join(dir, f), 'utf8')))
if (ONLY.length) cases = cases.filter(c => ONLY.includes(c.id))
if (LIMIT) cases = cases.slice(0, LIMIT)
if (!cases.length) throw new Error('no cases — run `npm run generate` first')

const stamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
const outDir = join(BENCH_DIR, 'results', `${stamp}_${MODEL}_${MODE}`)
mkdirSync(join(outDir, 'transcripts'), { recursive: true })

const jobs = cases.flatMap(c => Array.from({ length: RUNS }, (_, r) => ({ c, r: r + 1 })))
console.log(`${cases.length} cases × ${RUNS} run(s) · model=${MODEL} · prompt=${MODE} · T=${TEMP}`)
let done = 0
const records = await pool(jobs, CONC, async ({ c, r }) => {
  const rec = await runCase(c, r)
  const s = scoreRun(c, rec)
  writeFileSync(join(outDir, 'transcripts', `${c.id}-r${r}.json`), JSON.stringify({ case: c, record: rec, score: s }, null, 2))
  console.log(`[${++done}/${jobs.length}] ${c.id} ${c.level} r${r} ${s.pass ? 'PASS' : 'FAIL'} tpl=${s.templateCorrect ? 'ok' : 'x'} F1=${s.f1.toFixed(2)} turns=${rec.turns}${rec.error ? ' ERR ' + rec.error.slice(0, 80) : ''}`)
  return { c, rec, s }
})
const summary = summarize(records, { model: MODEL, prompt: MODE, temperature: TEMP, runs: RUNS, rateLimitRetries: retries })
writeFileSync(join(outDir, 'summary.json'), JSON.stringify(summary, null, 2))
writeFileSync(join(outDir, 'scores.csv'), ['case,level,run,pass,templateCorrect,precision,recall,f1,serviceMetaCorrect,asks,turns,parseErrors,invalidActions,latencyMs,tokens,error',
  ...records.map(({ c, rec, s }) => [c.id, c.level, rec.run, s.pass, s.templateCorrect, s.precision.toFixed(3), s.recall.toFixed(3), s.f1.toFixed(3),
    s.serviceMetaCorrect, rec.asks, rec.turns, rec.parseErrors, rec.invalidActions, rec.latencyMs, rec.tokens, JSON.stringify(rec.error ?? '')].join(','))].join('\n'))
writeReport(outDir, summary)
console.log(`\nresults: ${outDir}\n` + JSON.stringify(summary.overall, null, 2))
