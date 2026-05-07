import { useNavigate, Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'
import { isAxiosError } from 'axios'

const loginSchema = z.object({
  mobileNumber: z
    .string()
    .regex(/^09\d{9}$/, 'شماره موبایل باید به فرمت 09xxxxxxxxx باشد'),
  password: z
    .string()
    .min(6, 'رمز عبور باید حداقل ۶ کاراکتر باشد'),
})

type LoginFormData = z.infer<typeof loginSchema>

export default function LoginPage() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const { setUser, setTokens } = useAuthStore()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken)
      setUser(data.user)
      toast({
        title: 'ورود موفق',
        description: `خوش آمدید ${data.user.fullName || ''}`,
      })
      navigate('/')
    },
    onError: (error) => {
      let message = 'خطا در ورود. لطفا مجددا تلاش کنید.'
      if (isAxiosError(error) && error.response?.data?.message) {
        message = error.response.data.message
      }
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: message,
      })
    },
  })

  const onSubmit = (data: LoginFormData) => {
    loginMutation.mutate(data)
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">سامانه مدیریت سرویس</CardTitle>
          <CardDescription>
            برای ورود، شماره موبایل و رمز عبور خود را وارد کنید
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="mobileNumber">شماره موبایل</Label>
              <Input
                id="mobileNumber"
                type="tel"
                placeholder="09xxxxxxxxx"
                dir="ltr"
                className="text-center text-lg tracking-widest"
                {...register('mobileNumber')}
              />
              {errors.mobileNumber && (
                <p className="text-sm text-destructive">
                  {errors.mobileNumber.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">رمز عبور</Label>
              <Input
                id="password"
                type="password"
                placeholder="رمز عبور"
                dir="ltr"
                className="text-center"
                {...register('password')}
              />
              {errors.password && (
                <p className="text-sm text-destructive">
                  {errors.password.message}
                </p>
              )}
            </div>
            <Button
              type="submit"
              className="w-full"
              disabled={loginMutation.isPending}
            >
              {loginMutation.isPending ? 'در حال ورود...' : 'ورود'}
            </Button>
          </form>
        </CardContent>
        <CardFooter className="flex flex-col space-y-2">
          <Link
            to="/forgot-password"
            className="text-sm text-muted-foreground hover:text-primary"
          >
            رمز عبور خود را فراموش کرده‌اید؟
          </Link>
          <div className="text-sm text-muted-foreground">
            حساب کاربری ندارید؟{' '}
            <Link to="/register" className="text-primary hover:underline">
              ثبت نام کنید
            </Link>
          </div>
        </CardFooter>
      </Card>
    </div>
  )
}
