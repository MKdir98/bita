import apiClient from './client'

export interface AuditRevisionEntry {
  revisionId: number
  revisionDate: string
  revisionType: string
  userId?: number
  username?: string
  entityData: Record<string, unknown>
}

export interface AuditHistoryResponse {
  entityType: string
  entityId: number
  totalRevisions: number
  revisions: AuditRevisionEntry[]
}

export const getAuditHistory = async (
  entityType: string,
  id: number
): Promise<AuditHistoryResponse> => {
  const response = await apiClient.get(`/api/v1/audit/${entityType}/${id}/history`)
  return response.data
}
