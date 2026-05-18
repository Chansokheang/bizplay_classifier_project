'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useEffect, useState } from 'react'
import { ChevronRight } from 'lucide-react'
import { COMPANIES } from '../lib/mock-data'

const LABELS = {
  dashboard: 'Dashboard',
  companies: 'Companies',
  categories: 'Categories',
  rules: 'Rules',
  transactions: 'Transactions',
  chatbot: 'Chatbot',
  settings: 'Settings',
  help: 'Help',
}

export default function Breadcrumbs() {
  const pathname = usePathname()
  const [companyIndex, setCompanyIndex] = useState({})
  const [filesIndex, setFilesIndex] = useState({})
  const segments = pathname.split('/').filter(Boolean)

  if (segments.length === 0) return null

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem('companiesIndex')
      if (raw) {
        setCompanyIndex(JSON.parse(raw))
      }
      const filesRaw = window.localStorage.getItem('filesIndex')
      if (filesRaw) {
        setFilesIndex(JSON.parse(filesRaw))
      }
    } catch {
      setCompanyIndex({})
      setFilesIndex({})
    }
  }, [])

  const crumbs = []
  let href = ''

  for (let i = 0; i < segments.length; i += 1) {
    const segment = segments[i]
    href += `/${segment}`

    if (segment === 'companies' && segments[i + 1]) {
      const companyId = segments[i + 1]
      const company = COMPANIES.find((c) => c.id === companyId)
      const storedName = companyIndex[companyId]
      crumbs.push({ label: LABELS[segment] ?? 'Companies', href })
      href += `/${companyId}`
      crumbs.push({ label: company?.name ?? storedName ?? `Company ${companyId}`, href })
      i += 1
      continue
    }

    if (segment === 'transactions' && segments[i + 1]) {
      const fileId = segments[i + 1]
      const companyId = segments[i - 1]
      const fileName = filesIndex?.[companyId]?.[fileId]
      crumbs.push({ label: LABELS[segment] ?? 'Transactions', href })
      href += `/${fileId}`
      crumbs.push({ label: fileName ?? `File ${fileId}`, href })
      i += 1
      continue
    }

    crumbs.push({ label: LABELS[segment] ?? segment, href })
  }

  return (
    <div className="flex items-center gap-2 text-xs py-6" style={{ color: '#CBD5E1' }}>
      {crumbs.map((crumb, index) => {
        const isLast = index === crumbs.length - 1
        return (
          <div key={`${crumb.href}-${index}`} className="flex items-center gap-2">
            {index > 0 && <ChevronRight size={13} />}
            {isLast ? (
              <span style={{ color: '#0F172A', fontWeight: 600 }}>{crumb.label}</span>
            ) : (
              <Link
                href={crumb.href}
                className="cursor-pointer"
                style={{ color: '#94A3B8' }}
                onMouseEnter={(e) => { e.currentTarget.style.color = '#1A32D8' }}
                onMouseLeave={(e) => { e.currentTarget.style.color = '#94A3B8' }}
              >
                {crumb.label}
              </Link>
            )}
          </div>
        )
      })}
    </div>
  )
}
