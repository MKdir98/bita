import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface User {
  id: number
  mobileNumber: string
  fullName: string
  roles: string[]
}

// Pending registration data for OTP verification step
interface PendingRegistration {
  mobileNumber: string
  password: string
  fullName?: string
}

// Pending password reset data for OTP verification step
interface PendingPasswordReset {
  mobileNumber: string
}

interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  pendingMobile: string | null
  pendingRegistration: PendingRegistration | null
  pendingPasswordReset: PendingPasswordReset | null

  setUser: (user: User) => void
  setTokens: (accessToken: string, refreshToken: string) => void
  setPendingMobile: (mobile: string) => void
  setPendingRegistration: (data: PendingRegistration) => void
  setPendingPasswordReset: (data: PendingPasswordReset) => void
  clearPendingData: () => void
  logout: () => void
  hasRole: (role: string) => boolean
  hasAnyRole: (roles: string[]) => boolean
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      pendingMobile: null,
      pendingRegistration: null,
      pendingPasswordReset: null,

      setUser: (user) => set({ user, isAuthenticated: true }),

      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken, isAuthenticated: true }),

      setPendingMobile: (mobile) => set({ pendingMobile: mobile }),

      setPendingRegistration: (data) => set({ pendingRegistration: data }),

      setPendingPasswordReset: (data) => set({ pendingPasswordReset: data }),

      clearPendingData: () =>
        set({
          pendingMobile: null,
          pendingRegistration: null,
          pendingPasswordReset: null,
        }),

      logout: () =>
        set({
          user: null,
          accessToken: null,
          refreshToken: null,
          isAuthenticated: false,
          pendingMobile: null,
          pendingRegistration: null,
          pendingPasswordReset: null,
        }),

      hasRole: (role) => {
        const user = get().user
        if (!user?.roles) return false
        const upperRole = role.toUpperCase()
        return user.roles.some((r) => r.toUpperCase() === upperRole)
      },

      hasAnyRole: (roles) => {
        const user = get().user
        if (!user?.roles) return false
        const upperUserRoles = user.roles.map((r) => r.toUpperCase())
        return roles.some((role) => upperUserRoles.includes(role.toUpperCase()))
      },
    }),
    {
      name: 'bita-auth',
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
)
