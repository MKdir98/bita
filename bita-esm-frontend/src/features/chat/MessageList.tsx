import { useEffect, useRef } from 'react'
import { User, Bot, Wrench } from 'lucide-react'
import MarkdownRenderer from './MarkdownRenderer'
import type { ChatMessage } from '@/api/chat'

interface MessageListProps {
  messages: ChatMessage[]
  isLoading: boolean
}

export default function MessageList({ messages, isLoading }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">در حال بارگذاری...</p>
        </div>
      </div>
    )
  }

  if (!messages.length) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center max-w-md">
          <Bot className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
          <h3 className="text-lg font-medium mb-2">سلام! چطور می‌توانم کمک کنم؟</h3>
          <p className="text-sm text-muted-foreground">
            می‌توانید از من بخواهید سرویس، مسیر یا قالب جدید ایجاد کنم یا اطلاعات موجود را نمایش دهم.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {messages.map((message) => (
        <MessageBubble key={message.id} message={message} />
      ))}
      <div ref={bottomRef} />
    </div>
  )
}

function MessageBubble({ message }: { message: ChatMessage }) {
  const isUser = message.role === 'USER'
  const isTool = message.role === 'TOOL'

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : ''}`}>
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
          isUser
            ? 'bg-primary text-primary-foreground'
            : isTool
            ? 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
            : 'bg-secondary'
        }`}
      >
        {isUser ? <User className="w-4 h-4" /> : isTool ? <Wrench className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
      </div>
      <div
        className={`max-w-[80%] rounded-lg px-4 py-3 ${
          isUser
            ? 'bg-primary text-primary-foreground'
            : isTool
            ? 'bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800'
            : 'bg-muted'
        }`}
      >
        {isTool && message.toolName && (
          <div className="text-xs font-medium mb-1 opacity-70">
            ابزار: {message.toolName}
          </div>
        )}
        {isUser ? (
          <p className="whitespace-pre-wrap">{message.content}</p>
        ) : (
          <MarkdownRenderer content={message.content} />
        )}
      </div>
    </div>
  )
}
