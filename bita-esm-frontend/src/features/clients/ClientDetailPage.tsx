import { useParams, Link } from 'react-router-dom'
import { useClient, useClientCredentials, useRemoveCredential } from './useClients'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { formatDateTime } from '@/lib/utils'
import { Pencil, Plus, Trash2, Key, Shield, Mail, Phone, Tag } from 'lucide-react'

export default function ClientDetailPage() {
  const { id } = useParams<{ id: string }>()
  const clientId = Number(id)
  
  const { data: client, isLoading: clientLoading } = useClient(clientId)
  const { data: credentials, isLoading: credLoading } = useClientCredentials(clientId)
  const removeCredential = useRemoveCredential(clientId)

  const handleRemoveCredential = (credId: number) => {
    if (confirm('آیا از حذف این اعتبارنامه اطمینان دارید؟')) {
      removeCredential.mutate(credId)
    }
  }

  if (clientLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  if (!client) {
    return <div className="text-center py-8">سازمان یافت نشد</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{client.name}</h1>
          {client.description && (
            <p className="text-muted-foreground">{client.description}</p>
          )}
        </div>
        <Button asChild>
          <Link to={`/clients/${clientId}/edit`}>
            <Pencil className="w-4 h-4 ml-2" />
            ویرایش
          </Link>
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>اطلاعات سازمان</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <span className="text-muted-foreground text-sm">وضعیت</span>
              <div className="mt-1">
                <span
                  className={`inline-flex items-center px-2 py-1 rounded-full text-xs ${
                    client.active
                      ? 'bg-green-100 text-green-800'
                      : 'bg-red-100 text-red-800'
                  }`}
                >
                  {client.active ? 'فعال' : 'غیرفعال'}
                </span>
              </div>
            </div>

            {client.contactEmail && (
              <div className="flex items-center gap-2">
                <Mail className="w-4 h-4 text-muted-foreground" />
                <span>{client.contactEmail}</span>
              </div>
            )}

            {client.contactPhone && (
              <div className="flex items-center gap-2">
                <Phone className="w-4 h-4 text-muted-foreground" />
                <span dir="ltr">{client.contactPhone}</span>
              </div>
            )}

            {client.tags?.length > 0 && (
              <div>
                <div className="flex items-center gap-2 mb-2">
                  <Tag className="w-4 h-4 text-muted-foreground" />
                  <span className="text-muted-foreground text-sm">برچسب‌ها</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {client.tags.map((tag) => (
                    <span
                      key={tag.id || `${tag.key}-${tag.value}`}
                      className="px-2 py-1 bg-secondary text-secondary-foreground rounded-md text-sm"
                    >
                      <span className="font-medium">{tag.key}:</span> {tag.value}
                    </span>
                  ))}
                </div>
              </div>
            )}

            <div className="pt-4 border-t text-sm text-muted-foreground">
              <p>تاریخ ایجاد: {formatDateTime(client.createdAt)}</p>
              <p>آخرین به‌روزرسانی: {formatDateTime(client.updatedAt)}</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle>اعتبارنامه‌ها</CardTitle>
              <CardDescription>مدیریت اعتبارنامه‌های دسترسی</CardDescription>
            </div>
            <Button size="sm">
              <Plus className="w-4 h-4 ml-2" />
              افزودن
            </Button>
          </CardHeader>
          <CardContent>
            {credLoading ? (
              <div className="text-center py-4">در حال بارگذاری...</div>
            ) : !credentials?.length ? (
              <div className="text-center py-4">
                <Key className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
                <p className="text-muted-foreground text-sm">اعتبارنامه‌ای وجود ندارد</p>
              </div>
            ) : (
              <div className="space-y-3">
                {credentials.map((cred) => (
                  <div
                    key={cred.id}
                    className="flex items-center justify-between p-3 border rounded-lg"
                  >
                    <div className="flex items-center gap-3">
                      <Shield className="w-5 h-5 text-muted-foreground" />
                      <div>
                        <p className="font-medium">{cred.credentialType}</p>
                        <p className="text-sm text-muted-foreground">
                          {cred.credentialValue.substring(0, 20)}...
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-2 py-1 rounded-full text-xs ${
                          cred.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {cred.active ? 'فعال' : 'غیرفعال'}
                      </span>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleRemoveCredential(cred.id)}
                      >
                        <Trash2 className="w-4 h-4 text-destructive" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
