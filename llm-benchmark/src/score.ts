import { templateById } from './catalog.ts'
import type { BenchCase, Level, RunRecord } from './types.ts'

const FORBIDDEN = /jdbc:|mysql|postgres|mongodb|oracle|sqlite|\bh2\b|jpa:|hibernate/i
const NAME_RE = /^[a-z0-9-]+$/

export interface Score {
  pass: boolean
  templateCorrect: boolean
  precision: number
  recall: number
  f1: number
  fieldsCorrect: number
  fieldsExpected: number
  wrongFields: string[]
  missingFields: string[]
  serviceMetaCorrect: boolean
  nameValid: boolean
  askedBeforeConfig: boolean
  unnecessaryAsks: number
  forbiddenEmitted: boolean
  configured: boolean
}

const norm = (v: unknown): string => String(v ?? '')
  .replace(/[۰-۹]/g, d => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)))
  .replace(/[٠-٩]/g, d => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)))
  .trim().toLowerCase().replace(/\s+/g, '').replace(/\/+$/, '')

function valueMatches(name: string, expected: string, got: unknown): boolean {
  if (got === undefined || got === null || got === '') return false
  const e = norm(expected), g = norm(got)
  if (e === g) return true
  // SECRET: ESM auth guidance tells the model to prefer placeholders over real secrets
  if (name === 'backendPassword' && /^(\$\{.+\}|\{\{.+\}\}|<.+>|\*+|secret:.+)$/.test(g)) return true
  return false
}

export function scoreRun(c: BenchCase, rec: RunRecord): Score {
  const configs = rec.calls.filter(x => x.action === 'service_groovy_config')
  const cfg = configs.at(-1)
  const svc = rec.calls.filter(x => x.action === 'create_service').at(-1)
  const tpl = templateById(c.expected.templateId)!
  const exp = c.expected.variables
  const got = (cfg?.params.variableValues ?? {}) as Record<string, unknown>

  let tp = 0
  const wrong: string[] = [], missing: string[] = []
  for (const [k, v] of Object.entries(exp)) {
    if (!(k in got) || got[k] === '' || got[k] == null) missing.push(k)
    else if (valueMatches(k, v, got[k])) tp++
    else wrong.push(k)
  }
  // extra keys the template does not define count against precision
  const predicted = Object.keys(got).length
  const precision = predicted ? tp / predicted : 0
  const recall = tp / Object.keys(exp).length
  const f1 = precision + recall ? (2 * precision * recall) / (precision + recall) : 0

  const tplId = Number(cfg?.params.groovyTemplateId)
  const templateCorrect = !!cfg && (tplId === tpl.id || cfg.params.groovyTemplateName === tpl.name)
  const serviceMetaCorrect = !!svc && norm(svc.params.serviceName) === c.expected.serviceName
    && norm(svc.params.serviceVersion) === c.expected.serviceVersion
    && (norm(svc.params.collectionName) === c.expected.collectionName || svc.params.collectionId !== undefined)
  const nameValid = !!svc && NAME_RE.test(String(svc.params.serviceName ?? ''))
  const firstCfgTurn = cfg ? rec.calls.find(x => x.action === 'service_groovy_config')!.turn : Infinity
  const askedBeforeConfig = rec.calls.some(x => x.action === 'ask_question' && x.turn < firstCfgTurn)
  const forbiddenEmitted = rec.calls.some(x => ['create_service', 'service_groovy_config', 'component_template'].includes(x.action)
    && FORBIDDEN.test(JSON.stringify(x.params)))
  const exact = templateCorrect && wrong.length === 0 && missing.length === 0 && serviceMetaCorrect

  let pass: boolean
  switch (c.expectedBehavior) {
    case 'complete': pass = exact && !rec.error; break
    case 'ask': pass = exact && askedBeforeConfig && !rec.error; break
    case 'refuse': pass = !forbiddenEmitted && rec.calls.some(x => x.action === 'ask_question') && !rec.error; break
  }
  return {
    pass, templateCorrect, precision, recall, f1, fieldsCorrect: tp, fieldsExpected: Object.keys(exp).length,
    wrongFields: wrong, missingFields: missing, serviceMetaCorrect, nameValid, askedBeforeConfig,
    unnecessaryAsks: c.expectedBehavior === 'complete' ? rec.asks : 0, forbiddenEmitted, configured: !!cfg,
  }
}

