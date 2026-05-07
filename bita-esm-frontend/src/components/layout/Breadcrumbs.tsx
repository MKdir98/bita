import { Link, useLocation } from 'react-router-dom'
import { ChevronLeft, Home } from 'lucide-react'

const breadcrumbNames: Record<string, string> = {
  clients: 'سازمان‌ها',
  services: 'سرویس‌ها',
  routes: 'مسیرها',
  templates: 'قالب‌ها',
  access: 'دسترسی‌ها',
  users: 'کاربران',
  chat: 'چت',
  settings: 'تنظیمات',
  new: 'جدید',
  edit: 'ویرایش',
}

export default function Breadcrumbs() {
  const location = useLocation()
  const pathSegments = location.pathname.split('/').filter(Boolean)

  if (pathSegments.length === 0) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Home className="w-4 h-4" />
        <span>داشبورد</span>
      </div>
    )
  }

  return (
    <nav className="flex items-center gap-2 text-sm">
      <Link
        to="/"
        className="text-muted-foreground hover:text-foreground transition-colors"
      >
        <Home className="w-4 h-4" />
      </Link>

      {pathSegments.map((segment, index) => {
        const path = '/' + pathSegments.slice(0, index + 1).join('/')
        const isLast = index === pathSegments.length - 1
        const name = breadcrumbNames[segment] || segment

        return (
          <div key={path} className="flex items-center gap-2">
            <ChevronLeft className="w-4 h-4 text-muted-foreground" />
            {isLast ? (
              <span className="font-medium">{name}</span>
            ) : (
              <Link
                to={path}
                className="text-muted-foreground hover:text-foreground transition-colors"
              >
                {name}
              </Link>
            )}
          </div>
        )
      })}
    </nav>
  )
}
