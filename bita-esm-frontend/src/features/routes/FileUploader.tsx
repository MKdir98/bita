import { useRef, useState } from 'react'
import { useUploadRouteFile } from './useRoutes'
import { Button } from '@/components/ui/button'
import { Upload } from 'lucide-react'

interface FileUploaderProps {
  routeId: number
}

const fileTypes = [
  { value: 'WSDL', label: 'WSDL' },
  { value: 'XSD', label: 'XSD' },
  { value: 'PROPERTIES', label: 'Properties' },
  { value: 'XSLT', label: 'XSLT' },
  { value: 'OTHER', label: 'سایر' },
]

export default function FileUploader({ routeId }: FileUploaderProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [selectedType, setSelectedType] = useState('WSDL')
  const uploadFile = useUploadRouteFile(routeId)

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      await uploadFile.mutateAsync({ file, fileType: selectedType })
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  return (
    <div className="flex items-center gap-2">
      <select
        className="h-10 px-3 rounded-md border border-input bg-background"
        value={selectedType}
        onChange={(e) => setSelectedType(e.target.value)}
      >
        {fileTypes.map((type) => (
          <option key={type.value} value={type.value}>
            {type.label}
          </option>
        ))}
      </select>
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        onChange={handleFileSelect}
        accept=".wsdl,.xsd,.properties,.xslt,.xml"
      />
      <Button
        type="button"
        variant="outline"
        onClick={() => fileInputRef.current?.click()}
        disabled={uploadFile.isPending}
      >
        <Upload className="w-4 h-4 ml-2" />
        {uploadFile.isPending ? 'در حال آپلود...' : 'آپلود فایل'}
      </Button>
    </div>
  )
}
