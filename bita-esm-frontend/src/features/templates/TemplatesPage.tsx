import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useEndpointTemplates, useComponentTemplates, useRouteTemplates, useDeleteEndpointTemplate, useDeleteComponentTemplate, useDeleteRouteTemplate } from './useTemplates'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Plus, Search, Eye, Pencil, Trash2, FileCode2, Box, Route } from 'lucide-react'

type TemplateTab = 'endpoint' | 'component' | 'route'

export default function TemplatesPage() {
  const [activeTab, setActiveTab] = useState<TemplateTab>('endpoint')
  const [search, setSearch] = useState('')

  const tabs = [
    { id: 'endpoint' as TemplateTab, label: 'Endpoint', icon: FileCode2 },
    { id: 'component' as TemplateTab, label: 'Component', icon: Box },
    { id: 'route' as TemplateTab, label: 'Route', icon: Route },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">قالب‌ها</h1>
          <p className="text-muted-foreground">مدیریت قالب‌های Endpoint، Component و Route</p>
        </div>
        <Button asChild>
          <Link to={`/templates/${activeTab}/new`}>
            <Plus className="w-4 h-4 ml-2" />
            قالب جدید
          </Link>
        </Button>
      </div>

      <div className="flex gap-2 border-b">
        {tabs.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-2 border-b-2 transition-colors ${
                activeTab === tab.id
                  ? 'border-primary text-primary'
                  : 'border-transparent hover:text-primary'
              }`}
            >
              <Icon className="w-4 h-4" />
              {tab.label}
            </button>
          )
        })}
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <Input
          placeholder="جستجو..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pr-10"
        />
      </div>

      {activeTab === 'endpoint' && <EndpointTemplateList search={search} />}
      {activeTab === 'component' && <ComponentTemplateList search={search} />}
      {activeTab === 'route' && <RouteTemplateList search={search} />}
    </div>
  )
}

function EndpointTemplateList({ search }: { search: string }) {
  const { data, isLoading } = useEndpointTemplates({ search })
  const deleteTemplate = useDeleteEndpointTemplate()

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این قالب اطمینان دارید؟')) {
      deleteTemplate.mutate(id)
    }
  }

  if (isLoading) return <div className="text-center py-8">در حال بارگذاری...</div>

  if (!data?.content.length) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <FileCode2 className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
          <p className="text-muted-foreground">قالب Endpoint یافت نشد</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {data.content.map((template) => (
        <Card key={template.id}>
          <CardHeader>
            <CardTitle className="text-lg">{template.name}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-2">{template.description || 'بدون توضیحات'}</p>
            <code className="text-xs bg-muted px-2 py-1 rounded block overflow-x-auto max-h-16" dir="ltr">
              {template.camelYaml || '(بدون تعریف)'}
            </code>
            <div className="flex items-center gap-2 mt-4">
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/endpoint/${template.id}`}>
                  <Eye className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/endpoint/${template.id}/edit`}>
                  <Pencil className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" onClick={() => handleDelete(template.id)}>
                <Trash2 className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function ComponentTemplateList({ search }: { search: string }) {
  const { data, isLoading } = useComponentTemplates({ search })
  const deleteTemplate = useDeleteComponentTemplate()

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این قالب اطمینان دارید؟')) {
      deleteTemplate.mutate(id)
    }
  }

  if (isLoading) return <div className="text-center py-8">در حال بارگذاری...</div>

  if (!data?.content.length) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <Box className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
          <p className="text-muted-foreground">قالب Component یافت نشد</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {data.content.map((template) => (
        <Card key={template.id}>
          <CardHeader>
            <CardTitle className="text-lg">{template.name}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-2">{template.description || 'بدون توضیحات'}</p>
            <span className="text-xs bg-secondary px-2 py-1 rounded">{template.componentType}</span>
            <div className="flex items-center gap-2 mt-4">
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/component/${template.id}`}>
                  <Eye className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/component/${template.id}/edit`}>
                  <Pencil className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" onClick={() => handleDelete(template.id)}>
                <Trash2 className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function RouteTemplateList({ search }: { search: string }) {
  const { data, isLoading } = useRouteTemplates({ search })
  const deleteTemplate = useDeleteRouteTemplate()

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این قالب اطمینان دارید؟')) {
      deleteTemplate.mutate(id)
    }
  }

  if (isLoading) return <div className="text-center py-8">در حال بارگذاری...</div>

  if (!data?.content.length) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <Route className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
          <p className="text-muted-foreground">قالب Route یافت نشد</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      {data.content.map((template) => (
        <Card key={template.id}>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="text-lg">{template.name}</CardTitle>
              <span className="text-xs bg-secondary px-2 py-1 rounded">v{template.version}</span>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground mb-2">{template.description || 'بدون توضیحات'}</p>
            <div className="flex items-center gap-2 mt-4">
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/route/${template.id}`}>
                  <Eye className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" asChild>
                <Link to={`/templates/route/${template.id}/edit`}>
                  <Pencil className="w-4 h-4" />
                </Link>
              </Button>
              <Button variant="ghost" size="icon" onClick={() => handleDelete(template.id)}>
                <Trash2 className="w-4 h-4 text-destructive" />
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
