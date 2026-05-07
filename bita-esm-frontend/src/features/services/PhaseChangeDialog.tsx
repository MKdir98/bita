import { useState } from 'react'
import { useChangeServicePhase } from './useServices'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import type { ServicePhase } from '@/types'

interface PhaseChangeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  serviceId: number
  currentPhase: ServicePhase
}

const phases: { value: ServicePhase; label: string; description: string }[] = [
  { value: 'DRAFT', label: 'پیش‌نویس', description: 'سرویس در حال توسعه است' },
  { value: 'TEST', label: 'تست', description: 'سرویس در حال تست است' },
  { value: 'ACTIVE', label: 'فعال', description: 'سرویس در محیط Production فعال است' },
]

export default function PhaseChangeDialog({
  open,
  onOpenChange,
  serviceId,
  currentPhase,
}: PhaseChangeDialogProps) {
  const [selectedPhase, setSelectedPhase] = useState<ServicePhase>(currentPhase)
  const changePhase = useChangeServicePhase(serviceId)

  if (!open) return null

  const handleSubmit = async () => {
    if (selectedPhase !== currentPhase) {
      await changePhase.mutateAsync({ newPhase: selectedPhase })
    }
    onOpenChange(false)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>تغییر فاز سرویس</CardTitle>
          <CardDescription>فاز جدید سرویس را انتخاب کنید</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            {phases.map((phase) => (
              <button
                key={phase.value}
                type="button"
                onClick={() => setSelectedPhase(phase.value)}
                className={`w-full p-4 text-right rounded-lg border transition-colors ${
                  selectedPhase === phase.value
                    ? 'border-primary bg-primary/5'
                    : 'border-border hover:border-primary/50'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-medium">{phase.label}</span>
                  {currentPhase === phase.value && (
                    <span className="text-xs bg-secondary px-2 py-1 rounded">فعلی</span>
                  )}
                </div>
                <p className="text-sm text-muted-foreground mt-1">{phase.description}</p>
              </button>
            ))}
          </div>

          {selectedPhase === 'ACTIVE' && currentPhase !== 'ACTIVE' && (
            <div className="p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg text-sm text-yellow-800 dark:text-yellow-200">
              توجه: فعال‌سازی سرویس باعث استقرار آن در Kubernetes می‌شود.
            </div>
          )}

          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              انصراف
            </Button>
            <Button
              onClick={handleSubmit}
              disabled={selectedPhase === currentPhase || changePhase.isPending}
            >
              {changePhase.isPending ? 'در حال تغییر...' : 'تایید'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
