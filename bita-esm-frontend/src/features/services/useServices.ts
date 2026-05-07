import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { servicesApi, CreateServiceCollectionData, CreateServiceData, UpdateServiceData, ChangePhaseData } from '@/api/services'
import { useToast } from '@/hooks/use-toast'
import type { ServicePhase } from '@/types'

// Service Collections
export function useServiceCollections(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['serviceCollections', params],
    queryFn: () => servicesApi.listCollections(params),
  })
}

export function useServiceCollection(id: number) {
  return useQuery({
    queryKey: ['serviceCollections', id],
    queryFn: () => servicesApi.getCollection(id),
    enabled: !!id,
  })
}

export function useCreateServiceCollection() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateServiceCollectionData) => servicesApi.createCollection(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['serviceCollections'] })
      toast({ title: 'مجموعه سرویس ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد مجموعه سرویس' })
    },
  })
}

export function useUpdateServiceCollection(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: Partial<CreateServiceCollectionData>) => servicesApi.updateCollection(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['serviceCollections'] })
      toast({ title: 'مجموعه سرویس به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteServiceCollection() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => servicesApi.deleteCollection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['serviceCollections'] })
      toast({ title: 'مجموعه سرویس حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف' })
    },
  })
}

// Services
export function useServices(params?: { collectionId?: number; phase?: ServicePhase; search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['services', params],
    queryFn: () => servicesApi.listServices(params),
  })
}

export function useService(id: number) {
  return useQuery({
    queryKey: ['services', id],
    queryFn: () => servicesApi.getService(id),
    enabled: !!id,
  })
}

export function useCreateService() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateServiceData) => servicesApi.createService(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      toast({ title: 'سرویس ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد سرویس' })
    },
  })
}

export function useUpdateService(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: UpdateServiceData) => servicesApi.updateService(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.invalidateQueries({ queryKey: ['services', id] })
      toast({ title: 'سرویس به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteService() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => servicesApi.deleteService(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      toast({ title: 'سرویس حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف سرویس' })
    },
  })
}

export function useChangeServicePhase(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: ChangePhaseData) => servicesApi.changePhase(id, data),
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['services'] })
      queryClient.invalidateQueries({ queryKey: ['services', id] })
      toast({ title: `فاز سرویس به ${data.phase} تغییر کرد` })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در تغییر فاز' })
    },
  })
}

// Service Access
export function useServiceAccess(serviceId: number) {
  return useQuery({
    queryKey: ['services', serviceId, 'access'],
    queryFn: () => servicesApi.getServiceAccess(serviceId),
    enabled: !!serviceId,
  })
}

export function useGrantAccess(serviceId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: { clientId: number; rateLimit?: number; rateLimitWindow?: string; expiresAt?: string }) =>
      servicesApi.grantAccess(serviceId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services', serviceId, 'access'] })
      toast({ title: 'دسترسی اعطا شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در اعطای دسترسی' })
    },
  })
}

export function useRevokeAccess(serviceId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (clientId: number) => servicesApi.revokeAccess(serviceId, clientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['services', serviceId, 'access'] })
      toast({ title: 'دسترسی لغو شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در لغو دسترسی' })
    },
  })
}
