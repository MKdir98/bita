import apiClient from './client'
import type { Client, Credential, Page, Tag } from '@/types'

export interface CreateClientData {
  name: string
  description?: string
  contactEmail?: string
  contactPhone?: string
  tags?: Tag[]
}

export interface UpdateClientData extends Partial<CreateClientData> {}

export interface AddCredentialData {
  credentialType: string
  credentialValue: string
  secret?: string
  description?: string
  expiresAt?: string
}

export const clientsApi = {
  list: async (params?: {
    search?: string
    page?: number
    size?: number
  }): Promise<Page<Client>> => {
    const response = await apiClient.get('/api/v1/clients', { params })
    return response.data
  },

  get: async (id: number): Promise<Client> => {
    const response = await apiClient.get(`/api/v1/clients/${id}`)
    return response.data
  },

  create: async (data: CreateClientData): Promise<Client> => {
    const response = await apiClient.post('/api/v1/clients', data)
    return response.data
  },

  update: async (id: number, data: UpdateClientData): Promise<Client> => {
    const response = await apiClient.put(`/api/v1/clients/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/clients/${id}`)
  },

  getCredentials: async (clientId: number): Promise<Credential[]> => {
    const response = await apiClient.get(`/api/v1/clients/${clientId}/credentials`)
    return response.data
  },

  addCredential: async (clientId: number, data: AddCredentialData): Promise<Credential> => {
    const response = await apiClient.post(`/api/v1/clients/${clientId}/credentials`, data)
    return response.data
  },

  removeCredential: async (clientId: number, credentialId: number): Promise<void> => {
    await apiClient.delete(`/api/v1/clients/${clientId}/credentials/${credentialId}`)
  },
}
