import { useState, useEffect } from 'react'
import { Label } from '@/components/ui/label'

interface JsonSchemaEditorProps {
  value: object | undefined
  onChange: (value: object) => void
}

export default function JsonSchemaEditor({ value, onChange }: JsonSchemaEditorProps) {
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setText(value ? JSON.stringify(value, null, 2) : '')
  }, [value])

  const handleChange = (newText: string) => {
    setText(newText)
    setError(null)

    if (!newText.trim()) {
      onChange({})
      return
    }

    try {
      const parsed = JSON.parse(newText)
      onChange(parsed)
    } catch (e) {
      setError('JSON نامعتبر است')
    }
  }

  return (
    <div className="space-y-2">
      <Label>JSON Schema (پیکربندی)</Label>
      <textarea
        className={`w-full min-h-[200px] px-3 py-2 rounded-md border bg-background font-mono text-sm ${
          error ? 'border-destructive' : 'border-input'
        }`}
        value={text}
        onChange={(e) => handleChange(e.target.value)}
        placeholder={`{
  "type": "object",
  "properties": {
    "timeout": {
      "type": "integer",
      "title": "Timeout (ms)",
      "default": 30000
    }
  }
}`}
        dir="ltr"
      />
      {error && <p className="text-sm text-destructive">{error}</p>}
      <p className="text-xs text-muted-foreground">
        JSON Schema برای تعریف پارامترهای قابل پیکربندی استفاده می‌شود
      </p>
    </div>
  )
}
