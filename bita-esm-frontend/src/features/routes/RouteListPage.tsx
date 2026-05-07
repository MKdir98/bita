import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useRoutes, useDeleteRoute } from './useRoutes'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Plus, Search, Eye, Pencil, Trash2, Route, History } from 'lucide-react'
import { useAuthStore } from '@/stores/authStore'

export default function RouteListPage() {
  const [searchParams] = useSearchParams()
  const serviceId = searchParams.get('serviceId')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const { hasAnyRole } = useAuthStore()
  const canViewAudit = hasAnyRole(['ADMIN', 'VIEWER'])

  const { data, isLoading } = useRoutes({
    serviceId: serviceId ? Number(serviceId) : undefined,
    search,
    page,
    size: 10,
  })
  const deleteRoute = useDeleteRoute()

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این مسیر اطمینان دارید؟')) {
      deleteRoute.mutate(id)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">مسیرها</h1>
          <p className="text-muted-foreground">مدیریت مسیرهای پردازش</p>
        </div>
        <Button asChild>
          <Link to={serviceId ? `/routes/new?serviceId=${serviceId}` : '/routes/new'}>
            <Plus className="w-4 h-4 ml-2" />
            مسیر جدید
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="جستجو..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pr-10"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="text-center py-8">در حال بارگذاری...</div>
          ) : !data?.content.length ? (
            <div className="text-center py-8">
              <Route className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
              <p className="text-muted-foreground">مسیری یافت نشد</p>
            </div>
          ) : (
            <div className="space-y-4">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-right py-3 px-4">نام</th>
                    <th className="text-right py-3 px-4">سرویس</th>
                    <th className="text-right py-3 px-4">قالب</th>
                    <th className="text-right py-3 px-4">وضعیت</th>
                    <th className="text-right py-3 px-4">عملیات</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((route) => (
                    <tr key={route.id} className="border-b hover:bg-muted/50">
                      <td className="py-3 px-4 font-medium">{route.name}</td>
                      <td className="py-3 px-4 text-muted-foreground">{route.serviceName}</td>
                      <td className="py-3 px-4 text-muted-foreground">{route.templateName}</td>
                      <td className="py-3 px-4">
                        <span
                          className={`px-2 py-1 rounded-full text-xs ${
                            route.active
                              ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                              : 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200'
                          }`}
                        >
                          {route.active ? 'فعال' : 'غیرفعال'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <Button variant="ghost" size="icon" asChild>
                            <Link to={`/routes/${route.id}`}>
                              <Eye className="w-4 h-4" />
                            </Link>
                          </Button>
                          {canViewAudit && (
                            <Button variant="ghost" size="icon" asChild>
                              <Link to={`/audit/route/${route.id}`} title="تاریخچه تغییرات">
                                <History className="w-4 h-4" />
                              </Link>
                            </Button>
                          )}
                          <Button variant="ghost" size="icon" asChild>
                            <Link to={`/routes/${route.id}/edit`}>
                              <Pencil className="w-4 h-4" />
                            </Link>
                          </Button>
                          <Button variant="ghost" size="icon" onClick={() => handleDelete(route.id)}>
                            <Trash2 className="w-4 h-4 text-destructive" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 pt-4">
                  <Button variant="outline" size="sm" onClick={() => setPage((p) => p - 1)} disabled={data.first}>
                    قبلی
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    صفحه {data.number + 1} از {data.totalPages}
                  </span>
                  <Button variant="outline" size="sm" onClick={() => setPage((p) => p + 1)} disabled={data.last}>
                    بعدی
                  </Button>
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
