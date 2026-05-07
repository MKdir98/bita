import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Building2, Server, Route, Shield } from 'lucide-react'

export default function DashboardPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">داشبورد</h1>
        <p className="text-muted-foreground">به سامانه مدیریت سرویس BITA خوش آمدید</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">سازمان‌ها</CardTitle>
            <Building2 className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">۰</div>
            <p className="text-xs text-muted-foreground">کل سازمان‌های ثبت شده</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">سرویس‌ها</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">۰</div>
            <p className="text-xs text-muted-foreground">سرویس‌های فعال</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">مسیرها</CardTitle>
            <Route className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">۰</div>
            <p className="text-xs text-muted-foreground">مسیرهای تعریف شده</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">دسترسی‌ها</CardTitle>
            <Shield className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">۰</div>
            <p className="text-xs text-muted-foreground">دسترسی‌های فعال</p>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
