import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useProfile, useUpdateProfile } from './useUsers'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { User, Mail, Phone, Shield } from 'lucide-react'

const profileSchema = z.object({
  fullName: z.string().optional(),
  email: z.string().email('ایمیل نامعتبر است').optional().or(z.literal('')),
})

type ProfileFormData = z.infer<typeof profileSchema>

export default function UserProfilePage() {
  const { data: profile, isLoading } = useProfile()
  const updateProfile = useUpdateProfile()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
  })

  useEffect(() => {
    if (profile) {
      reset({
        fullName: profile.fullName || '',
        email: profile.email || '',
      })
    }
  }, [profile, reset])

  const onSubmit = async (data: ProfileFormData) => {
    await updateProfile.mutateAsync({
      fullName: data.fullName,
      email: data.email || undefined,
    })
  }

  if (isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div>
        <h1 className="text-3xl font-bold">پروفایل</h1>
        <p className="text-muted-foreground">مدیریت اطلاعات حساب کاربری</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>اطلاعات کاربر</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center gap-4 p-4 bg-muted rounded-lg">
            <div className="w-16 h-16 rounded-full bg-primary text-primary-foreground flex items-center justify-center">
              <User className="w-8 h-8" />
            </div>
            <div>
              <p className="font-bold text-lg">{profile?.fullName || 'بدون نام'}</p>
              <p className="text-muted-foreground" dir="ltr">
                <Phone className="w-4 h-4 inline ml-1" />
                {profile?.mobileNumber}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Shield className="w-4 h-4 text-muted-foreground" />
            <span className="text-sm text-muted-foreground">نقش‌ها:</span>
            <div className="flex gap-1">
              {profile?.roles.map((role) => (
                <span key={role} className="text-xs bg-secondary px-2 py-0.5 rounded">
                  {role}
                </span>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>ویرایش پروفایل</CardTitle>
          <CardDescription>اطلاعات خود را به‌روزرسانی کنید</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="fullName">نام کامل</Label>
              <Input id="fullName" {...register('fullName')} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">ایمیل</Label>
              <Input id="email" type="email" dir="ltr" {...register('email')} />
              {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
            </div>

            <Button type="submit" disabled={isSubmitting || !isDirty}>
              {isSubmitting ? 'در حال ذخیره...' : 'ذخیره تغییرات'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
