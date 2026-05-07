import { useEffect, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useRoute, useCreateRoute, useUpdateRoute } from './useRoutes'
import { useServices } from '@/features/services/useServices'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import TemplateSelector from './TemplateSelector'
import DynamicConfigForm from './DynamicConfigForm'

const routeSchema = z.object({
  serviceId: z.number({ required_error: 'انتخاب سرویس الزامی است' }),
  routeTemplateId: z.number({ required_error: 'انتخاب قالب الزامی است' }),
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  description: z.string().optional(),
})

type RouteFormData = z.infer<typeof routeSchema>

export default function RouteFormPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const isEdit = !!id
  const routeId = Number(id)
  const preselectedServiceId = searchParams.get('serviceId')

  const [config, setConfig] = useState<object>({})
  const [selectedTemplate, setSelectedTemplate] = useState<any>(null)

  const { data: route, isLoading: routeLoading } = useRoute(routeId)
  const { data: services } = useServices({ size: 100 })
  const createRoute = useCreateRoute()
  const updateRoute = useUpdateRoute(routeId)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RouteFormData>({
    resolver: zodResolver(routeSchema),
    defaultValues: {
      serviceId: preselectedServiceId ? Number(preselectedServiceId) : undefined,
    },
  })

  useEffect(() => {
    if (route) {
      reset({
        serviceId: route.serviceId,
        routeTemplateId: route.routeTemplateId,
        name: route.name,
        description: route.description || '',
      })
      setConfig(route.config || {})
    }
  }, [route, reset])

  const handleTemplateSelect = (template: any) => {
    setSelectedTemplate(template)
    setValue('routeTemplateId', template.id)
  }

  const onSubmit = async (data: RouteFormData) => {
    if (isEdit) {
      const { serviceId, routeTemplateId, ...updateData } = data
      await updateRoute.mutateAsync({ ...updateData, config })
    } else {
      await createRoute.mutateAsync({ ...data, config })
    }
    navigate('/routes')
  }

  if (isEdit && routeLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش مسیر' : 'مسیر جدید'}</CardTitle>
          <CardDescription>
            {isEdit ? 'اطلاعات مسیر را ویرایش کنید' : 'مسیر جدید بر اساس قالب ایجاد کنید'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label>سرویس *</Label>
                <select
                  className="w-full h-10 px-3 rounded-md border border-input bg-background"
                  {...register('serviceId', { valueAsNumber: true })}
                  disabled={isEdit}
                >
                  <option value="">انتخاب کنید...</option>
                  {services?.content.map((svc) => (
                    <option key={svc.id} value={svc.id}>
                      {svc.name} (v{svc.version})
                    </option>
                  ))}
                </select>
                {errors.serviceId && (
                  <p className="text-sm text-destructive">{errors.serviceId.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="name">نام مسیر *</Label>
                <Input id="name" {...register('name')} />
                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
            </div>

            {!isEdit && (
              <div className="space-y-2">
                <Label>قالب مسیر *</Label>
                <TemplateSelector onSelect={handleTemplateSelect} selected={selectedTemplate} />
                {errors.routeTemplateId && (
                  <p className="text-sm text-destructive">{errors.routeTemplateId.message}</p>
                )}
              </div>
            )}

            {(selectedTemplate?.configSchema || route?.config) && (
              <div className="space-y-2">
                <Label>پیکربندی</Label>
                <DynamicConfigForm
                  schema={selectedTemplate?.configSchema || {}}
                  value={config}
                  onChange={setConfig}
                />
              </div>
            )}

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => navigate('/routes')}>
                انصراف
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'در حال ذخیره...' : isEdit ? 'ذخیره تغییرات' : 'ایجاد مسیر'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
