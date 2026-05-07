import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { usersApi, CreateUserData, UpdateUserData } from '@/api/users'
import { useToast } from '@/hooks/use-toast'

export function useUsers(params?: { search?: string; page?: number; size?: number }) {
  return useQuery({
    queryKey: ['users', params],
    queryFn: () => usersApi.list(params),
  })
}

export function useUser(id: number) {
  return useQuery({
    queryKey: ['users', id],
    queryFn: () => usersApi.get(id),
    enabled: !!id,
  })
}

export function useRoles() {
  return useQuery({
    queryKey: ['roles'],
    queryFn: () => usersApi.listRoles(),
  })
}

export function useCreateUser() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: CreateUserData) => usersApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast({ title: 'کاربر ایجاد شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در ایجاد کاربر' })
    },
  })
}

export function useUpdateUser(id: number) {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: UpdateUserData) => usersApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      queryClient.invalidateQueries({ queryKey: ['users', id] })
      toast({ title: 'کاربر به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی' })
    },
  })
}

export function useDeleteUser() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (id: number) => usersApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      toast({ title: 'کاربر حذف شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در حذف کاربر' })
    },
  })
}

export function useProfile() {
  return useQuery({
    queryKey: ['profile'],
    queryFn: () => usersApi.getProfile(),
  })
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  const { toast } = useToast()

  return useMutation({
    mutationFn: (data: { fullName?: string; email?: string }) => usersApi.updateProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['profile'] })
      toast({ title: 'پروفایل به‌روزرسانی شد' })
    },
    onError: () => {
      toast({ variant: 'destructive', title: 'خطا در به‌روزرسانی پروفایل' })
    },
  })
}
