import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { templatesApi, CreateEndpointTemplateData, CreateComponentTemplateData, CreateRouteTemplateData } from '@/api/templates'
import { useToast } from '@/hooks/use-toast'

// Endpoint Templates
export function useEndpointTemplates(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['endpointTemplates', params],
    queryFn: () => templatesApi.listEndpointTemplates(params),
  })
}

export function useEndpointTemplate(id: number) {
  return useQuery({
    queryKey: ['endpointTemplates', id],
    queryFn: () => templatesApi.getEndpointTemplate(id),
    enabled: !!id,
  })
}

export function useCreateEndpointTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateEndpointTemplateData) => templatesApi.createEndpointTemplate(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endpointTemplates'] })
      toast({ title: 'قالب Endpoint ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد قالب' })
    },
  })
}

export function useUpdateEndpointTemplate(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: Partial<CreateEndpointTemplateData>) => templatesApi.updateEndpointTemplate(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endpointTemplates'] })
      toast({ title: 'قالب به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteEndpointTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => templatesApi.deleteEndpointTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['endpointTemplates'] })
      toast({ title: 'قالب حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف' })
    },
  })
}

// Component Templates
export function useComponentTemplates(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['componentTemplates', params],
    queryFn: () => templatesApi.listComponentTemplates(params),
  })
}

export function useComponentTemplate(id: number) {
  return useQuery({
    queryKey: ['componentTemplates', id],
    queryFn: () => templatesApi.getComponentTemplate(id),
    enabled: !!id,
  })
}

export function useCreateComponentTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateComponentTemplateData) => templatesApi.createComponentTemplate(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['componentTemplates'] })
      toast({ title: 'قالب Component ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد قالب' })
    },
  })
}

export function useUpdateComponentTemplate(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: Partial<CreateComponentTemplateData>) => templatesApi.updateComponentTemplate(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['componentTemplates'] })
      toast({ title: 'قالب به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteComponentTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => templatesApi.deleteComponentTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['componentTemplates'] })
      toast({ title: 'قالب حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف' })
    },
  })
}

// Route Templates
export function useRouteTemplates(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['routeTemplates', params],
    queryFn: () => templatesApi.listRouteTemplates(params),
  })
}

export function useRouteTemplate(id: number) {
  return useQuery({
    queryKey: ['routeTemplates', id],
    queryFn: () => templatesApi.getRouteTemplate(id),
    enabled: !!id,
  })
}

export function useCreateRouteTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateRouteTemplateData) => templatesApi.createRouteTemplate(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routeTemplates'] })
      toast({ title: 'قالب Route ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد قالب' })
    },
  })
}

export function useUpdateRouteTemplate(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: Partial<CreateRouteTemplateData>) => templatesApi.updateRouteTemplate(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routeTemplates'] })
      toast({ title: 'قالب به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteRouteTemplate() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => templatesApi.deleteRouteTemplate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routeTemplates'] })
      toast({ title: 'قالب حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف' })
    },
  })
}
