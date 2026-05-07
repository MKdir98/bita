import { useEffect, useState } from 'react'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface DynamicConfigFormProps {
  schema: any
  value: object
  onChange: (value: object) => void
}

export default function DynamicConfigForm({ schema, value, onChange }: DynamicConfigFormProps) {
  const [localValue, setLocalValue] = useState<Record<string, any>>(value as Record<string, any>)

  useEffect(() => {
    setLocalValue(value as Record<string, any>)
  }, [value])

  const handleChange = (key: string, val: any) => {
    const newValue = { ...localValue, [key]: val }
    setLocalValue(newValue)
    onChange(newValue)
  }

  if (!schema.properties) {
    return (
      <div className="p-4 border rounded-lg">
        <p className="text-sm text-muted-foreground">پیکربندی اضافی نیاز نیست</p>
      </div>
    )
  }

  const renderField = (key: string, prop: any) => {
    const isRequired = schema.required?.includes(key)
    const fieldValue = localValue[key] ?? ''

    switch (prop.type) {
      case 'string':
        if (prop.enum) {
          return (
            <select
              className="w-full h-10 px-3 rounded-md border border-input bg-background"
              value={fieldValue}
              onChange={(e) => handleChange(key, e.target.value)}
            >
              <option value="">انتخاب کنید...</option>
              {prop.enum.map((opt: string) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          )
        }
        return (
          <Input
            value={fieldValue}
            onChange={(e) => handleChange(key, e.target.value)}
            placeholder={prop.default || ''}
            dir={prop.format === 'uri' || key.includes('url') || key.includes('path') ? 'ltr' : 'rtl'}
          />
        )

      case 'integer':
      case 'number':
        return (
          <Input
            type="number"
            value={fieldValue}
            onChange={(e) => handleChange(key, Number(e.target.value))}
            min={prop.minimum}
            max={prop.maximum}
          />
        )

      case 'boolean':
        return (
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={fieldValue || false}
              onChange={(e) => handleChange(key, e.target.checked)}
              className="w-4 h-4"
            />
            <span>{prop.description || key}</span>
          </label>
        )

      case 'array':
        return (
          <Input
            value={Array.isArray(fieldValue) ? fieldValue.join(', ') : fieldValue}
            onChange={(e) =>
              handleChange(
                key,
                e.target.value.split(',').map((s) => s.trim())
              )
            }
            placeholder="مقادیر را با کاما جدا کنید"
          />
        )

      default:
        return (
          <textarea
            className="w-full min-h-[100px] px-3 py-2 rounded-md border border-input bg-background"
            value={typeof fieldValue === 'object' ? JSON.stringify(fieldValue, null, 2) : fieldValue}
            onChange={(e) => {
              try {
                handleChange(key, JSON.parse(e.target.value))
              } catch {
                handleChange(key, e.target.value)
              }
            }}
            dir="ltr"
          />
        )
    }
  }

  return (
    <div className="space-y-4 p-4 border rounded-lg">
      {Object.entries(schema.properties).map(([key, prop]: [string, any]) => (
        <div key={key} className="space-y-2">
          <Label>
            {prop.title || key}
            {schema.required?.includes(key) && <span className="text-destructive mr-1">*</span>}
          </Label>
          {renderField(key, prop)}
          {prop.description && (
            <p className="text-xs text-muted-foreground">{prop.description}</p>
          )}
        </div>
      ))}
    </div>
  )
}
