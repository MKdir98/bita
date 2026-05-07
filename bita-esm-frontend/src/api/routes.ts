import apiClient from './client'
import type { Page } from '@/types'

export interface RouteTemplate {
  id: number
  name: string
  description?: string
  version: string
  inputEndpointTemplateId: number
  outputEndpointTemplateId: number
  configSchema?: object
}

export interface Route {
  id: number
  name: string
  description?: string
  serviceId: number
  serviceName?: string
  routeTemplateId: number
  templateName?: string
  config: object
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateRouteData {
  serviceId: number
  routeTemplateId: number
  name: string
  description?: string
  config: object
}

export interface UpdateRouteData {
  name?: string
  description?: string
  config?: object
  active?: boolean
}

export interface RouteFile {
  id: number
  routeId: number
  fileName: string
  fileType: string
  fileSize: number
  createdAt: string
}

export const routesApi = {
  list: async (params?: {
    serviceId?: number
    search?: string
    page?: number
    size?: number
  }): Promise<Page<Route>> => {
    const response = await apiClient.get('/api/v1/routes', { params })
    return response.data
  },

  get: async (id: number): Promise<Route> => {
    const response = await apiClient.get(`/api/v1/routes/${id}`)
    return response.data
  },

  create: async (data: CreateRouteData): Promise<Route> => {
    const response = await apiClient.post('/api/v1/routes', data)
    return response.data
  },

  update: async (id: number, data: UpdateRouteData): Promise<Route> => {
    const response = await apiClient.put(`/api/v1/routes/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/routes/${id}`)
  },

  getYaml: async (id: number): Promise<string> => {
    const response = await apiClient.get(`/api/v1/routes/${id}/yaml`)
    return response.data
  },

  // Route Files
  getFiles: async (routeId: number): Promise<RouteFile[]> => {
    const response = await apiClient.get(`/api/v1/routes/${routeId}/files`)
    return response.data
  },

  uploadFile: async (routeId: number, file: File, fileType: string): Promise<RouteFile> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('fileType', fileType)
    const response = await apiClient.post(`/api/v1/routes/${routeId}/files`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return response.data
  },

  deleteFile: async (routeId: number, fileId: number): Promise<void> => {
    await apiClient.delete(`/api/v1/routes/${routeId}/files/${fileId}`)
  },

  downloadFile: async (routeId: number, fileId: number): Promise<Blob> => {
    const response = await apiClient.get(`/api/v1/routes/${routeId}/files/${fileId}/download`, {
      responseType: 'blob',
    })
    return response.data
  },
}
