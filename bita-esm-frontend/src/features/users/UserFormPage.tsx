import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useUser, useRoles, useCreateUser, useUpdateUser } from './useUsers'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import RoleAssignment from './RoleAssignment'

const userSchema = z.object({
  mobileNumber: z.string().regex(/^09\d{9}$/, 'شماره موبایل باید به فرمت 09xxxxxxxxx باشد'),
  fullName: z.string().optional(),
  email: z.string().email('ایمیل نامعتبر است').optional().or(z.literal('')),
  active: z.boolean().optional(),
})

type UserFormData = z.infer<typeof userSchema>

export default function UserFormPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const isEdit = !!id && id !== 'new'
  const userId = Number(id)

  const [selectedRoles, setSelectedRoles] = useState<number[]>([])

  const { data: user, isLoading: userLoading } = useUser(userId)
  const { data: roles } = useRoles()
  const createUser = useCreateUser()
  const updateUser = useUpdateUser(userId)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<UserFormData>({
    resolver: zodResolver(userSchema),
    defaultValues: {
      active: true,
    },
  })

  useEffect(() => {
    if (user && roles) {
      reset({
        mobileNumber: user.mobileNumber,
        fullName: user.fullName || '',
        email: user.email || '',
        active: user.active,
      })
      // Find role IDs from role names
      const userRoleIds = roles
        .filter((role) => user.roles.includes(role.name))
        .map((role) => role.id)
      setSelectedRoles(userRoleIds)
    }
  }, [user, roles, reset])

  const onSubmit = async (data: UserFormData) => {
    if (isEdit) {
      await updateUser.mutateAsync({
        fullName: data.fullName,
        email: data.email || undefined,
        active: data.active,
        roleIds: selectedRoles,
      })
    } else {
      await createUser.mutateAsync({
        ...data,
        roleIds: selectedRoles,
      })
    }
    navigate('/users')
  }

  if (isEdit && userLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-2xl mx-auto">
      <Card>
        <CardHeader>
          <CardTitle>{isEdit ? 'ویرایش کاربر' : 'کاربر جدید'}</CardTitle>
          <CardDescription>
            {isEdit ? 'اطلاعات کاربر را ویرایش کنید' : 'اطلاعات کاربر جدید را وارد کنید'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="mobileNumber">شماره موبایل *</Label>
              <Input
                id="mobileNumber"
                dir="ltr"
                placeholder="09xxxxxxxxx"
                disabled={isEdit}
                {...register('mobileNumber')}
              />
              {errors.mobileNumber && (
                <p className="text-sm text-destructive">{errors.mobileNumber.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="fullName">نام کامل</Label>
              <Input id="fullName" {...register('fullName')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">ایمیل</Label>
              <Input id="email" type="email" dir="ltr" {...register('email')} />
              {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
            </div>

            {isEdit && (
              <div className="flex items-center gap-2">
                <input type="checkbox" id="active" {...register('active')} className="w-4 h-4" />
                <Label htmlFor="active">کاربر فعال</Label>
              </div>
            )}

            <RoleAssignment
              roles={roles || []}
              selectedRoles={selectedRoles}
              onChange={setSelectedRoles}
            />

            <div className="flex justify-end gap-2 pt-4">
              <Button type="button" variant="outline" onClick={() => navigate('/users')}>
                انصراف
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'در حال ذخیره...' : isEdit ? 'ذخیره تغییرات' : 'ایجاد کاربر'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
