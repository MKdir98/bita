import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useService, useServiceAccess, useRevokeAccess } from './useServices'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { formatDateTime } from '@/lib/utils'
import { Pencil, Plus, Trash2, Route, Shield, Server, Settings } from 'lucide-react'
import PhaseChangeDialog from './PhaseChangeDialog'
import type { ServicePhase } from '@/types'

const phaseColors: Record<ServicePhase, string> = {
  DRAFT: 'bg-gray-100 text-gray-800',
  TEST: 'bg-yellow-100 text-yellow-800',
  ACTIVE: 'bg-green-100 text-green-800',
}

const phaseLabels: Record<ServicePhase, string> = {
  DRAFT: 'پیش‌نویس',
  TEST: 'تست',
  ACTIVE: 'فعال',
}

export default function ServiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const serviceId = Number(id)
  const [phaseDialogOpen, setPhaseDialogOpen] = useState(false)

  const { data: service, isLoading } = useService(serviceId)
  const { data: accessList } = useServiceAccess(serviceId)
  const revokeAccess = useRevokeAccess(serviceId)

  const handleRevokeAccess = (clientId: number) => {
    if (confirm('آیا از لغو این دسترسی اطمینان دارید؟')) {
      revokeAccess.mutate(clientId)
    }
  }

  if (isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  if (!service) {
    return <div className="text-center py-8">سرویس یافت نشد</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{service.name}</h1>
          <p className="text-muted-foreground">نسخه {service.version}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => setPhaseDialogOpen(true)}>
            <Settings className="w-4 h-4 ml-2" />
            تغییر فاز
          </Button>
          <Button asChild>
            <Link to={`/services/${serviceId}/edit`}>
              <Pencil className="w-4 h-4 ml-2" />
              ویرایش
            </Link>
          </Button>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Server className="w-5 h-5" />
              اطلاعات سرویس
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <span className="text-muted-foreground text-sm">فاز</span>
                <div className="mt-1">
                  <span className={`px-2 py-1 rounded-full text-xs ${phaseColors[service.phase]}`}>
                    {phaseLabels[service.phase]}
                  </span>
                </div>
              </div>
              <div>
                <span className="text-muted-foreground text-sm">مسیر کامل</span>
                <p className="mt-1 font-mono text-sm" dir="ltr">{service.fullPath}</p>
              </div>
            </div>

            {service.description && (
              <div>
                <span className="text-muted-foreground text-sm">توضیحات</span>
                <p className="mt-1">{service.description}</p>
              </div>
            )}

            <div className="grid grid-cols-3 gap-4 pt-4 border-t">
              <div>
                <span className="text-muted-foreground text-sm">حداقل Replica</span>
                <p className="mt-1 font-bold">{service.minReplicas}</p>
              </div>
              <div>
                <span className="text-muted-foreground text-sm">حداکثر Replica</span>
                <p className="mt-1 font-bold">{service.maxReplicas}</p>
              </div>
              <div>
                <span className="text-muted-foreground text-sm">Target CPU</span>
                <p className="mt-1 font-bold">{service.targetCpuPercent}%</p>
              </div>
            </div>

            {service.k8sDeploymentName && (
              <div className="pt-4 border-t">
                <span className="text-muted-foreground text-sm">Kubernetes</span>
                <p className="mt-1 font-mono text-sm" dir="ltr">
                  Deployment: {service.k8sDeploymentName}
                </p>
                <p className="font-mono text-sm" dir="ltr">
                  Service: {service.k8sServiceName}
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <Shield className="w-5 h-5" />
                دسترسی‌ها
              </CardTitle>
              <CardDescription>سازمان‌های دارای دسترسی</CardDescription>
            </div>
            <Button size="sm" asChild>
              <Link to={`/access?serviceId=${serviceId}`}>
                <Plus className="w-4 h-4 ml-2" />
                افزودن
              </Link>
            </Button>
          </CardHeader>
          <CardContent>
            {!accessList?.length ? (
              <div className="text-center py-4">
                <Shield className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
                <p className="text-muted-foreground text-sm">دسترسی‌ای تعریف نشده</p>
              </div>
            ) : (
              <div className="space-y-3">
                {accessList.map((access: any) => (
                  <div key={access.id} className="flex items-center justify-between p-3 border rounded-lg">
                    <div>
                      <p className="font-medium">{access.clientName}</p>
                      <p className="text-sm text-muted-foreground">
                        {access.rateLimit ? `${access.rateLimit} درخواست/${access.rateLimitWindow}` : 'بدون محدودیت'}
                      </p>
                    </div>
                    <Button variant="ghost" size="icon" onClick={() => handleRevokeAccess(access.clientId)}>
                      <Trash2 className="w-4 h-4 text-destructive" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Route className="w-5 h-5" />
              مسیرها
            </CardTitle>
            <CardDescription>مسیرهای تعریف شده برای این سرویس</CardDescription>
          </div>
          <Button size="sm" asChild>
            <Link to={`/routes/new?serviceId=${serviceId}`}>
              <Plus className="w-4 h-4 ml-2" />
              مسیر جدید
            </Link>
          </Button>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8 text-muted-foreground">
            برای مشاهده مسیرها به صفحه مسیرها مراجعه کنید
          </div>
        </CardContent>
      </Card>

      <PhaseChangeDialog
        open={phaseDialogOpen}
        onOpenChange={setPhaseDialogOpen}
        serviceId={serviceId}
        currentPhase={service.phase}
      />
    </div>
  )
}
