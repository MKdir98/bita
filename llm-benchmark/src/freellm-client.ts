import OpenAI from 'openai'

export interface FreeLLMClientOptionsBase {
  apiKey: string
  baseUrl?: string
}
export type FreeLLMApiClientOptions = FreeLLMClientOptionsBase

export interface FreeLLMChatMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface FreeLLMChatOptions {
  model?: string
  temperature?: number
  maxTokens?: number
  /** Force a JSON object reply, as ESM does (response_format=json_object). */
  jsonMode?: boolean
}

export interface FreeLLMChatResponse {
  content: string
  model: string
  usage: { promptTokens: number; completionTokens: number; totalTokens: number }
}

export class FreeLLMApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'FreeLLMApiError'
  }
}

export class FreeLLMApiClient {
  private readonly client: OpenAI

  constructor(options: FreeLLMApiClientOptions) {
    this.client = new OpenAI({
      baseURL: options.baseUrl ?? 'http://localhost:3001/v1',
      apiKey: options.apiKey,
      timeout: 180_000,
      maxRetries: 0, // retries with backoff are handled by the caller
    })
  }

  async chat(messages: FreeLLMChatMessage[], options: FreeLLMChatOptions = {}): Promise<FreeLLMChatResponse> {
    let response: OpenAI.Chat.ChatCompletion
    try {
      response = await this.client.chat.completions.create({
        model: options.model ?? 'auto',
        messages,
        temperature: options.temperature,
        max_tokens: options.maxTokens,
        stream: false,
        ...(options.jsonMode ? { response_format: { type: 'json_object' as const } } : {}),
      })
    } catch (error) {
      throw new FreeLLMApiError(
        `FreeLLMAPI request failed: ${error instanceof Error ? error.message : String(error)}`
      )
    }

    const choice = response.choices[0]
    if (!choice?.message?.content) {
      throw new FreeLLMApiError('FreeLLMAPI returned no content')
    }

    return {
      content: choice.message.content,
      model: response.model,
      usage: {
        promptTokens: response.usage?.prompt_tokens ?? 0,
        completionTokens: response.usage?.completion_tokens ?? 0,
        totalTokens: response.usage?.total_tokens ?? 0,
      },
    }
  }
}
