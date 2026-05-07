import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useComponentTemplate, useCreateComponentTemplate, useUpdateComponentTemplate } from './useTemplates'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import JsonSchemaEditor from './JsonSchemaEditor'

const componentTypes = [
  'TRANSFORM',
  'VALIDATE',
  'ENRICH',
  'FILTER',
  'SPLIT',
  'AGGREGATE',
  'LOG',
  'CUSTOM',
]

const schema = z.object({
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  description: z.string().optional(),
  componentType: z.string().min(1, 'نوع Component الزامی است'),
})

type FormData = z.infer<typeof schema>

export default function ComponentTemplateFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id && id !== 'new'
  const templateId = Number(id)

  const [configSchema, setConfigSchema] = useState<object>({})

  const { data: template, isLoading } = useComponentTemplate(templateId)
  const createTemplate = useCreateComponentTemplate()
  const updateTemplate = useUpdateComponentTemplate(templateId)

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
        componentType: template.componentType,
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
          <CardTitle>{isEdit ? 'ویرایش قالب Component' : 'قالب Component جدید'}</CardTitle>
          <CardDescription>کامپوننت‌های پردازشی در مسیر</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="name">نام *</Label>
                <Input id="name" {...register('name')} />
                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
              </div>

              <div className="space-y-2">
                <Label>نوع Component *</Label>
                <select
                  className="w-full h-10 px-3 rounded-md border border-input bg-background"
                  {...register('componentType')}
                >
                  <option value="">انتخاب کنید...</option>
                  {componentTypes.map((type) => (
                    <option key={type} value={type}>
                      {type}
                    </option>
                  ))}
                </select>
                {errors.componentType && (
                  <p className="text-sm text-destructive">{errors.componentType.message}</p>
                )}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
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
