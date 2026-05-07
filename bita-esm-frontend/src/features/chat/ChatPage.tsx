import { useState, useEffect } from 'react'
import { useChatSessions, useChatHistory, useCreateSession, useSendMessage, useConfirmToolExecution } from './useChat'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Plus, MessageSquare } from 'lucide-react'
import ChatHistory from './ChatHistory'
import MessageList from './MessageList'
import MessageInput from './MessageInput'
import ToolConfirmDialog from './ToolConfirmDialog'
import type { ToolExecution } from '@/api/chat'

export default function ChatPage() {
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null)
  const [pendingExecution, setPendingExecution] = useState<ToolExecution | null>(null)

  const { data: sessions } = useChatSessions()
  const { data: messages, isLoading: messagesLoading } = useChatHistory(currentSessionId)
  const createSession = useCreateSession()
  const sendMessage = useSendMessage(currentSessionId)
  const confirmExecution = useConfirmToolExecution(currentSessionId)

  useEffect(() => {
    if (sessions?.length && !currentSessionId) {
      setCurrentSessionId(sessions[0].id)
    }
  }, [sessions, currentSessionId])

  // Restore pending execution when loading history (e.g. after page refresh)
  useEffect(() => {
    if (!currentSessionId) {
      setPendingExecution(null)
      return
    }
    if (messages?.length) {
      const lastWithPending = [...messages].reverse().find((m) => m.pendingToolExecution?.status === 'PENDING')
      setPendingExecution(lastWithPending?.pendingToolExecution ?? null)
    } else {
      setPendingExecution(null)
    }
  }, [currentSessionId, messages])

  const handleNewSession = async () => {
    const session = await createSession.mutateAsync()
    setCurrentSessionId(session.id)
  }

  const handleSendMessage = async (content: string) => {
    const response = await sendMessage.mutateAsync(content)
    if (response.pendingToolExecution) {
      setPendingExecution(response.pendingToolExecution)
    }
  }

  const handleConfirmTool = async (confirmed: boolean) => {
    if (pendingExecution) {
      await confirmExecution.mutateAsync({
        toolCallId: pendingExecution.id,
        confirmed,
      })
      setPendingExecution(null)
    }
  }

  return (
    <div className="h-[calc(100vh-8rem)] flex gap-4">
      {/* Sidebar */}
      <div className="w-64 flex flex-col">
        <Button onClick={handleNewSession} className="mb-4" disabled={createSession.isPending}>
          <Plus className="w-4 h-4 ml-2" />
          گفتگوی جدید
        </Button>
        <ChatHistory
          sessions={sessions || []}
          currentSessionId={currentSessionId}
          onSelect={setCurrentSessionId}
        />
      </div>

      {/* Main Chat Area */}
      <Card className="flex-1 flex flex-col">
        {!currentSessionId ? (
          <div className="flex-1 flex items-center justify-center">
            <div className="text-center">
              <MessageSquare className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
              <p className="text-muted-foreground">یک گفتگو انتخاب کنید یا گفتگوی جدید ایجاد کنید</p>
            </div>
          </div>
        ) : (
          <>
            <div className="flex-1 overflow-auto p-4">
              <MessageList messages={messages || []} isLoading={messagesLoading} />
            </div>
            <div className="p-4 border-t">
              <MessageInput
                onSend={handleSendMessage}
                disabled={sendMessage.isPending || !!pendingExecution}
                isLoading={sendMessage.isPending}
              />
            </div>
          </>
        )}
      </Card>

      {/* Tool Confirmation Dialog */}
      <ToolConfirmDialog
        execution={pendingExecution}
        onConfirm={() => handleConfirmTool(true)}
        onReject={() => handleConfirmTool(false)}
        isLoading={confirmExecution.isPending}
      />
    </div>
  )
}
