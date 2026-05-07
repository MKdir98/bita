import { useState, useRef, useEffect } from 'react'
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

const OTP_LENGTH = 6

const registerSchema = z.object({
  mobileNumber: z
    .string()
    .regex(/^09\d{9}$/, 'شماره موبایل باید به فرمت 09xxxxxxxxx باشد'),
  password: z
    .string()
    .min(6, 'رمز عبور باید حداقل ۶ کاراکتر باشد'),
  confirmPassword: z
    .string()
    .min(6, 'تکرار رمز عبور باید حداقل ۶ کاراکتر باشد'),
  fullName: z
    .string()
    .max(100, 'نام نباید بیش از ۱۰۰ کاراکتر باشد')
    .optional(),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'رمز عبور و تکرار آن باید یکسان باشند',
  path: ['confirmPassword'],
})

type RegisterFormData = z.infer<typeof registerSchema>

export default function RegisterPage() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const { setUser, setTokens, setPendingRegistration, pendingRegistration, clearPendingData } = useAuthStore()
  
  const [step, setStep] = useState<'form' | 'otp'>('form')
  const [otp, setOtp] = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  const {
    register,
    handleSubmit,
    formState: { errors },
    getValues,
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  })

  // Request OTP for registration
  const requestOtpMutation = useMutation({
    mutationFn: authApi.requestRegistrationOtp,
    onSuccess: () => {
      toast({
        title: 'کد تایید ارسال شد',
        description: 'کد تایید به شماره موبایل شما ارسال گردید',
      })
      setStep('otp')
    },
    onError: (error) => {
      let message = 'خطا در ارسال کد تایید. لطفا مجددا تلاش کنید.'
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

  // Verify OTP and complete registration
  const verifyMutation = useMutation({
    mutationFn: authApi.verifyRegistration,
    onSuccess: (data) => {
      clearPendingData()
      setTokens(data.accessToken, data.refreshToken)
      setUser(data.user)
      toast({
        title: 'ثبت نام موفق',
        description: `خوش آمدید ${data.user.fullName || ''}`,
      })
      navigate('/')
    },
    onError: (error) => {
      let message = 'کد تایید اشتباه است'
      if (isAxiosError(error) && error.response?.data?.message) {
        message = error.response.data.message
      }
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: message,
      })
      setOtp(Array(OTP_LENGTH).fill(''))
      inputRefs.current[0]?.focus()
    },
  })

  const onSubmit = (data: RegisterFormData) => {
    // Store registration data for OTP verification step
    setPendingRegistration({
      mobileNumber: data.mobileNumber,
      password: data.password,
      fullName: data.fullName,
    })
    requestOtpMutation.mutate({
      mobileNumber: data.mobileNumber,
      password: data.password,
      fullName: data.fullName,
    })
  }

  const handleOtpChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return

    const newOtp = [...otp]
    newOtp[index] = value.slice(-1)
    setOtp(newOtp)

    if (value && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus()
    }

    // Auto-submit when complete
    if (newOtp.every((digit) => digit) && newOtp.join('').length === OTP_LENGTH && pendingRegistration) {
      verifyMutation.mutate({
        mobileNumber: pendingRegistration.mobileNumber,
        otpCode: newOtp.join(''),
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
      })
    }
  }

  const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus()
    }
  }

  const handlePaste = (e: React.ClipboardEvent) => {
    e.preventDefault()
    const pastedData = e.clipboardData.getData('text').slice(0, OTP_LENGTH)
    if (!/^\d+$/.test(pastedData)) return

    const newOtp = [...otp]
    pastedData.split('').forEach((char, index) => {
      newOtp[index] = char
    })
    setOtp(newOtp)

    if (pastedData.length === OTP_LENGTH && pendingRegistration) {
      verifyMutation.mutate({
        mobileNumber: pendingRegistration.mobileNumber,
        otpCode: pastedData,
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
      })
    }
  }

  const handleResendOtp = () => {
    if (pendingRegistration) {
      requestOtpMutation.mutate({
        mobileNumber: pendingRegistration.mobileNumber,
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
      })
    }
  }

  const handleVerifySubmit = () => {
    if (pendingRegistration && otp.every((d) => d)) {
      verifyMutation.mutate({
        mobileNumber: pendingRegistration.mobileNumber,
        otpCode: otp.join(''),
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
      })
    }
  }

  // Registration form step
  if (step === 'form') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">ثبت نام</CardTitle>
            <CardDescription>
              برای ثبت نام، اطلاعات خود را وارد کنید
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
                <Label htmlFor="fullName">نام و نام خانوادگی (اختیاری)</Label>
                <Input
                  id="fullName"
                  type="text"
                  placeholder="نام و نام خانوادگی"
                  {...register('fullName')}
                />
                {errors.fullName && (
                  <p className="text-sm text-destructive">
                    {errors.fullName.message}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">رمز عبور</Label>
                <Input
                  id="password"
                  type="password"
                  placeholder="حداقل ۶ کاراکتر"
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
              <div className="space-y-2">
                <Label htmlFor="confirmPassword">تکرار رمز عبور</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  placeholder="تکرار رمز عبور"
                  dir="ltr"
                  className="text-center"
                  {...register('confirmPassword')}
                />
                {errors.confirmPassword && (
                  <p className="text-sm text-destructive">
                    {errors.confirmPassword.message}
                  </p>
                )}
              </div>
              <Button
                type="submit"
                className="w-full"
                disabled={requestOtpMutation.isPending}
              >
                {requestOtpMutation.isPending ? 'در حال ارسال...' : 'دریافت کد تایید'}
              </Button>
            </form>
          </CardContent>
          <CardFooter className="flex justify-center">
            <div className="text-sm text-muted-foreground">
              قبلا ثبت نام کرده‌اید؟{' '}
              <Link to="/login" className="text-primary hover:underline">
                وارد شوید
              </Link>
            </div>
          </CardFooter>
        </Card>
      </div>
    )
  }

  // OTP verification step
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">کد تایید</CardTitle>
          <CardDescription>
            کد ۶ رقمی ارسال شده به {pendingRegistration?.mobileNumber} را وارد کنید
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex justify-center gap-2" dir="ltr">
            {otp.map((digit, index) => (
              <Input
                key={index}
                ref={(el) => (inputRefs.current[index] = el)}
                type="text"
                inputMode="numeric"
                maxLength={1}
                value={digit}
                onChange={(e) => handleOtpChange(index, e.target.value)}
                onKeyDown={(e) => handleKeyDown(index, e)}
                onPaste={handlePaste}
                className="w-12 h-12 text-center text-xl font-bold"
                disabled={verifyMutation.isPending}
              />
            ))}
          </div>

          <div className="flex flex-col gap-2">
            <Button
              onClick={handleVerifySubmit}
              disabled={otp.some((d) => !d) || verifyMutation.isPending}
              className="w-full"
            >
              {verifyMutation.isPending ? 'در حال بررسی...' : 'تکمیل ثبت نام'}
            </Button>

            <Button
              variant="ghost"
              onClick={handleResendOtp}
              disabled={requestOtpMutation.isPending}
            >
              ارسال مجدد کد
            </Button>

            <Button variant="link" onClick={() => setStep('form')}>
              تغییر اطلاعات
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
