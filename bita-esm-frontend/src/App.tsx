import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { Toaster } from '@/components/ui/toaster'

// Auth Pages
import LoginPage from '@/features/auth/LoginPage'
import OtpPage from '@/features/auth/OtpPage'
import RegisterPage from '@/features/auth/RegisterPage'
import ForgotPasswordPage from '@/features/auth/ForgotPasswordPage'

// Layout
import MainLayout from '@/components/layout/MainLayout'
import ProtectedRoute from '@/features/auth/ProtectedRoute'

// Dashboard
import DashboardPage from '@/features/dashboard/DashboardPage'

// Clients
import ClientListPage from '@/features/clients/ClientListPage'
import ClientDetailPage from '@/features/clients/ClientDetailPage'
import ClientFormPage from '@/features/clients/ClientFormPage'

// Services
import ServiceListPage from '@/features/services/ServiceListPage'
import ServiceDetailPage from '@/features/services/ServiceDetailPage'
import ServiceFormPage from '@/features/services/ServiceFormPage'
import ServiceCollectionListPage from '@/features/services/ServiceCollectionListPage'

// Routes
import RouteListPage from '@/features/routes/RouteListPage'
import RouteDetailPage from '@/features/routes/RouteDetailPage'
import RouteFormPage from '@/features/routes/RouteFormPage'

// Chat
import ChatPage from '@/features/chat/ChatPage'

// Templates
import TemplatesPage from '@/features/templates/TemplatesPage'
import RouteTemplateFormPage from '@/features/templates/RouteTemplateFormPage'
import ComponentTemplateFormPage from '@/features/templates/ComponentTemplateFormPage'
import EndpointTemplateFormPage from '@/features/templates/EndpointTemplateFormPage'

// Users
import UserListPage from '@/features/users/UserListPage'
import UserFormPage from '@/features/users/UserFormPage'
import UserProfilePage from '@/features/users/UserProfilePage'

// Settings
import SettingsPage from '@/features/settings/SettingsPage'

// Audit
import AuditHistoryPage from '@/features/audit/AuditHistoryPage'

function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  return (
    <>
      <Routes>
        {/* Public Routes */}
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/" /> : <LoginPage />}
        />
        <Route
          path="/register"
          element={isAuthenticated ? <Navigate to="/" /> : <RegisterPage />}
        />
        <Route
          path="/forgot-password"
          element={isAuthenticated ? <Navigate to="/" /> : <ForgotPasswordPage />}
        />
        <Route
          path="/otp"
          element={isAuthenticated ? <Navigate to="/" /> : <OtpPage />}
        />

        {/* Protected Routes */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          }
        >
          {/* Dashboard */}
          <Route index element={<DashboardPage />} />

          {/* Audit */}
          <Route path="audit/:entityType/:id" element={<AuditHistoryPage />} />

          {/* Clients */}
          <Route path="clients" element={<ClientListPage />} />
          <Route path="clients/new" element={<ClientFormPage />} />
          <Route path="clients/:id" element={<ClientDetailPage />} />
          <Route path="clients/:id/edit" element={<ClientFormPage />} />

          {/* Services */}
          <Route path="services" element={<ServiceListPage />} />
          <Route path="services/collections" element={<ServiceCollectionListPage />} />
          <Route path="services/new" element={<ServiceFormPage />} />
          <Route path="services/:id" element={<ServiceDetailPage />} />
          <Route path="services/:id/edit" element={<ServiceFormPage />} />

          {/* Routes */}
          <Route path="routes" element={<RouteListPage />} />
          <Route path="routes/new" element={<RouteFormPage />} />
          <Route path="routes/:id" element={<RouteDetailPage />} />
          <Route path="routes/:id/edit" element={<RouteFormPage />} />

          {/* Chat */}
          <Route path="chat" element={<ChatPage />} />

          {/* Templates */}
          <Route path="templates" element={<TemplatesPage />} />
          <Route path="templates/route/new" element={<RouteTemplateFormPage />} />
          <Route path="templates/route/:id/edit" element={<RouteTemplateFormPage />} />
          <Route path="templates/component/new" element={<ComponentTemplateFormPage />} />
          <Route path="templates/component/:id/edit" element={<ComponentTemplateFormPage />} />
          <Route path="templates/endpoint/new" element={<EndpointTemplateFormPage />} />
          <Route path="templates/endpoint/:id/edit" element={<EndpointTemplateFormPage />} />

          {/* Users */}
          <Route path="users" element={<UserListPage />} />
          <Route path="users/new" element={<UserFormPage />} />
          <Route path="users/:id" element={<UserFormPage />} />
          <Route path="profile" element={<UserProfilePage />} />

          {/* Settings */}
          <Route path="settings" element={<SettingsPage />} />
        </Route>

        {/* Catch all */}
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
      <Toaster />
    </>
  )
}

export default App
