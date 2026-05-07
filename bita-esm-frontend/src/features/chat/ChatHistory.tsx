import { formatDateTime } from '@/lib/utils'
import { useDeleteSession } from './useChat'
import { Button } from '@/components/ui/button'
import { MessageSquare, Trash2 } from 'lucide-react'
import type { ChatSession } from '@/api/chat'

interface ChatHistoryProps {
  sessions: ChatSession[]
  currentSessionId: number | null
  onSelect: (id: number) => void
}

export default function ChatHistory({ sessions, currentSessionId, onSelect }: ChatHistoryProps) {
  const deleteSession = useDeleteSession()

  const handleDelete = (e: React.MouseEvent, id: number) => {
    e.stopPropagation()
    if (confirm('آیا از حذف این گفتگو اطمینان دارید؟')) {
      deleteSession.mutate(id)
    }
  }

  if (!sessions.length) {
    return (
      <div className="flex-1 flex items-center justify-center text-center p-4">
        <div>
          <MessageSquare className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
          <p className="text-sm text-muted-foreground">گفتگویی وجود ندارد</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-auto space-y-1">
      {sessions.map((session) => (
        <div
          key={session.id}
          onClick={() => onSelect(session.id)}
          className={`group flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
            currentSessionId === session.id
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          }`}
        >
          <div className="flex-1 min-w-0">
            <p className="font-medium truncate">
              {session.title || `گفتگو ${session.id}`}
            </p>
            <p className={`text-xs ${currentSessionId === session.id ? 'opacity-80' : 'text-muted-foreground'}`}>
              {formatDateTime(session.createdAt)}
            </p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className={`opacity-0 group-hover:opacity-100 ${
              currentSessionId === session.id ? 'hover:bg-primary-foreground/20' : ''
            }`}
            onClick={(e) => handleDelete(e, session.id)}
          >
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      ))}
    </div>
  )
}
