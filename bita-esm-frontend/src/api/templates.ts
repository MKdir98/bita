import apiClient from './client'
import type { Page } from '@/types'

export interface EndpointTemplate {
  id: number
  name: string
  description?: string
  camelYaml?: string
  attachmentIds?: number[]
  configSchema?: object
  createdAt: string
}

export interface ComponentTemplate {
  id: number
  name: string
  description?: string
  componentType: string
  configSchema?: object
  createdAt: string
}

export interface RouteTemplate {
  id: number
  name: string
  description?: string
  version: string
  inputEndpointTemplateId: number
  inputEndpointTemplateName?: string
  outputEndpointTemplateId: number
  outputEndpointTemplateName?: string
  componentIds: number[]
  configSchema?: object
  createdAt: string
}

export interface CreateEndpointTemplateData {
  name: string
  description?: string
  camelYaml: string
  attachmentIds?: number[]
  configSchema?: object
}

export interface CreateComponentTemplateData {
  name: string
  description?: string
  componentType: string
  configSchema?: object
}

export interface CreateRouteTemplateData {
  name: string
  description?: string
  version: string
  inputEndpointTemplateId: number
  outputEndpointTemplateId: number
  componentIds?: number[]
  configSchema?: object
}

export const templatesApi = {
  // Endpoint Templates
  listEndpointTemplates: async (params?: { search?: string; page?: number; size?: number }): Promise<Page<EndpointTemplate>> => {
    const response = await apiClient.get('/api/v1/endpoint-templates', { params })
    return response.data
  },

  getEndpointTemplate: async (id: number): Promise<EndpointTemplate> => {
    const response = await apiClient.get(`/api/v1/endpoint-templates/${id}`)
    return response.data
  },

  createEndpointTemplate: async (data: CreateEndpointTemplateData): Promise<EndpointTemplate> => {
    const response = await apiClient.post('/api/v1/endpoint-templates', data)
    return response.data
  },

  updateEndpointTemplate: async (id: number, data: Partial<CreateEndpointTemplateData>): Promise<EndpointTemplate> => {
    const response = await apiClient.put(`/api/v1/endpoint-templates/${id}`, data)
    return response.data
  },

  deleteEndpointTemplate: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/endpoint-templates/${id}`)
  },

  // Component Templates
  listComponentTemplates: async (params?: { search?: string; page?: number; size?: number }): Promise<Page<ComponentTemplate>> => {
    const response = await apiClient.get('/api/v1/component-templates', { params })
    return response.data
  },

  getComponentTemplate: async (id: number): Promise<ComponentTemplate> => {
    const response = await apiClient.get(`/api/v1/component-templates/${id}`)
    return response.data
  },

  createComponentTemplate: async (data: CreateComponentTemplateData): Promise<ComponentTemplate> => {
    const response = await apiClient.post('/api/v1/component-templates', data)
    return response.data
  },

  updateComponentTemplate: async (id: number, data: Partial<CreateComponentTemplateData>): Promise<ComponentTemplate> => {
    const response = await apiClient.put(`/api/v1/component-templates/${id}`, data)
    return response.data
  },

  deleteComponentTemplate: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/component-templates/${id}`)
  },

  // Route Templates
  listRouteTemplates: async (params?: { search?: string; page?: number; size?: number }): Promise<Page<RouteTemplate>> => {
    const response = await apiClient.get('/api/v1/route-templates', { params })
    return response.data
  },

  getRouteTemplate: async (id: number): Promise<RouteTemplate> => {
    const response = await apiClient.get(`/api/v1/route-templates/${id}`)
    return response.data
  },

  createRouteTemplate: async (data: CreateRouteTemplateData): Promise<RouteTemplate> => {
    const response = await apiClient.post('/api/v1/route-templates', data)
    return response.data
  },

  updateRouteTemplate: async (id: number, data: Partial<CreateRouteTemplateData>): Promise<RouteTemplate> => {
    const response = await apiClient.put(`/api/v1/route-templates/${id}`, data)
    return response.data
  },

  deleteRouteTemplate: async (id: number): Promise<void> => {
    await apiClient.delete(`/api/v1/route-templates/${id}`)
  },
}
