interface MarkdownRendererProps {
  content: string
}

export default function MarkdownRenderer({ content }: MarkdownRendererProps) {
  // Simple markdown parsing
  const parseMarkdown = (text: string): React.ReactNode => {
    const lines = text.split('\n')
    const elements: React.ReactNode[] = []
    let inCodeBlock = false
    let codeContent = ''
    let codeLanguage = ''

    lines.forEach((line, index) => {
      // Code block
      if (line.startsWith('```')) {
        if (!inCodeBlock) {
          inCodeBlock = true
          codeLanguage = line.slice(3)
          codeContent = ''
        } else {
          inCodeBlock = false
          elements.push(
            <pre key={index} className="my-2 p-3 bg-gray-900 text-gray-100 rounded-lg overflow-auto text-sm" dir="ltr">
              <code>{codeContent}</code>
            </pre>
          )
        }
        return
      }

      if (inCodeBlock) {
        codeContent += (codeContent ? '\n' : '') + line
        return
      }

      // Headers
      if (line.startsWith('### ')) {
        elements.push(<h3 key={index} className="text-lg font-bold mt-4 mb-2">{line.slice(4)}</h3>)
        return
      }
      if (line.startsWith('## ')) {
        elements.push(<h2 key={index} className="text-xl font-bold mt-4 mb-2">{line.slice(3)}</h2>)
        return
      }
      if (line.startsWith('# ')) {
        elements.push(<h1 key={index} className="text-2xl font-bold mt-4 mb-2">{line.slice(2)}</h1>)
        return
      }

      // List items
      if (line.startsWith('- ') || line.startsWith('* ')) {
        elements.push(
          <li key={index} className="mr-4">
            {parseInline(line.slice(2))}
          </li>
        )
        return
      }

      // Numbered list
      const numberedMatch = line.match(/^(\d+)\.\s/)
      if (numberedMatch) {
        elements.push(
          <li key={index} className="mr-4">
            {parseInline(line.slice(numberedMatch[0].length))}
          </li>
        )
        return
      }

      // Empty line
      if (!line.trim()) {
        elements.push(<br key={index} />)
        return
      }

      // Regular paragraph
      elements.push(<p key={index} className="my-1">{parseInline(line)}</p>)
    })

    return elements
  }

  const parseInline = (text: string): React.ReactNode => {
    // Bold
    text = text.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    // Italic
    text = text.replace(/\*(.*?)\*/g, '<em>$1</em>')
    // Inline code
    text = text.replace(/`(.*?)`/g, '<code class="bg-muted px-1 py-0.5 rounded text-sm">$1</code>')

    return <span dangerouslySetInnerHTML={{ __html: text }} />
  }

  return <div className="prose prose-sm dark:prose-invert max-w-none">{parseMarkdown(content)}</div>
}