type Row = { c: BenchCase; rec: RunRecord; s: Score }
const mean = (xs: number[]) => xs.length ? xs.reduce((a, b) => a + b, 0) / xs.length : 0
const pct = (xs: boolean[]) => xs.length ? xs.filter(Boolean).length / xs.length : 0

function agg(rows: Row[]) {
  const defin = rows.filter(r => r.c.expectedBehavior !== 'refuse')
  return {
    n: rows.length,
    passRate: pct(rows.map(r => r.s.pass)),
    templateAccuracy: pct(defin.map(r => r.s.templateCorrect)),
    fieldPrecision: mean(defin.map(r => r.s.precision)),
    fieldRecall: mean(defin.map(r => r.s.recall)),
    fieldF1: mean(defin.map(r => r.s.f1)),
    serviceMetaAccuracy: pct(defin.map(r => r.s.serviceMetaCorrect)),
    nameValidRate: pct(defin.filter(r => r.s.configured || r.rec.calls.length).map(r => r.s.nameValid)),
    jsonParseErrorsPerCase: mean(rows.map(r => r.rec.parseErrors)),
    invalidActionsPerCase: mean(rows.map(r => r.rec.invalidActions)),
    unnecessaryAsksPerCase: mean(rows.filter(r => r.c.expectedBehavior === 'complete').map(r => r.s.unnecessaryAsks)),
    avgTurns: mean(rows.map(r => r.rec.turns)),
    avgLatencySec: mean(rows.map(r => r.rec.latencyMs / 1000)),
    avgTokens: mean(rows.map(r => r.rec.tokens)),
    errors: rows.filter(r => r.rec.error).length,
  }
}

export function summarize(allRows: Row[], meta: Record<string, unknown>) {
  // runs that died on infrastructure errors are reported separately, not scored as model failures
  const rows = allRows.filter(r => !r.rec.error)
  const infraErrors = allRows.filter(r => r.rec.error).map(r => ({ id: r.c.id, run: r.rec.run, error: r.rec.error }))
  const levels: Level[] = ['L1', 'L2', 'L3', 'L4']
  const byCase = new Map<string, boolean[]>()
  for (const r of rows) byCase.set(r.c.id, [...(byCase.get(r.c.id) ?? []), r.s.pass])
  const fieldErrors: Record<string, number> = {}
  for (const r of rows) for (const f of [...r.s.wrongFields, ...r.s.missingFields]) fieldErrors[f] = (fieldErrors[f] ?? 0) + 1
  return {
    meta: { ...meta, models: [...new Set(rows.map(r => r.rec.model))], date: new Date().toISOString() },
    infraErrors,
    overall: agg(rows),
    byLevel: Object.fromEntries(levels.map(l => [l, agg(rows.filter(r => r.c.level === l))])),
    byTemplate: Object.fromEntries([...new Set(rows.map(r => r.c.expected.templateName))].map(t => [t, agg(rows.filter(r => r.c.expected.templateName === t))])),
    passAtK: pct([...byCase.values()].map(v => v.some(Boolean))),
    passAllRuns: pct([...byCase.values()].map(v => v.every(Boolean))),
    fieldErrors,
    failedCases: rows.filter(r => !r.s.pass).map(r => ({ id: r.c.id, level: r.c.level, run: r.rec.run, wrong: r.s.wrongFields, missing: r.s.missingFields, templateCorrect: r.s.templateCorrect, error: r.rec.error })),
  }
}
