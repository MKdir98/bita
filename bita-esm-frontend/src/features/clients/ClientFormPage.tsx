import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useClient, useCreateClient, useUpdateClient } from './useClients'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Plus, X } from 'lucide-react'
import type { Tag } from '@/types'

const clientSchema = z.object({
  name: z.string().min(2, 'نام باید حداقل ۲ کاراکتر باشد'),
  description: z.string().optional(),
  contactEmail: z.string().email('ایمیل نامعتبر است').optional().or(z.literal('')),
  contactPhone: z.string().optional(),
})

type ClientFormData = z.infer<typeof clientSchema>

interface TagInput {
  key: string
  value: string
}

export default function ClientFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id
  const clientId = Number(id)

  const { data: client, isLoading } = useClient(clientId)
  const createClient = useCreateClient()
  const updateClient = useUpdateClient(clientId)

  const [tags, setTags] = useState<TagInput[]>([])
  const [newTagKey, setNewTagKey] = useState('')
  const [newTagValue, setNewTagValue] = useState('')

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ClientFormData>({
    resolver: zodResolver(clientSchema),
  })

  useEffect(() => {
    if (client) {
      reset({
        name: client.name,
        description: client.description || '',
        contactEmail: client.contactEmail || '',
        contactPhone: client.contactPhone || '',
      })
      // Load existing tags
      if (client.tags) {
        setTags(client.tags.map((t: Tag) => ({ key: t.key, value: t.value })))
      }
    }
  }, [client, reset])

  const addTag = () => {
    if (newTagKey.trim() && newTagValue.trim()) {
      // Check for duplicate key
      if (tags.some(t => t.key === newTagKey.trim())) {
        alert('این کلید قبلاً اضافه شده است')
        return
      }
      setTags([...tags, { key: newTagKey.trim(), value: newTagValue.trim() }])
      setNewTagKey('')
      setNewTagValue('')
    }
  }

  const removeTag = (index: number) => {
    setTags(tags.filter((_, i) => i !== index))
  }

  const onSubmit = async (data: ClientFormData) => {
    const payload = {
      ...data,
      tags: tags.length > 0 ? tags : undefined,
    }

    if (isEdit) {
      await updateClient.mutateAsync(payload)
    } else {
      await createClient.mutateAsync(payload as any)
    }
    navigate('/clients')
  }

  if (isEdit && isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش سازمان' : 'سازمان جدید'}</CardTitle>
          <CardDescription>
            {isEdit ? 'اطلاعات سازمان را ویرایش کنید' : 'اطلاعات سازمان جدید را وارد کنید'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">نام سازمان *</Label>
              <Input id="name" {...register('name')} />
              {errors.name && (
                <p className="text-sm text-destructive">{errors.name.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">توضیحات</Label>
              <Input id="description" {...register('description')} />
            </div>

            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="contactEmail">ایمیل</Label>
                <Input id="contactEmail" type="email" dir="ltr" {...register('contactEmail')} />
                {errors.contactEmail && (
                  <p className="text-sm text-destructive">{errors.contactEmail.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="contactPhone">تلفن</Label>
                <Input id="contactPhone" dir="ltr" {...register('contactPhone')} />
              </div>
            </div>

            {/* Tags Section */}
            <div className="space-y-3">
              <Label>برچسب‌ها (کلید - مقدار)</Label>
              
              {/* Existing tags */}
              {tags.length > 0 && (
                <div className="space-y-2">
                  {tags.map((tag, index) => (
                    <div key={index} className="flex items-center gap-2 p-2 bg-secondary/50 rounded-md">
                      <span className="font-medium text-sm">{tag.key}:</span>
                      <span className="text-sm flex-1">{tag.value}</span>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-6 w-6"
                        onClick={() => removeTag(index)}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}

              {/* Add new tag */}
              <div className="flex items-end gap-2">
                <div className="flex-1">
                  <Label htmlFor="tagKey" className="text-xs text-muted-foreground">کلید</Label>
                  <Input
                    id="tagKey"
                    placeholder="مثال: type"
                    value={newTagKey}
                    onChange={(e) => setNewTagKey(e.target.value)}
                    dir="ltr"
                  />
                </div>
                <div className="flex-1">
                  <Label htmlFor="tagValue" className="text-xs text-muted-foreground">مقدار</Label>
                  <Input
                    id="tagValue"
                    placeholder="مثال: private"
                    value={newTagValue}
                    onChange={(e) => setNewTagValue(e.target.value)}
                    dir="ltr"
                  />
                </div>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  onClick={addTag}
                  disabled={!newTagKey.trim() || !newTagValue.trim()}
                >
                  <Plus className="h-4 w-4" />
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">
                برچسب‌ها به صورت کلید-مقدار ذخیره می‌شوند. مثال: type: private یا national_id: 002109
              </p>
            </div>

            <div className="flex justify-end gap-2 pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => navigate('/clients')}
              >
                انصراف
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'در حال ذخیره...' : isEdit ? 'ذخیره تغییرات' : 'ایجاد سازمان'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
