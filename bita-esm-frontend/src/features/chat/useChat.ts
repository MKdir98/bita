import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { chatApi, ChatMessage, ToolExecution } from '@/api/chat'
import { useToast } from '@/hooks/use-toast'
import { useState } from 'react'

export function useChatSessions() {
  return useQuery({
    queryKey: ['chatSessions'],
    queryFn: () => chatApi.listSessions(),
  })
}

export function useChatHistory(sessionId: number | null) {
  return useQuery({
    queryKey: ['chatHistory', sessionId],
    queryFn: () => chatApi.getHistory(sessionId!),
    enabled: !!sessionId,
  })
}

export function useCreateSession() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (title?: string) => chatApi.createSession(title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatSessions'] })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد گفتگو' })
    },
  })
}

export function useDeleteSession() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => chatApi.deleteSession(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatSessions'] })
      toast({ title: 'گفتگو حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف گفتگو' })
    },
  })
}

export function useSendMessage(sessionId: number | null) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (content: string) => chatApi.sendMessage(sessionId!, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatHistory', sessionId] })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ارسال پیام' })
    },
  })
}

export function useConfirmToolExecution(sessionId: number | null) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ toolCallId, confirmed }: { toolCallId: string; confirmed: boolean }) =>
      chatApi.confirmToolExecution(sessionId!, toolCallId, confirmed),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['chatHistory', sessionId] })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در پردازش' })
    },
  })
}

// Local state hook for chat UI
export function useChatState() {
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null)
  const [pendingExecution, setPendingExecution] = useState<ToolExecution | null>(null)

  return {
    currentSessionId,
    setCurrentSessionId,
    pendingExecution,
    setPendingExecution,
  }
}
