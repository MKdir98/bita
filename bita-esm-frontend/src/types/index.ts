// Auth Types
export interface User {
  id: number
  mobileNumber: string
  fullName: string
  roles: string[]
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  user: User
}

// Tag Types
export interface Tag {
  id?: number
  key: string
  value: string
}

// Client Types
export interface Client {
  id: number
  name: string
  description?: string
  contactEmail?: string
  contactPhone?: string
  tags: Tag[]
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface Credential {
  id: number
  credentialType: CredentialType
  credentialValue: string
  description?: string
  active: boolean
  expiresAt?: string
  createdAt: string
}

export type CredentialType = 'IP_ADDRESS' | 'X509_CERTIFICATE' | 'API_KEY' | 'OAUTH2' | 'BASIC_AUTH'

// Service Types
export interface ServiceCollection {
  id: number
  name: string
  basePath: string
  description?: string
  servicesCount: number
}

export interface Service {
  id: number
  name: string
  version: string
  description?: string
  phase: ServicePhase
  fullPath: string
  minReplicas: number
  maxReplicas: number
  targetCpuPercent: number
  k8sDeploymentName?: string
  k8sServiceName?: string
}

export type ServicePhase = 'DRAFT' | 'TEST' | 'ACTIVE'

// Pagination
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// API Error
export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
}
