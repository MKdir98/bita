import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useClients, useDeleteClient } from './useClients'
import { useAuthStore } from '@/stores/authStore'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { formatDate } from '@/lib/utils'
import { Plus, Search, Eye, Pencil, Trash2, Building2, History } from 'lucide-react'

export default function ClientListPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useClients({ search, page, size: 10 })
  const deleteClient = useDeleteClient()
  const { hasAnyRole } = useAuthStore()
  
  // Only ADMIN and CLIENT_MANAGER can create/edit/delete clients
  const canManageClients = hasAnyRole(['ADMIN', 'CLIENT_MANAGER'])
  const canViewAudit = hasAnyRole(['ADMIN', 'VIEWER'])

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این سازمان اطمینان دارید؟')) {
      deleteClient.mutate(id)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">سازمان‌ها</h1>
          <p className="text-muted-foreground">مدیریت سازمان‌ها و اعتبارنامه‌های آن‌ها</p>
        </div>
        {canManageClients && (
          <Button asChild>
            <Link to="/clients/new">
              <Plus className="w-4 h-4 ml-2" />
              سازمان جدید
            </Link>
          </Button>
        )}
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="جستجو در سازمان‌ها..."
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
              <Building2 className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
              <p className="text-muted-foreground">سازمانی یافت نشد</p>
            </div>
          ) : (
            <div className="space-y-4">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-right py-3 px-4">نام</th>
                    <th className="text-right py-3 px-4">برچسب‌ها</th>
                    <th className="text-right py-3 px-4">وضعیت</th>
                    <th className="text-right py-3 px-4">تاریخ ایجاد</th>
                    <th className="text-right py-3 px-4">عملیات</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((client) => (
                    <tr key={client.id} className="border-b hover:bg-muted/50">
                      <td className="py-3 px-4 font-medium">{client.name}</td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {client.tags?.slice(0, 2).map((tag) => (
                            <span
                              key={tag.id || `${tag.key}-${tag.value}`}
                              className="px-1.5 py-0.5 bg-secondary text-secondary-foreground rounded text-xs"
                            >
                              {tag.key}: {tag.value}
                            </span>
                          ))}
                          {client.tags?.length > 2 && (
                            <span className="px-1.5 py-0.5 text-muted-foreground text-xs">
                              +{client.tags.length - 2}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-1 rounded-full text-xs ${
                            client.active
                              ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                              : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                          }`}
                        >
                          {client.active ? 'فعال' : 'غیرفعال'}
                        </span>
                      </td>
                      <td className="py-3 px-4 text-muted-foreground">
                        {formatDate(client.createdAt)}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <Button variant="ghost" size="icon" asChild>
                            <Link to={`/clients/${client.id}`}>
                              <Eye className="w-4 h-4" />
                            </Link>
                          </Button>
                          {canViewAudit && (
                            <Button variant="ghost" size="icon" asChild>
                              <Link to={`/audit/client/${client.id}`} title="تاریخچه تغییرات">
                                <History className="w-4 h-4" />
                              </Link>
                            </Button>
                          )}
                          {canManageClients && (
                            <>
                              <Button variant="ghost" size="icon" asChild>
                                <Link to={`/clients/${client.id}/edit`}>
                                  <Pencil className="w-4 h-4" />
                                </Link>
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleDelete(client.id)}
                              >
                                <Trash2 className="w-4 h-4 text-destructive" />
                              </Button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-2 pt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => p - 1)}
                    disabled={data.first}
                  >
                    قبلی
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    صفحه {data.number + 1} از {data.totalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={data.last}
                  >
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
