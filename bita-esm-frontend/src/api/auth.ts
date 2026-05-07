import apiClient from './client'
import type { AuthResponse } from '@/types'

// Login with password
export interface LoginData {
  mobileNumber: string
  password: string
}

// Registration flow
export interface RegisterRequestData {
  mobileNumber: string
  password: string
  fullName?: string
}

export interface RegisterVerifyData {
  mobileNumber: string
  otpCode: string
  password: string
  fullName?: string
}

// Password reset flow
export interface ForgotPasswordData {
  mobileNumber: string
}

export interface ResetPasswordData {
  mobileNumber: string
  otpCode: string
  newPassword: string
}

// Legacy types (deprecated)
export interface RequestOtpData {
  mobileNumber: string
}

export interface VerifyOtpData {
  mobileNumber: string
  otpCode: string
}

export interface RefreshTokenData {
  refreshToken: string
}

export interface OtpResponse {
  message: string
  expiresInSeconds: number
  isNewUser: boolean
  otpCode?: string // Only in dev environment
}

export const authApi = {
  // ============ Password-based Authentication ============

  login: async (data: LoginData): Promise<AuthResponse> => {
    const response = await apiClient.post('/api/v1/auth/login', data)
    return response.data
  },

  requestRegistrationOtp: async (data: RegisterRequestData): Promise<OtpResponse> => {
    const response = await apiClient.post('/api/v1/auth/register/request-otp', data)
    return response.data
  },

  verifyRegistration: async (data: RegisterVerifyData): Promise<AuthResponse> => {
    const response = await apiClient.post('/api/v1/auth/register/verify', data)
    return response.data
  },

  forgotPassword: async (data: ForgotPasswordData): Promise<OtpResponse> => {
    const response = await apiClient.post('/api/v1/auth/forgot-password', data)
    return response.data
  },

  resetPassword: async (data: ResetPasswordData): Promise<void> => {
    await apiClient.post('/api/v1/auth/reset-password', data)
  },

  // ============ Legacy OTP-based Authentication (Deprecated) ============

  /** @deprecated Use login() or requestRegistrationOtp() instead */
  requestOtp: async (data: RequestOtpData): Promise<OtpResponse> => {
    const response = await apiClient.post('/api/v1/auth/request-otp', data)
    return response.data
  },

  /** @deprecated Use login() or verifyRegistration() instead */
  verifyOtp: async (data: VerifyOtpData): Promise<AuthResponse> => {
    const response = await apiClient.post('/api/v1/auth/verify-otp', data)
    return response.data
  },

  // ============ Common Methods ============

  refreshToken: async (data: RefreshTokenData): Promise<AuthResponse> => {
    const response = await apiClient.post('/api/v1/auth/refresh', data)
    return response.data
  },

  logout: async () => {
    await apiClient.post('/api/v1/auth/logout')
  },

  getCurrentUser: async () => {
    const response = await apiClient.get('/api/v1/auth/me')
    return response.data
  },
}
