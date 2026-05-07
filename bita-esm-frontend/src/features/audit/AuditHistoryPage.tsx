import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getAuditHistory } from '@/api/audit'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { formatDateTime } from '@/lib/utils'
import { ArrowRight, History } from 'lucide-react'

const ENTITY_TYPE_LABELS: Record<string, string> = {
  client: 'سازمان',
  credential: 'اعتبارنامه',
  service: 'سرویس',
  'service-collection': 'مجموعه سرویس',
  'service-access': 'دسترسی سرویس',
  route: 'مسیر',
}

const REVISION_TYPE_LABELS: Record<string, string> = {
  ADD: 'ایجاد',
  MOD: 'ویرایش',
  DEL: 'حذف',
}

const LIST_PATHS: Record<string, string> = {
  client: '/clients',
  credential: '/clients',
  service: '/services',
  'service-collection': '/services/collections',
  'service-access': '/services',
  route: '/routes',
}

export default function AuditHistoryPage() {
  const { entityType, id } = useParams<{ entityType: string; id: string }>()
  const entityId = id ? Number(id) : 0

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', entityType, entityId],
    queryFn: () => getAuditHistory(entityType!, entityId),
    enabled: !!entityType && !!entityId,
  })

  if (!entityType || !entityId) {
    return (
      <div className="space-y-6">
        <p className="text-muted-foreground">پارامترهای نامعتبر</p>
        <Button asChild variant="outline">
          <Link to="/">بازگشت</Link>
        </Button>
      </div>
    )
  }

  if (isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  if (error) {
    return (
      <div className="space-y-6">
        <p className="text-destructive">خطا در بارگذاری تاریخچه</p>
        <Button asChild variant="outline">
          <Link to={LIST_PATHS[entityType] || '/'}>بازگشت</Link>
        </Button>
      </div>
    )
  }

  if (!data) {
    return null
  }

  const entityLabel = ENTITY_TYPE_LABELS[entityType] || entityType
  const backPath = LIST_PATHS[entityType] || '/'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <History className="w-8 h-8" />
            تاریخچه تغییرات
          </h1>
          <p className="text-muted-foreground mt-1">
            {entityLabel} - شناسه {data.entityId}
          </p>
        </div>
        <Button asChild variant="outline">
          <Link to={backPath}>
            <ArrowRight className="w-4 h-4 ml-2" />
            بازگشت به لیست
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>تعداد {data.totalRevisions} تغییر</CardTitle>
        </CardHeader>
        <CardContent>
          {data.revisions.length === 0 ? (
            <p className="text-muted-foreground text-center py-8">تغییری یافت نشد</p>
          ) : (
            <div className="space-y-4">
              {data.revisions.map((revision) => (
                <details
                  key={revision.revisionId}
                  className="group border rounded-lg p-4 hover:bg-muted/30"
                >
                  <summary className="cursor-pointer flex items-center justify-between list-none">
                    <div className="flex items-center gap-4">
                      <span
                        className={`px-2 py-1 rounded-full text-xs font-medium ${
                          revision.revisionType === 'ADD'
                            ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
                            : revision.revisionType === 'DEL'
                              ? 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
                              : 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
                        }`}
                      >
                        {REVISION_TYPE_LABELS[revision.revisionType] || revision.revisionType}
                      </span>
                      <span className="text-muted-foreground">
                        {formatDateTime(revision.revisionDate)}
                      </span>
                      {revision.username && (
                        <span className="text-sm">{revision.username}</span>
                      )}
                    </div>
                  </summary>
                  <div className="mt-4 pt-4 border-t">
                    <pre className="text-xs overflow-auto max-h-64 p-4 bg-muted rounded-md">
                      {JSON.stringify(revision.entityData, null, 2)}
                    </pre>
                  </div>
                </details>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
