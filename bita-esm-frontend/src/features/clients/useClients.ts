import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { clientsApi, CreateClientData, UpdateClientData, AddCredentialData } from '@/api/clients'
import { useToast } from '@/hooks/use-toast'

export function useClients(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['clients', params],
    queryFn: () => clientsApi.list(params),
  })
}

export function useClient(id: number) {
  return useQuery({
    queryKey: ['clients', id],
    queryFn: () => clientsApi.get(id),
    enabled: !!id,
  })
}

export function useClientCredentials(clientId: number) {
  return useQuery({
    queryKey: ['clients', clientId, 'credentials'],
    queryFn: () => clientsApi.getCredentials(clientId),
    enabled: !!clientId,
  })
}

export function useCreateClient() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateClientData) => clientsApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      toast({
        title: 'سازمان ایجاد شد',
        description: 'سازمان جدید با موفقیت ایجاد گردید',
      })
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'خطا در ایجاد سازمان',
      })
    },
  })
}

export function useUpdateClient(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: UpdateClientData) => clientsApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      queryClient.invalidateQueries({ queryKey: ['clients', id] })
      toast({
        title: 'سازمان به‌روزرسانی شد',
        description: 'تغییرات با موفقیت ذخیره گردید',
      })
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'خطا در به‌روزرسانی سازمان',
      })
    },
  })
}

export function useDeleteClient() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => clientsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients'] })
      toast({
        title: 'سازمان حذف شد',
        description: 'سازمان با موفقیت حذف گردید',
      })
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'خطا در حذف سازمان',
      })
    },
  })
}

export function useAddCredential(clientId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: AddCredentialData) => clientsApi.addCredential(clientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients', clientId, 'credentials'] })
      toast({
        title: 'اعتبارنامه اضافه شد',
        description: 'اعتبارنامه جدید با موفقیت اضافه گردید',
      })
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'خطا در افزودن اعتبارنامه',
      })
    },
  })
}

export function useRemoveCredential(clientId: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (credentialId: number) => clientsApi.removeCredential(clientId, credentialId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clients', clientId, 'credentials'] })
      toast({
        title: 'اعتبارنامه حذف شد',
        description: 'اعتبارنامه با موفقیت حذف گردید',
      })
    },
    onError: () => {
      toast({
        variant: 'destructive',
        title: 'خطا',
        description: 'خطا در حذف اعتبارنامه',
      })
    },
  })
}
