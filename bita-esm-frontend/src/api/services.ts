import apiClient from './client'
import type { Page, Service, ServiceCollection, ServicePhase } from '@/types'

export interface CreateServiceCollectionData {
  name: string
  basePath: string
  description?: string
}

export interface CreateServiceData {
  collectionId: number
  name: string
  version: string
  description?: string
}

export interface UpdateServiceData {
  name?: string
  version?: string
  description?: string
  minReplicas?: number
  maxReplicas?: number
  targetCpuPercent?: number
}

export interface ChangePhaseData {
  newPhase: ServicePhase
}

export const servicesApi = {
  // Service Collections
  listCollections: async (params?: {
    search?: string
    page?: number
    size?: number
  }): Promise<Page<ServiceCollection>> => {
    const response = await apiClient.get('/api/v1/service-collections', { params })
    return response.data
  },

  getCollection: async (id: number): Promise<ServiceCollection> => {
    const response = await apiClient.get(`/api/v1/service-collections/${id}`)
    return response.data
  },

  createCollection: async (data: CreateServiceCollectionData): Promise<ServiceCollection> => {
    const response = await apiClient.post('/api/v1/service-collections', data)
    return response.data
  },

  updateCollection: async (id: number, data: Partial<CreateServiceCollectionData>): Promise<ServiceCollection> => {
    const response = await apiClient.put(`/api/v1/service-collections/${id}`, data)
    return response.data
  },

  deleteCollection: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/service-collections/${id}`)
  },

  // Services
  listServices: async (params?: {
    collectionId?: number
    phase?: ServicePhase
    search?: string
    page?: number
    size?: number
  }): Promise<Page<Service>> => {
    const response = await apiClient.get('/api/v1/services', { params })
    return response.data
  },

  getService: async (id: number): Promise<Service> => {
    const response = await apiClient.get(`/api/v1/services/${id}`)
    return response.data
  },

  createService: async (data: CreateServiceData): Promise<Service> => {
    const response = await apiClient.post('/api/v1/services', data)
    return response.data
  },

  updateService: async (id: number, data: UpdateServiceData): Promise<Service> => {
    const response = await apiClient.put(`/api/v1/services/${id}`, data)
    return response.data
  },

  deleteService: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/services/${id}`)
  },

  changePhase: async (id: number, data: ChangePhaseData): Promise<Service> => {
    const response = await apiClient.post(`/api/v1/services/${id}/change-phase`, data)
    return response.data
  },

  // Service Access
  getServiceAccess: async (serviceId: number) => {
    const response = await apiClient.get(`/api/v1/services/${serviceId}/access`)
    return response.data
  },

  grantAccess: async (serviceId: number, data: {
    clientId: number
    rateLimit?: number
    rateLimitWindow?: string
    expiresAt?: string
  }) => {
    const response = await apiClient.post(`/api/v1/services/${serviceId}/access`, data)
    return response.data
  },

  revokeAccess: async (serviceId: number, clientId: number) => {
    await apiClient.delete(`/api/v1/services/${serviceId}/access/${clientId}`)
  },
}
