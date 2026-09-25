/**
 * Builds the prompt exactly the way ESM does: the real DEFAULT_SYSTEM_PROMPT and the
 * BITA auth section are read from the Java sources, and prepended to the first user message
 * (ChatService.java: "# دستورالعمل‌های سیستم ... # درخواست کاربر").
 */
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { REPO_DIR } from './env.ts'

const JAVA = join(REPO_DIR, 'bita-esm-backend/src/main/java/ir/bita/esm/llm/service')

function textBlock(file: string, after: string): string {
  const src = readFileSync(join(JAVA, file), 'utf8')
  const start = src.indexOf('"""', src.indexOf(after)) + 3
  const end = src.indexOf('"""', start)
  const lines = src.slice(start, end).replace(/^\n/, '').split('\n')
  const indent = Math.min(...lines.filter(l => l.trim()).map(l => l.match(/^ */)![0].length))
  return lines.map(l => l.slice(indent)).join('\n').replace(/\\"/g, '"').trimEnd()
}

/**
 * The ESM prompt still documents endpoint/route template+instance actions whose tool classes
 * were removed in the Groovy migration. `esm+tools` appends the current tool contract so the
 * benchmark measures the model, not the stale prompt; `esm` sends the prompt verbatim.
 */
const CURRENT_TOOLS = `
## ابزارهای فعلی سامانه (به‌روز — بر بخش‌های قبلی مقدم است)

actionهای endpoint_template، route_template، endpoint_instance، component_instance و route_instance دیگر وجود ندارند. هر سرویس یک GroovyTemplate دارد و فقط مقدار متغیرهای آن پر می‌شود.

- request_data با data_type=list_component_templates: فهرست GroovyTemplateها (groovyTemplates) و متغیرهای هر کدام را برمی‌گرداند
- create_service: {"collectionName": "...", "collectionBasePath": "...", "serviceName": "...", "serviceVersion": "...", "description": "..."} ← serviceId برمی‌گرداند
- service_groovy_config: {"serviceId": <عدد>, "groovyTemplateId": <عدد>, "variableValues": {"<نام متغیر>": "<مقدار>", ...}}
- ask_question و complete مثل قبل

روند: ۱) request_data برای دیدن قالب‌ها ۲) create_service ۳) service_groovy_config با همهٔ متغیرهای الزامی قالب ۴) complete
`

export type PromptMode = 'esm' | 'esm+tools'

export function systemPrompt(mode: PromptMode): string {
  const base = textBlock('ChatService.java', 'DEFAULT_SYSTEM_PROMPT') + '\n' + textBlock('AuthGuidanceService.java', 'promptSection')
  return mode === 'esm' ? base : base + '\n' + CURRENT_TOOLS
}

export function firstUserMessage(mode: PromptMode, document: string): string {
  return '# دستورالعمل‌های سیستم\n\n' + systemPrompt(mode) + '\n\n---\n\n# درخواست کاربر\n\n' + document
}
