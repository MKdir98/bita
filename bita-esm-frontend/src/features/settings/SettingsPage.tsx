import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/stores/authStore'
import { Moon, Sun, Globe, Bell, Shield, Palette } from 'lucide-react'
import { useState, useEffect } from 'react'

export default function SettingsPage() {
  const { user } = useAuthStore()
  const [darkMode, setDarkMode] = useState(false)
  const [notifications, setNotifications] = useState(true)
  const [language, setLanguage] = useState('fa')

  useEffect(() => {
    // Check current theme
    const isDark = document.documentElement.classList.contains('dark')
    setDarkMode(isDark)
  }, [])

  const toggleDarkMode = () => {
    const newValue = !darkMode
    setDarkMode(newValue)
    if (newValue) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('theme', 'light')
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">تنظیمات</h1>
        <p className="text-gray-600 dark:text-gray-400">
          تنظیمات و ترجیحات شخصی خود را مدیریت کنید
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Appearance Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Palette className="w-5 h-5" />
              ظاهر
            </CardTitle>
            <CardDescription>تنظیمات نمایش و ظاهر برنامه</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                {darkMode ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
                <Label htmlFor="dark-mode">حالت تاریک</Label>
              </div>
              <Switch
                id="dark-mode"
                checked={darkMode}
                onCheckedChange={toggleDarkMode}
              />
            </div>
          </CardContent>
        </Card>

        {/* Language Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Globe className="w-5 h-5" />
              زبان
            </CardTitle>
            <CardDescription>تنظیمات زبان برنامه</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <Label>زبان فعلی</Label>
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {language === 'fa' ? 'فارسی' : 'English'}
              </span>
            </div>
            <p className="text-sm text-gray-500">
              در حال حاضر فقط زبان فارسی پشتیبانی می‌شود
            </p>
          </CardContent>
        </Card>

        {/* Notification Settings */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Bell className="w-5 h-5" />
              اعلان‌ها
            </CardTitle>
            <CardDescription>تنظیمات اعلان‌ها و هشدارها</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between">
              <Label htmlFor="notifications">اعلان‌های سیستم</Label>
              <Switch
                id="notifications"
                checked={notifications}
                onCheckedChange={setNotifications}
              />
            </div>
          </CardContent>
        </Card>

        {/* Account Info */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Shield className="w-5 h-5" />
              حساب کاربری
            </CardTitle>
            <CardDescription>اطلاعات حساب کاربری شما</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <div className="flex justify-between">
                <span className="text-sm text-gray-600 dark:text-gray-400">نام</span>
                <span className="text-sm font-medium">{user?.fullName || '-'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-gray-600 dark:text-gray-400">شماره موبایل</span>
                <span className="text-sm font-medium">{user?.mobileNumber || '-'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-gray-600 dark:text-gray-400">نقش‌ها</span>
                <span className="text-sm font-medium">
                  {user?.roles?.join(', ') || '-'}
                </span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
