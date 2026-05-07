import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/api/client'
import { Input } from '@/components/ui/input'
import { Search, FileCode2 } from 'lucide-react'

interface TemplateSelectorProps {
  onSelect: (template: any) => void
  selected: any
}

export default function TemplateSelector({ onSelect, selected }: TemplateSelectorProps) {
  const [search, setSearch] = useState('')

  const { data: templates, isLoading } = useQuery({
    queryKey: ['routeTemplates', search],
    queryFn: async () => {
      const response = await apiClient.get('/api/v1/route-templates', {
        params: { search, size: 20 },
      })
      return response.data
    },
  })

  return (
    <div className="space-y-4">
      <div className="relative">
        <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <Input
          placeholder="جستجوی قالب..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pr-10"
        />
      </div>

      <div className="border rounded-lg max-h-64 overflow-auto">
        {isLoading ? (
          <div className="p-4 text-center text-muted-foreground">در حال بارگذاری...</div>
        ) : !templates?.content?.length ? (
          <div className="p-4 text-center text-muted-foreground">
            <FileCode2 className="w-8 h-8 mx-auto mb-2" />
            قالبی یافت نشد
          </div>
        ) : (
          <div className="divide-y">
            {templates.content.map((template: any) => (
              <button
                key={template.id}
                type="button"
                onClick={() => onSelect(template)}
                className={`w-full p-4 text-right hover:bg-muted/50 transition-colors ${
                  selected?.id === template.id ? 'bg-primary/10 border-r-2 border-primary' : ''
                }`}
              >
                <div className="flex items-start justify-between">
                  <div>
                    <p className="font-medium">{template.name}</p>
                    <p className="text-sm text-muted-foreground mt-1">
                      {template.description || 'بدون توضیحات'}
                    </p>
                  </div>
                  <span className="text-xs bg-secondary px-2 py-1 rounded">v{template.version}</span>
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
