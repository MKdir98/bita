import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Wrench, Check, X, Loader2 } from 'lucide-react'
import type { ToolExecution } from '@/api/chat'

interface ToolConfirmDialogProps {
  execution: ToolExecution | null
  onConfirm: () => void
  onReject: () => void
  isLoading: boolean
}

const toolDescriptions: Record<string, string> = {
  ListClientsTool: 'نمایش لیست سازمان‌ها',
  GetClientTool: 'دریافت اطلاعات سازمان',
  CreateServiceTool: 'ایجاد سرویس جدید',
  CreateRouteTool: 'ایجاد مسیر جدید',
  ListTemplatesTool: 'نمایش لیست قالب‌ها',
  ListServicesTool: 'نمایش لیست سرویس‌ها',
  endpoint_template: 'ایجاد قالب Endpoint',
  component_template: 'ایجاد قالب Component',
  route_template: 'ایجاد قالب Route',
  endpoint_instance: 'ایجاد نمونه Endpoint',
  component_instance: 'ایجاد نمونه Component',
  route_instance: 'ایجاد نمونه Route',
  create_route: 'ایجاد مسیر',
  complete: 'اتمام کار',
}

export default function ToolConfirmDialog({
  execution,
  onConfirm,
  onReject,
  isLoading,
}: ToolConfirmDialogProps) {
  if (!execution) return null

  const isDestructive =
    execution.toolName.includes('Create') ||
    execution.toolName.includes('Update') ||
    execution.toolName.includes('Delete') ||
    ['endpoint_template', 'component_template', 'route_template', 'endpoint_instance', 'component_instance', 'route_instance', 'create_route'].includes(execution.toolName)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-yellow-100 dark:bg-yellow-900 flex items-center justify-center">
              <Wrench className="w-5 h-5 text-yellow-800 dark:text-yellow-200" />
            </div>
            <div>
              <CardTitle>تایید اجرای عملیات</CardTitle>
              <CardDescription>
                {toolDescriptions[execution.toolName] || execution.toolName}
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="p-3 bg-muted rounded-lg">
            <p className="text-sm font-medium mb-2">پارامترها:</p>
            <pre className="text-xs overflow-auto max-h-32" dir="ltr">
              {JSON.stringify(execution.arguments, null, 2)}
            </pre>
          </div>

          {isDestructive && (
            <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg text-sm text-yellow-800 dark:text-yellow-200">
              این عملیات تغییراتی در سیستم ایجاد می‌کند. آیا مطمئن هستید؟
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={onReject} disabled={isLoading}>
              <X className="w-4 h-4 ml-2" />
              انصراف
            </Button>
            <Button onClick={onConfirm} disabled={isLoading}>
              {isLoading ? (
                <Loader2 className="w-4 h-4 ml-2 animate-spin" />
              ) : (
                <Check className="w-4 h-4 ml-2" />
              )}
              تایید و اجرا
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
