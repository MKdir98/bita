import apiClient from './client'

export interface ChatSession {
  id: number
  userId: number
  title?: string
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  id: number
  sessionId?: number
  role: 'USER' | 'ASSISTANT' | 'TOOL'
  content: string
  toolName?: string
  toolArgs?: object
  toolResult?: string
  toolCalls?: ToolExecution[]
  pendingToolExecution?: ToolExecution
  createdAt: string
}

export interface ToolExecution {
  id: string // toolCallId - used for confirm API
  toolName: string
  arguments: object
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXECUTED' | 'FAILED'
  requiresConfirmation?: boolean
  result?: object
  errorMessage?: string
}

export interface SendMessageResponse {
  id: number
  role: string
  content: string
  toolCalls?: ToolExecution[]
  pendingToolExecution?: ToolExecution
  createdAt: string
}

export const chatApi = {
  listSessions: async (): Promise<ChatSession[]> => {
    const response = await apiClient.get('/api/v1/chat/sessions')
    // Backend returns paginated response, extract content array
    return response.data.content || response.data
  },

  createSession: async (title?: string): Promise<ChatSession> => {
    const response = await apiClient.post('/api/v1/chat/sessions', { title })
    return response.data
  },

  getSession: async (id: number): Promise<ChatSession> => {
    const response = await apiClient.get(`/api/v1/chat/sessions/${id}`)
    return response.data
  },

  deleteSession: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/chat/sessions/${id}`)
  },

  getHistory: async (sessionId: number): Promise<ChatMessage[]> => {
    const response = await apiClient.get(`/api/v1/chat/sessions/${sessionId}/history`)
    // Backend returns session object with messages array inside
    return response.data.messages || response.data
  },

  sendMessage: async (sessionId: number, content: string): Promise<SendMessageResponse> => {
    const response = await apiClient.post(`/api/v1/chat/sessions/${sessionId}/messages`, {
      content,
    })
    return response.data
  },

  confirmToolExecution: async (
    sessionId: number,
    toolCallId: string,
    confirmed: boolean
  ): Promise<SendMessageResponse> => {
    const response = await apiClient.post(`/api/v1/chat/sessions/${sessionId}/confirm`, {
      toolCallId,
      confirmed,
    })
    return response.data
  },
}
