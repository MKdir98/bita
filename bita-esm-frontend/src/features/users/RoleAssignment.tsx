import { Label } from '@/components/ui/label'
import { Shield } from 'lucide-react'
import type { Role } from '@/api/users'

interface RoleAssignmentProps {
  roles: Role[]
  selectedRoles: number[]
  onChange: (roleIds: number[]) => void
}

export default function RoleAssignment({ roles, selectedRoles, onChange }: RoleAssignmentProps) {
  const toggleRole = (roleId: number) => {
    if (selectedRoles.includes(roleId)) {
      onChange(selectedRoles.filter((id) => id !== roleId))
    } else {
      onChange([...selectedRoles, roleId])
    }
  }

  if (!roles.length) {
    return (
      <div className="space-y-2">
        <Label>نقش‌ها</Label>
        <div className="p-4 border rounded-lg text-center text-muted-foreground">
          <Shield className="w-8 h-8 mx-auto mb-2" />
          <p className="text-sm">نقشی تعریف نشده</p>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <Label>نقش‌ها</Label>
      <div className="border rounded-lg p-4 space-y-2">
        {roles.map((role) => (
          <label
            key={role.id}
            className={`flex items-start gap-3 p-3 rounded-lg cursor-pointer transition-colors ${
              selectedRoles.includes(role.id)
                ? 'bg-primary/10 border border-primary'
                : 'hover:bg-muted border border-transparent'
            }`}
          >
            <input
              type="checkbox"
              checked={selectedRoles.includes(role.id)}
              onChange={() => toggleRole(role.id)}
              className="w-4 h-4 mt-1"
            />
            <div className="flex-1">
              <p className="font-medium">{role.name}</p>
              {role.description && (
                <p className="text-sm text-muted-foreground">{role.description}</p>
              )}
              {role.permissions.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-2">
                  {role.permissions.slice(0, 5).map((perm) => (
                    <span key={perm} className="text-xs bg-secondary px-1.5 py-0.5 rounded">
                      {perm}
                    </span>
                  ))}
                  {role.permissions.length > 5 && (
                    <span className="text-xs text-muted-foreground">
                      +{role.permissions.length - 5} دیگر
                    </span>
                  )}
                </div>
              )}
            </div>
          </label>
        ))}
      </div>
    </div>
  )
}
