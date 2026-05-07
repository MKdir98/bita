import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { useToast } from '@/hooks/use-toast'

const OTP_LENGTH = 6

export default function OtpPage() {
  const navigate = useNavigate()
  const { toast } = useToast()
  const { pendingMobile, setUser, setTokens } = useAuthStore()
  const [otp, setOtp] = useState<string[]>(Array(OTP_LENGTH).fill(''))
  const inputRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (!pendingMobile) {
      navigate('/login')
    }
  }, [pendingMobile, navigate])

  const verifyOtpMutation = useMutation({
    mutationFn: authApi.verifyOtp,
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken)
      setUser(data.user)
      toast({
        title: 'ورود موفق',
        description: 'به سامانه خوش آمدید',
      })
      navigate('/')
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'کد تایید اشتباه است',
      })
      setOtp(Array(OTP_LENGTH).fill(''))
      inputRefs.current[0]?.focus()
    },
  })

  const handleChange = (index: number, value: string) => {
    if (!/^\d*$/.test(value)) return

    const newOtp = [...otp]
    newOtp[index] = value.slice(-1)
    setOtp(newOtp)

    // Auto-focus next input
    if (value && index < OTP_LENGTH - 1) {
      inputRefs.current[index + 1]?.focus()
    }

    // Auto-submit when complete
    if (newOtp.every((digit) => digit) && newOtp.join('').length === OTP_LENGTH) {
      verifyOtpMutation.mutate({
        mobileNumber: pendingMobile!,
        otpCode: newOtp.join(''),
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

    if (pastedData.length === OTP_LENGTH) {
      verifyOtpMutation.mutate({
        mobileNumber: pendingMobile!,
        otpCode: pastedData,
      })
    }
  }

  const resendOtpMutation = useMutation({
    mutationFn: () => authApi.requestOtp({ mobileNumber: pendingMobile! }),
    onSuccess: () => {
      toast({
        title: 'کد جدید ارسال شد',
        description: 'کد تایید جدید به شماره موبایل شما ارسال گردید',
      })
    },
  })

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl">کد تایید</CardTitle>
          <CardDescription>
            کد ۶ رقمی ارسال شده به {pendingMobile} را وارد کنید
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
                onChange={(e) => handleChange(index, e.target.value)}
                onKeyDown={(e) => handleKeyDown(index, e)}
                onPaste={handlePaste}
                className="w-12 h-12 text-center text-xl font-bold"
                disabled={verifyOtpMutation.isPending}
              />
            ))}
          </div>

          <div className="flex flex-col gap-2">
            <Button
              onClick={() =>
                verifyOtpMutation.mutate({
                  mobileNumber: pendingMobile!,
                  otpCode: otp.join(''),
                })
              }
              disabled={otp.some((d) => !d) || verifyOtpMutation.isPending}
              className="w-full"
            >
              {verifyOtpMutation.isPending ? 'در حال بررسی...' : 'تایید'}
            </Button>

            <Button
              variant="ghost"
              onClick={() => resendOtpMutation.mutate()}
              disabled={resendOtpMutation.isPending}
            >
              ارسال مجدد کد
            </Button>

            <Button variant="link" onClick={() => navigate('/login')}>
              تغییر شماره موبایل
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
