import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'

interface ProtectedRouteProps {
  children: React.ReactNode
  requiredRoles?: string[]
}

export default function ProtectedRoute({
  children,
  requiredRoles,
}: ProtectedRouteProps) {
  const location = useLocation()
  const { isAuthenticated, hasAnyRole } = useAuthStore()

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  if (requiredRoles && !hasAnyRole(requiredRoles)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
