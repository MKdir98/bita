import apiClient from './client'
import type { Page } from '@/types'

export interface User {
  id: number
  mobileNumber: string
  fullName?: string
  email?: string
  active: boolean
  roles: string[]
  createdAt: string
  updatedAt: string
}

export interface Role {
  id: number
  name: string
  description?: string
  permissions: string[]
}

export interface CreateUserData {
  mobileNumber: string
  fullName?: string
  email?: string
  roleIds: number[]
}

export interface UpdateUserData {
  fullName?: string
  email?: string
  active?: boolean
  roleIds?: number[]
}

export const usersApi = {
  list: async (params?: { search?: string; page?: number; size?: number }): Promise<Page<User>> => {
    const response = await apiClient.get('/api/v1/users', { params })
    return response.data
  },

  get: async (id: number): Promise<User> => {
    const response = await apiClient.get(`/api/v1/users/${id}`)
    return response.data
  },

  create: async (data: CreateUserData): Promise<User> => {
    const response = await apiClient.post('/api/v1/users', data)
    return response.data
  },

  update: async (id: number, data: UpdateUserData): Promise<User> => {
    const response = await apiClient.put(`/api/v1/users/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/users/${id}`)
  },

  listRoles: async (): Promise<Role[]> => {
    const response = await apiClient.get('/api/v1/roles')
    return response.data
  },

  getProfile: async (): Promise<User> => {
    const response = await apiClient.get('/api/v1/users/profile')
    return response.data
  },

  updateProfile: async (data: { fullName?: string; email?: string }): Promise<User> => {
    const response = await apiClient.put('/api/v1/users/profile', data)
    return response.data
  },
}
