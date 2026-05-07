import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { routesApi, CreateRouteData, UpdateRouteData } from '@/api/routes'
import { useToast } from '@/hooks/use-toast'

export function useRoutes(params?: { serviceId?: number; search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['routes', params],
    queryFn: () => routesApi.list(params),
  })
}

export function useRoute(id: number) {
  return useQuery({
    queryKey: ['routes', id],
    queryFn: () => routesApi.get(id),
    enabled: !!id,
  })
}

export function useRouteYaml(id: number) {
  return useQuery({
    queryKey: ['routes', id, 'yaml'],
    queryFn: () => routesApi.getYaml(id),
    enabled: !!id,
  })
}

export function useCreateRoute() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateRouteData) => routesApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes'] })
      toast({ title: 'مسیر ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد مسیر' })
    },
  })
}

export function useUpdateRoute(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: UpdateRouteData) => routesApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes'] })
      queryClient.invalidateQueries({ queryKey: ['routes', id] })
      toast({ title: 'مسیر به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteRoute() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => routesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes'] })
      toast({ title: 'مسیر حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف مسیر' })
    },
  })
}

// Route Files
export function useRouteFiles(routeId: number) {
  return useQuery({
    queryKey: ['routes', routeId, 'files'],
    queryFn: () => routesApi.getFiles(routeId),
    enabled: !!routeId,
  })
}

export function useUploadRouteFile(routeId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: ({ file, fileType }: { file: File; fileType: string }) =>
      routesApi.uploadFile(routeId, file, fileType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes', routeId, 'files'] })
      toast({ title: 'فایل آپلود شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در آپلود فایل' })
    },
  })
}

export function useDeleteRouteFile(routeId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (fileId: number) => routesApi.deleteFile(routeId, fileId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes', routeId, 'files'] })
      toast({ title: 'فایل حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف فایل' })
    },
  })
}
