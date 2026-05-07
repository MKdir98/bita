import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { Button } from '@/components/ui/button'
import ThemeToggle from './ThemeToggle'
import Breadcrumbs from './Breadcrumbs'
import { LogOut, User } from 'lucide-react'

export default function Header() {
  const navigate = useNavigate()
  const { user, logout } = useAuthStore()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <header className="h-16 bg-white dark:bg-gray-800 border-b dark:border-gray-700 flex items-center justify-between px-6">
      <Breadcrumbs />

      <div className="flex items-center gap-4">
        <ThemeToggle />

        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 px-3 py-1 rounded-lg bg-gray-100 dark:bg-gray-700">
            <User className="w-4 h-4" />
            <span className="text-sm">{user?.fullName || user?.mobileNumber}</span>
          </div>

          <Button variant="ghost" size="icon" onClick={handleLogout}>
            <LogOut className="w-5 h-5" />
          </Button>
        </div>
      </div>
    </header>
  )
}
