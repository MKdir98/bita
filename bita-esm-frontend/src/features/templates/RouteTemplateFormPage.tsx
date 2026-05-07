import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useRouteTemplate, useCreateRouteTemplate, useUpdateRouteTemplate, useEndpointTemplates, useComponentTemplates } from './useTemplates'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import JsonSchemaEditor from './JsonSchemaEditor'

const schema = z.object({
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  description: z.string().optional(),
  version: z.string().regex(/^\d+\.\d+\.\d+$/, 'نسخه باید به فرمت x.y.z باشد'),
  inputEndpointTemplateId: z.number({ required_error: 'انتخاب Endpoint ورودی الزامی است' }),
  outputEndpointTemplateId: z.number({ required_error: 'انتخاب Endpoint خروجی الزامی است' }),
})

type FormData = z.infer<typeof schema>

export default function RouteTemplateFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id && id !== 'new'
  const templateId = Number(id)

  const [configSchema, setConfigSchema] = useState<object>({})
  const [selectedComponents, setSelectedComponents] = useState<number[]>([])

  const { data: template, isLoading } = useRouteTemplate(templateId)
  const { data: endpointTemplates } = useEndpointTemplates({ size: 100 })
  const { data: componentTemplates } = useComponentTemplates({ size: 100 })
  const createTemplate = useCreateRouteTemplate()
  const updateTemplate = useUpdateRouteTemplate(templateId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      version: '1.0.0',
    },
  })

  useEffect(() => {
    if (template) {
      reset({
        name: template.name,
        description: template.description || '',
        version: template.version,
        inputEndpointTemplateId: template.inputEndpointTemplateId,
        outputEndpointTemplateId: template.outputEndpointTemplateId,
      })
      setConfigSchema(template.configSchema || {})
      setSelectedComponents(template.componentIds || [])
    }
  }, [template, reset])

  const toggleComponent = (id: number) => {
    setSelectedComponents((prev) =>
      prev.includes(id) ? prev.filter((c) => c !== id) : [...prev, id]
    )
  }

  const onSubmit = async (data: FormData) => {
    const payload = { ...data, configSchema, componentIds: selectedComponents }
    if (isEdit) {
      await updateTemplate.mutateAsync(payload)
    } else {
      await createTemplate.mutateAsync(payload)
    }
    navigate('/templates')
  }

  if (isEdit && isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-3xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش قالب Route' : 'قالب Route جدید'}</CardTitle>
          <CardDescription>تعریف الگوی مسیر پردازش</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="name">نام *</Label>
                <Input id="name" {...register('name')} />
                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="version">نسخه *</Label>
                <Input id="version" dir="ltr" {...register('version')} />
                {errors.version && <p className="text-sm text-destructive">{errors.version.message}</p>}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>Endpoint ورودی *</Label>
                <select
                  className="w-full h-10 px-3 rounded-md border border-input bg-background"
                  {...register('inputEndpointTemplateId', { valueAsNumber: true })}
                >
                  <option value="">انتخاب کنید...</option>
                  {endpointTemplates?.content.map((ep) => (
                    <option key={ep.id} value={ep.id}>
                      {ep.name}
                    </option>
                  ))}
                </select>
                {errors.inputEndpointTemplateId && (
                  <p className="text-sm text-destructive">{errors.inputEndpointTemplateId.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label>Endpoint خروجی *</Label>
                <select
                  className="w-full h-10 px-3 rounded-md border border-input bg-background"
                  {...register('outputEndpointTemplateId', { valueAsNumber: true })}
                >
                  <option value="">انتخاب کنید...</option>
                  {endpointTemplates?.content.map((ep) => (
                    <option key={ep.id} value={ep.id}>
                      {ep.name}
                    </option>
                  ))}
                </select>
                {errors.outputEndpointTemplateId && (
                  <p className="text-sm text-destructive">{errors.outputEndpointTemplateId.message}</p>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <Label>کامپوننت‌های میانی</Label>
              <div className="border rounded-lg p-4 max-h-48 overflow-auto">
                {!componentTemplates?.content.length ? (
                  <p className="text-sm text-muted-foreground">کامپوننتی تعریف نشده</p>
                ) : (
                  <div className="space-y-2">
                    {componentTemplates.content.map((comp) => (
                      <label key={comp.id} className="flex items-center gap-2 cursor-pointer">
                        <input
                          type="checkbox"
                          checked={selectedComponents.includes(comp.id)}
                          onChange={() => toggleComponent(comp.id)}
                          className="w-4 h-4"
                        />
                        <span>{comp.name}</span>
                        <span className="text-xs bg-secondary px-2 py-0.5 rounded">{comp.componentType}</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <JsonSchemaEditor value={configSchema} onChange={setConfigSchema} />

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => navigate('/templates')}>
                انصراف
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'در حال ذخیره...' : isEdit ? 'ذخیره تغییرات' : 'ایجاد قالب'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
