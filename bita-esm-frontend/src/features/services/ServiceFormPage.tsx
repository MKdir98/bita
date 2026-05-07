import { useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useService, useServiceCollections, useCreateService, useUpdateService } from './useServices'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

const serviceSchema = z.object({
  collectionId: z.number({ required_error: 'انتخاب مجموعه الزامی است' }),
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  version: z.string().regex(/^\d+\.\d+\.\d+$/, 'نسخه باید به فرمت x.y.z باشد'),
  description: z.string().optional(),
  minReplicas: z.number().min(1).max(10).optional(),
  maxReplicas: z.number().min(1).max(100).optional(),
  targetCpuPercent: z.number().min(10).max(90).optional(),
})

type ServiceFormData = z.infer<typeof serviceSchema>

export default function ServiceFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id
  const serviceId = Number(id)

  const { data: service, isLoading: serviceLoading } = useService(serviceId)
  const { data: collections } = useServiceCollections({ size: 100 })
  const createService = useCreateService()
  const updateService = useUpdateService(serviceId)

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ServiceFormData>({
    resolver: zodResolver(serviceSchema),
    defaultValues: {
      minReplicas: 1,
      maxReplicas: 3,
      targetCpuPercent: 70,
    },
  })

  const selectedCollectionId = watch('collectionId')

  useEffect(() => {
    if (service) {
      reset({
        collectionId: (service as any).collectionId,
        name: service.name,
        version: service.version,
        description: service.description || '',
        minReplicas: service.minReplicas,
        maxReplicas: service.maxReplicas,
        targetCpuPercent: service.targetCpuPercent,
      })
    }
  }, [service, reset])

  const onSubmit = async (data: ServiceFormData) => {
    if (isEdit) {
      const { collectionId, ...updateData } = data
      await updateService.mutateAsync(updateData)
    } else {
      await createService.mutateAsync(data as any)
    }
    navigate('/services')
  }

  if (isEdit && serviceLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش سرویس' : 'سرویس جدید'}</CardTitle>
          <CardDescription>
            {isEdit ? 'اطلاعات سرویس را ویرایش کنید' : 'اطلاعات سرویس جدید را وارد کنید'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {!isEdit && (
              <div className="space-y-2">
                <Label>مجموعه سرویس *</Label>
                <select
                  className="w-full h-10 px-3 rounded-md border border-input bg-background"
                  {...register('collectionId', { valueAsNumber: true })}
                >
                  <option value="">انتخاب کنید...</option>
                  {collections?.content.map((col) => (
                    <option key={col.id} value={col.id}>
                      {col.name} ({col.basePath})
                    </option>
                  ))}
                </select>
                {errors.collectionId && (
                  <p className="text-sm text-destructive">{errors.collectionId.message}</p>
                )}
              </div>
            )}

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="name">نام سرویس *</Label>
                <Input id="name" {...register('name')} />
                {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="version">نسخه *</Label>
                <Input id="version" placeholder="1.0.0" dir="ltr" {...register('version')} />
                {errors.version && <p className="text-sm text-destructive">{errors.version.message}</p>}
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
            </div>

            <div className="grid gap-4 md:grid-cols-3">
              <div className="space-y-2">
                <Label htmlFor="minReplicas">حداقل Replica</Label>
                <Input
                  id="minReplicas"
                  type="number"
                  min={1}
                  max={10}
                  {...register('minReplicas', { valueAsNumber: true })}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="maxReplicas">حداکثر Replica</Label>
                <Input
                  id="maxReplicas"
                  type="number"
                  min={1}
                  max={100}
                  {...register('maxReplicas', { valueAsNumber: true })}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="targetCpuPercent">Target CPU (%)</Label>
                <Input
                  id="targetCpuPercent"
                  type="number"
                  min={10}
                  max={90}
                  {...register('targetCpuPercent', { valueAsNumber: true })}
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => navigate('/services')}>
                انصراف
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'در حال ذخیره...' : isEdit ? 'ذخیره تغییرات' : 'ایجاد سرویس'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
