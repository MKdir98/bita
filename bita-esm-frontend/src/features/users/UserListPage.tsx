import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useUsers, useDeleteUser } from './useUsers'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { formatDate } from '@/lib/utils'
import { Plus, Search, Eye, Pencil, Trash2, Users } from 'lucide-react'

export default function UserListPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useUsers({ search, page, size: 10 })
  const deleteUser = useDeleteUser()

  const handleDelete = (id: number) => {
    if (confirm('آیا از حذف این کاربر اطمینان دارید؟')) {
      deleteUser.mutate(id)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">کاربران</h1>
          <p className="text-muted-foreground">مدیریت کاربران سیستم</p>
        </div>
        <Button asChild>
          <Link to="/users/new">
            <Plus className="w-4 h-4 ml-2" />
            کاربر جدید
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
              <Users className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
              <p className="text-muted-foreground">کاربری یافت نشد</p>
            </div>
          ) : (
            <div className="space-y-4">
              <table className="w-full">
                <thead>
                  <tr className="border-b">
                    <th className="text-right py-3 px-4">نام</th>
                    <th className="text-right py-3 px-4">شماره موبایل</th>
                    <th className="text-right py-3 px-4">نقش‌ها</th>
                    <th className="text-right py-3 px-4">وضعیت</th>
                    <th className="text-right py-3 px-4">عملیات</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((user) => (
                    <tr key={user.id} className="border-b hover:bg-muted/50">
                      <td className="py-3 px-4 font-medium">{user.fullName || '-'}</td>
                      <td className="py-3 px-4 text-muted-foreground" dir="ltr">
                        {user.mobileNumber}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {user.roles.map((role) => (
                            <span key={role} className="text-xs bg-secondary px-2 py-0.5 rounded">
                              {role}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <span
                          className={`px-2 py-1 rounded-full text-xs ${
                            user.active
                              ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                              : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                          }`}
                        >
                          {user.active ? 'فعال' : 'غیرفعال'}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center gap-2">
                          <Button variant="ghost" size="icon" asChild>
                            <Link to={`/users/${user.id}/edit`}>
                              <Pencil className="w-4 h-4" />
                            </Link>
                          </Button>
                          <Button variant="ghost" size="icon" onClick={() => handleDelete(user.id)}>
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
