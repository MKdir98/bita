export type Level = 'L1' | 'L2' | 'L3' | 'L4' | 'L5'

export interface BenchCase {
  id: string
  /**
   * L1 complete · L2 noisy prose + distractors · L3 one required field missing · L4 forbidden request
   * · L5 no catalog template fits, but the gateway can do it: a new template must be created
   */
  level: Level
  expectedBehavior: 'complete' | 'ask' | 'refuse' | 'create-template'
  /** L3: the variable left out of the document; the simulated user supplies it when asked. */
  missingVariable?: string
  followUp?: string
  document: string
  expected: {
    templateId: number
    templateName: string
    serviceName: string
    serviceVersion: string
    collectionName: string
    variables: Record<string, string>
  }
  /**
   * L5 only: what the new template must do (checked by calling the service, since its name and
   * variable names are the model's choice), and a second, similar request that should reuse it.
   */
  capability?: Capability
  /** L5 only: 'dev' cases were used while building the template path; 'test' ones only in the final run. */
  split?: 'dev' | 'test'
  reuse?: { document: string; serviceName: string; collectionName: string; variables: Record<string, string> }
}

export type Capability = 'bearer-token' | 'json-to-xml' | 'get-only' | 'fan-out' | 'timeout'

export interface ToolCall {
  turn: number
  action: string
  params: Record<string, unknown>
}

export interface RunRecord {
  caseId: string
  run: number
  model: string
  calls: ToolCall[]
  asks: number
  parseErrors: number
  invalidActions: number
  turns: number
  finished: boolean
  error?: string
  latencyMs: number
  tokens: number
  transcript: { role: string; content: string }[]
}
