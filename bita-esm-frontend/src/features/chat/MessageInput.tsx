import { useState, useRef, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Send, Loader2 } from 'lucide-react'

interface MessageInputProps {
  onSend: (content: string) => void
  disabled: boolean
  isLoading: boolean
}

export default function MessageInput({ onSend, disabled, isLoading }: MessageInputProps) {
  const [content, setContent] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px'
    }
  }, [content])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (content.trim() && !disabled) {
      onSend(content.trim())
      setContent('')
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <textarea
        ref={textareaRef}
        value={content}
        onChange={(e) => setContent(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder="پیام خود را بنویسید... (Shift+Enter برای خط جدید)"
        disabled={disabled}
        className="flex-1 min-h-[44px] max-h-[200px] px-4 py-3 rounded-lg border border-input bg-background resize-none focus:outline-none focus:ring-2 focus:ring-ring"
        rows={1}
      />
      <Button type="submit" disabled={!content.trim() || disabled} className="self-end">
        {isLoading ? (
          <Loader2 className="w-4 h-4 animate-spin" />
        ) : (
          <Send className="w-4 h-4" />
        )}
      </Button>
    </form>
  )
}
