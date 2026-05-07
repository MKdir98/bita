import { useState, useRef } from 'react'
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

const forgotPasswordSchema = z.object({
  mobileNumber: z
    .string()
    .regex(/^09\d{9}$/, 'شماره موبایل باید به فرمت 09xxxxxxxxx باشد'),
})

const resetPasswordSchema = z.object({
  newPassword: z
    .string()
    .min(6, 'رمز عبور باید حداقل ۶ کاراکتر باشد'),
  confirmPassword: z
    .string()
    .min(6, 'تکرار رمز عبور باید حداقل ۶ کاراکتر باشد'),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: 'رمز عبور و تکرار آن باید یکسان باشند',
  path: ['confirmPassword'],
})

type ForgotPasswordFormData = z.infer<typeof forgotPasswordSchema>
type ResetPasswordFormData = z.infer<typeof resetPasswordSchema>

export default function ForgotPasswordPage() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const { setPendingPasswordReset, pendingPasswordReset, clearPendingData } = useAuthStore()
  
  const [step, setStep] = useState<'mobile' | 'otp' | 'newPassword'>('mobile')
  const [otp, setOtp] = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const [verifiedOtp, setVerifiedOtp] = useState<string>('')
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  const {
    register: registerMobile,
    handleSubmit: handleMobileSubmit,
    formState: { errors: mobileErrors },
  } = useForm<ForgotPasswordFormData>({
    resolver: zodResolver(forgotPasswordSchema),
  })

  const {
    register: registerPassword,
    handleSubmit: handlePasswordSubmit,
    formState: { errors: passwordErrors },
  } = useForm<ResetPasswordFormData>({
    resolver: zodResolver(resetPasswordSchema),
  })

  // Request password reset OTP
  const requestOtpMutation = useMutation({
    mutationFn: authApi.forgotPassword,
    onSuccess: () => {
      toast({
        title: 'کد تایید ارسال شد',
        description: 'اگر این شماره در سیستم ثبت شده باشد، کد تایید ارسال خواهد شد',
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

  // Reset password
  const resetPasswordMutation = useMutation({
    mutationFn: authApi.resetPassword,
    onSuccess: () => {
      clearPendingData()
      toast({
        title: 'رمز عبور تغییر یافت',
        description: 'رمز عبور شما با موفقیت تغییر یافت. اکنون می‌توانید وارد شوید.',
      })
      navigate('/login')
    },
    onError: (error) => {
      let message = 'خطا در تغییر رمز عبور. لطفا مجددا تلاش کنید.'
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

  const onMobileSubmit = (data: ForgotPasswordFormData) => {
    setPendingPasswordReset({ mobileNumber: data.mobileNumber })
    requestOtpMutation.mutate({ mobileNumber: data.mobileNumber })
  }

  const handleOtpChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return

    const newOtp = [...otp]
    newOtp[index] = value.slice(-1)
    setOtp(newOtp)

    if (value && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus()
    }

    // Move to password step when OTP is complete
    if (newOtp.every((digit) => digit) && newOtp.join('').length === OTP_LENGTH) {
      setVerifiedOtp(newOtp.join(''))
      setStep('newPassword')
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

    if (pastedData.length === OTP_LENGTH) {
      setVerifiedOtp(pastedData)
      setStep('newPassword')
    }
  }

  const handleResendOtp = () => {
    if (pendingPasswordReset) {
      requestOtpMutation.mutate({ mobileNumber: pendingPasswordReset.mobileNumber })
    }
  }

  const handleOtpVerify = () => {
    if (otp.every((d) => d)) {
      setVerifiedOtp(otp.join(''))
      setStep('newPassword')
    }
  }

  const onPasswordSubmit = (data: ResetPasswordFormData) => {
    if (pendingPasswordReset && verifiedOtp) {
      resetPasswordMutation.mutate({
        mobileNumber: pendingPasswordReset.mobileNumber,
        otpCode: verifiedOtp,
        newPassword: data.newPassword,
      })
    }
  }

  // Step 1: Enter mobile number
  if (step === 'mobile') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">فراموشی رمز عبور</CardTitle>
            <CardDescription>
              شماره موبایل خود را وارد کنید تا کد تایید برای شما ارسال شود
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleMobileSubmit(onMobileSubmit)} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="mobileNumber">شماره موبایل</Label>
                <Input
                  id="mobileNumber"
                  type="tel"
                  placeholder="09xxxxxxxxx"
                  dir="ltr"
                  className="text-center text-lg tracking-widest"
                  {...registerMobile('mobileNumber')}
                />
                {mobileErrors.mobileNumber && (
                  <p className="text-sm text-destructive">
                    {mobileErrors.mobileNumber.message}
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
            <Link to="/login" className="text-sm text-muted-foreground hover:text-primary">
              بازگشت به صفحه ورود
            </Link>
          </CardFooter>
        </Card>
      </div>
    )
  }

  // Step 2: Enter OTP
  if (step === 'otp') {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">کد تایید</CardTitle>
            <CardDescription>
              کد ۶ رقمی ارسال شده به {pendingPasswordReset?.mobileNumber} را وارد کنید
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
                />
              ))}
            </div>

            <div className="flex flex-col gap-2">
              <Button
                onClick={handleOtpVerify}
                disabled={otp.some((d) => !d)}
                className="w-full"
              >
                تایید
              </Button>

              <Button
                variant="ghost"
                onClick={handleResendOtp}
                disabled={requestOtpMutation.isPending}
              >
                ارسال مجدد کد
              </Button>

              <Button variant="link" onClick={() => setStep('mobile')}>
                تغییر شماره موبایل
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Step 3: Enter new password
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">رمز عبور جدید</CardTitle>
          <CardDescription>
            رمز عبور جدید خود را وارد کنید
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handlePasswordSubmit(onPasswordSubmit)} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="newPassword">رمز عبور جدید</Label>
              <Input
                id="newPassword"
                type="password"
                placeholder="حداقل ۶ کاراکتر"
                dir="ltr"
                className="text-center"
                {...registerPassword('newPassword')}
              />
              {passwordErrors.newPassword && (
                <p className="text-sm text-destructive">
                  {passwordErrors.newPassword.message}
                </p>
              )}
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">تکرار رمز عبور جدید</Label>
              <Input
                id="confirmPassword"
                type="password"
                placeholder="تکرار رمز عبور"
                dir="ltr"
                className="text-center"
                {...registerPassword('confirmPassword')}
              />
              {passwordErrors.confirmPassword && (
                <p className="text-sm text-destructive">
                  {passwordErrors.confirmPassword.message}
                </p>
              )}
            </div>
            <Button
              type="submit"
              className="w-full"
              disabled={resetPasswordMutation.isPending}
            >
              {resetPasswordMutation.isPending ? 'در حال ذخیره...' : 'تغییر رمز عبور'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
