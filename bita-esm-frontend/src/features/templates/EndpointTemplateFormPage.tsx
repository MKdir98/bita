import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useEndpointTemplate, useCreateEndpointTemplate, useUpdateEndpointTemplate } from './useTemplates'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import JsonSchemaEditor from './JsonSchemaEditor'

const schema = z.object({
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  description: z.string().optional(),
  camelYaml: z.string().min(1, 'تعریف Camel YAML الزامی است'),
})

type FormData = z.infer<typeof schema>

export default function EndpointTemplateFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id && id !== 'new'
  const templateId = Number(id)

  const [configSchema, setConfigSchema] = useState<object>({})

  const { data: template, isLoading } = useEndpointTemplate(templateId)
  const createTemplate = useCreateEndpointTemplate()
  const updateTemplate = useUpdateEndpointTemplate(templateId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  useEffect(() => {
    if (template) {
      reset({
        name: template.name,
        description: template.description || '',
        camelYaml: template.camelYaml || '',
      })
      setConfigSchema(template.configSchema || {})
    }
  }, [template, reset])

  const onSubmit = async (data: FormData) => {
    const payload = { ...data, configSchema }
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
    <div className="max-w-2xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش قالب Endpoint' : 'قالب Endpoint جدید'}</CardTitle>
          <CardDescription>الگوی URI برای تعریف نقاط اتصال</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">نام *</Label>
              <Input id="name" {...register('name')} />
              {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="camelYaml">تعریف Camel YAML *</Label>
              <textarea
                id="camelYaml"
                dir="ltr"
                className="flex min-h-[120px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm font-mono"
                placeholder="- from:&#10;    uri: &quot;cxf:bean:{{serviceName}}?wsdlURL={{wsdlUrl}}&quot;&#10;    steps: []"
                {...register('camelYaml')}
              />
              {errors.camelYaml && <p className="text-sm text-destructive">{errors.camelYaml.message}</p>}
              <p className="text-xs text-muted-foreground">
                از {'{{placeholder}}'} برای پارامترهای قابل تنظیم استفاده کنید
              </p>
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
