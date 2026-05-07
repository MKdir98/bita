import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Copy, Check } from 'lucide-react'

interface RouteYamlViewerProps {
  yaml: string
}

export default function RouteYamlViewer({ yaml }: RouteYamlViewerProps) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(yaml)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  if (!yaml) {
    return (
      <div className="p-4 text-center text-muted-foreground">
        خروجی YAML در دسترس نیست
      </div>
    )
  }

  return (
    <div className="relative">
      <Button
        variant="ghost"
        size="icon"
        className="absolute top-2 left-2"
        onClick={handleCopy}
      >
        {copied ? <Check className="w-4 h-4 text-green-500" /> : <Copy className="w-4 h-4" />}
      </Button>
      <pre
        className="p-4 bg-muted rounded-lg text-sm overflow-auto max-h-96 font-mono"
        dir="ltr"
      >
        {yaml}
      </pre>
    </div>
  )
}
