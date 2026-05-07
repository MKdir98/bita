import { Link, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/authStore'
import {
  LayoutDashboard,
  Building2,
  Server,
  Route,
  FileCode2,
  Shield,
  Users,
  MessageSquare,
  Settings,
} from 'lucide-react'

interface NavItem {
  title: string
  href: string
  icon: React.ComponentType<{ className?: string }>
  roles?: string[]
}

const navItems: NavItem[] = [
  { title: 'داشبورد', href: '/', icon: LayoutDashboard },
  {
    title: 'سازمان‌ها',
    href: '/clients',
    icon: Building2,
    roles: ['ADMIN', 'CLIENT_MANAGER', 'VIEWER'],
  },
  {
    title: 'سرویس‌ها',
    href: '/services',
    icon: Server,
    roles: ['ADMIN', 'SERVICE_MANAGER', 'VIEWER'],
  },
  {
    title: 'مسیرها',
    href: '/routes',
    icon: Route,
    roles: ['ADMIN', 'SERVICE_MANAGER', 'VIEWER'],
  },
  {
    title: 'قالب‌ها',
    href: '/templates',
    icon: FileCode2,
    roles: ['ADMIN', 'SERVICE_MANAGER'],
  },
  {
    title: 'دسترسی‌ها',
    href: '/access',
    icon: Shield,
    roles: ['ADMIN', 'ACCESS_MANAGER'],
  },
  {
    title: 'کاربران',
    href: '/users',
    icon: Users,
    roles: ['ADMIN'],
  },
  {
    title: 'چت',
    href: '/chat',
    icon: MessageSquare,
  },
]

export default function Sidebar() {
  const location = useLocation()
  const { hasAnyRole, user } = useAuthStore()

  // Debug: Log user roles (can be removed in production)
  console.log('Current user roles:', user?.roles)

  const filteredItems = navItems.filter((item) => {
    if (!item.roles) return true
    return hasAnyRole(item.roles)
  })

  return (
    <aside className="w-64 bg-white dark:bg-gray-800 border-l dark:border-gray-700 flex flex-col">
      <div className="p-4 border-b dark:border-gray-700">
        <h1 className="text-xl font-bold text-primary">BITA</h1>
        <p className="text-xs text-muted-foreground">سامانه مدیریت سرویس</p>
      </div>

      <nav className="flex-1 p-4 space-y-1">
        {filteredItems.map((item) => {
          const Icon = item.icon
          const isActive = location.pathname === item.href || 
            (item.href !== '/' && location.pathname.startsWith(item.href))

          return (
            <Link
              key={item.href}
              to={item.href}
              className={cn(
                'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'hover:bg-gray-100 dark:hover:bg-gray-700'
              )}
            >
              <Icon className="w-5 h-5" />
              <span>{item.title}</span>
            </Link>
          )
        })}
      </nav>

      <div className="p-4 border-t dark:border-gray-700">
        <Link
          to="/settings"
          className="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"
        >
          <Settings className="w-5 h-5" />
          <span>تنظیمات</span>
        </Link>
      </div>
    </aside>
  )
}
