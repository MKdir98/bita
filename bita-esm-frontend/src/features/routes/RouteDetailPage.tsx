import { useParams, Link } from 'react-router-dom'
import { useRoute, useRouteYaml, useRouteFiles, useDeleteRouteFile } from './useRoutes'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { formatDateTime } from '@/lib/utils'
import { Pencil, FileCode, Upload, Trash2, Download, File } from 'lucide-react'
import FileUploader from './FileUploader'
import RouteYamlViewer from './RouteYamlViewer'

export default function RouteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const routeId = Number(id)

  const { data: route, isLoading } = useRoute(routeId)
  const { data: yaml } = useRouteYaml(routeId)
  const { data: files } = useRouteFiles(routeId)
  const deleteFile = useDeleteRouteFile(routeId)

  const handleDeleteFile = (fileId: number) => {
    if (confirm('آیا از حذف این فایل اطمینان دارید؟')) {
      deleteFile.mutate(fileId)
    }
  }

  if (isLoading) {
    return <div className="text-center py-8">در حال بارگذاری...</div>
  }

  if (!route) {
    return <div className="text-center py-8">مسیر یافت نشد</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{route.name}</h1>
          <p className="text-muted-foreground">قالب: {route.templateName}</p>
        </div>
        <Button asChild>
          <Link to={`/routes/${routeId}/edit`}>
            <Pencil className="w-4 h-4 ml-2" />
            ویرایش
          </Link>
        </Button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>اطلاعات مسیر</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <span className="text-muted-foreground text-sm">وضعیت</span>
                <div className="mt-1">
                  <span
                    className={`px-2 py-1 rounded-full text-xs ${
                      route.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}
                  >
                    {route.active ? 'فعال' : 'غیرفعال'}
                  </span>
                </div>
              </div>
              <div>
                <span className="text-muted-foreground text-sm">سرویس</span>
                <p className="mt-1">{route.serviceName}</p>
              </div>
            </div>

            {route.description && (
              <div>
                <span className="text-muted-foreground text-sm">توضیحات</span>
                <p className="mt-1">{route.description}</p>
              </div>
            )}

            <div>
              <span className="text-muted-foreground text-sm">پیکربندی</span>
              <pre className="mt-1 p-3 bg-muted rounded-lg text-sm overflow-auto max-h-48" dir="ltr">
                {JSON.stringify(route.config, null, 2)}
              </pre>
            </div>

            <div className="pt-4 border-t text-sm text-muted-foreground">
              <p>تاریخ ایجاد: {formatDateTime(route.createdAt)}</p>
              <p>آخرین به‌روزرسانی: {formatDateTime(route.updatedAt)}</p>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle className="flex items-center gap-2">
                <File className="w-5 h-5" />
                فایل‌ها
              </CardTitle>
              <CardDescription>فایل‌های WSDL، XSD و ...</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <FileUploader routeId={routeId} />
            
            {!files?.length ? (
              <div className="text-center py-4 mt-4">
                <File className="w-8 h-8 mx-auto text-muted-foreground mb-2" />
                <p className="text-muted-foreground text-sm">فایلی آپلود نشده</p>
              </div>
            ) : (
              <div className="space-y-2 mt-4">
                {files.map((file) => (
                  <div key={file.id} className="flex items-center justify-between p-3 border rounded-lg">
                    <div className="flex items-center gap-3">
                      <FileCode className="w-5 h-5 text-muted-foreground" />
                      <div>
                        <p className="font-medium">{file.fileName}</p>
                        <p className="text-sm text-muted-foreground">
                          {file.fileType} - {(file.fileSize / 1024).toFixed(1)} KB
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button variant="ghost" size="icon">
                        <Download className="w-4 h-4" />
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => handleDeleteFile(file.id)}>
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

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileCode className="w-5 h-5" />
            خروجی YAML
          </CardTitle>
          <CardDescription>تعریف مسیر برای ESB Core</CardDescription>
        </CardHeader>
        <CardContent>
          <RouteYamlViewer yaml={yaml || ''} />
        </CardContent>
      </Card>
    </div>
  )
}
