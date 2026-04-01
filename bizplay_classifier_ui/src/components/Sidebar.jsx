'use client'

import { useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useSession, signOut } from 'next-auth/react'
import {
  Menu,
  LayoutDashboard,
  Folder,
  Landmark,
  FileSpreadsheet,
  Layers,
  ShieldCheck,
  MessageSquare,
  ChevronDown,
  HelpCircle,
  LogOut,
  Settings,
} from 'lucide-react'
import { COMPANIES } from '../lib/mock-data'

const PROJECT_LINKS = [
  { href: 'transactions', label: 'Transactions', icon: FileSpreadsheet },
  { href: 'categories', label: 'Categories', icon: Layers },
  { href: 'rules', label: 'Rules', icon: ShieldCheck },
  { href: 'chatbot', label: 'Chatbot', icon: MessageSquare },
]

export default function Sidebar() {
  const pathname = usePathname()
  const { data: session } = useSession()
  const [projectOpen, setProjectOpen] = useState(true)

  const companyMatch = pathname.match(/\/companies\/([^/]+)/)
  const currentCompanyId = companyMatch ? companyMatch[1] : null
  const currentCompany = currentCompanyId ? COMPANIES.find((c) => c.id === currentCompanyId) : null
  const projectLabel = currentCompany?.name ?? 'My Project'
  const projectActive = PROJECT_LINKS.some(({ href }) =>
    currentCompanyId ? pathname.startsWith(`/companies/${currentCompanyId}/${href}`) : false
  )

  const isActive = (href) => pathname === href || (href !== '/' && pathname.startsWith(href))

  return (
    <aside className="w-[220px] h-screen bg-white border-r border-gray-200 flex flex-col flex-shrink-0">
      <div className="px-5 pt-6 pb-4 flex items-center gap-3">
        <button className="focus:outline-none" style={{ color: '#1A32D8' }} aria-label="Open menu">
          <Menu size={20} />
        </button>
        <span className="text-[22px] font-bold tracking-tight" style={{ color: '#1A32D8' }}>Bizplay</span>
      </div>

      <div className="px-5 mb-2" />

      <div className="flex-1 overflow-y-auto px-3 pb-6" style={{ scrollbarWidth: 'thin' }}>
        <div className="mb-6">
          <h3 className="px-3 text-[11px] font-semibold text-gray-400 uppercase tracking-widest mb-2 mt-4">Main</h3>
          <ul className="space-y-0.5">
            <li>
              <Link
                href="/dashboard"
                className="flex items-center gap-3 px-3 py-2 rounded-lg transition-all duration-200 hover:text-[#1A32D8] hover:bg-[rgba(26,50,216,0.08)]"
                style={
                  isActive('/dashboard')
                    ? { background: 'rgba(26,50,216,0.08)', color: '#1A32D8' }
                    : { color: '#6B7280' }
                }
              >
                <LayoutDashboard
                  className={`w-5 h-5 ${isActive('/dashboard') ? 'text-[#1A32D8]' : 'text-gray-400'}`}
                  strokeWidth={1.75}
                />
                <span className={`text-[12px] ${isActive('/dashboard') ? 'font-semibold' : 'font-medium'}`}>Dashboard</span>
              </Link>
            </li>

            <li className="nav-group">
              <button
                type="button"
                className="w-full flex items-center justify-between px-3 py-2 rounded-lg transition-colors"
                style={projectActive ? { color: '#1A32D8' } : { color: '#6B7280' }}
                onClick={() => setProjectOpen((open) => !open)}
              >
                <div className="flex items-center gap-3">
                  <Folder className={`w-5 h-5 ${projectActive ? 'text-[#1A32D8]' : 'text-gray-400'}`} strokeWidth={1.75} />
                  <span className={`text-[12px] truncate ${projectActive ? 'font-semibold' : 'font-medium'}`}>{projectLabel}</span>
                </div>
                <ChevronDown
                  className={`w-4 h-4 transition-transform ${projectActive ? 'text-[#1A32D8]' : 'text-gray-400'}`}
                  style={{ transform: projectOpen ? 'rotate(180deg)' : 'rotate(0deg)' }}
                />
              </button>

              <div
                className="grid transition-[grid-template-rows] duration-200"
                style={{ gridTemplateRows: projectOpen ? '1fr' : '0fr' }}
              >
                <div className="overflow-hidden">
                  <ul className="mt-1 mb-1 ml-[30px] border-l border-gray-200 flex flex-col pl-3">
                    <li>
                      <Link
                        href="/companies"
                        className="flex items-center gap-2 pl-3 pr-3 py-1.5 text-[13px] transition-all duration-200 hover:text-[#1A32D8] hover:bg-[rgba(26,50,216,0.08)]"
                        style={
                          pathname === '/companies'
                            ? { background: 'rgba(26,50,216,0.08)', color: '#1A32D8', borderRadius: '8px' }
                            : { color: '#6B7280' }
                        }
                      >
                        <Landmark
                          size={14}
                          className={pathname === '/companies' ? 'text-[#1A32D8]' : 'text-gray-400'}
                        />
                        <span className={pathname === '/companies' ? 'font-semibold' : 'font-medium'}>Companies</span>
                      </Link>
                    </li>
                    {PROJECT_LINKS.map(({ href, label, icon: Icon }) => {
                      const fullHref = currentCompanyId ? `/companies/${currentCompanyId}/${href}` : null
                      const disabled = !currentCompanyId
                      const active = fullHref ? isActive(fullHref) : false
                      return (
                        <li key={href}>
                          <Link
                            href={fullHref || '/companies'}
                            className={`flex items-center gap-2 pl-3 pr-3 py-1.5 text-[13px] transition-all duration-200 hover:text-[#1A32D8] hover:bg-[rgba(26,50,216,0.08)] ${disabled ? 'opacity-50' : ''}`}
                            style={
                              active
                                ? { background: 'rgba(26,50,216,0.08)', color: '#1A32D8', borderRadius: '8px' }
                                : { color: '#6B7280' }
                            }
                          >
                            <Icon
                              size={14}
                              className={active ? 'text-[#1A32D8]' : 'text-gray-400'}
                            />
                            <span className={active ? 'font-semibold' : 'font-medium'}>{label}</span>
                          </Link>
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </div>
            </li>
          </ul>
        </div>
        <h3 className="px-3 text-[11px] font-semibold text-gray-400 uppercase tracking-widest mb-2 mt-6">Other</h3>
        {/*
        <div>
          <h3 className="px-3 text-[11px] font-semibold text-gray-400 uppercase tracking-widest mb-2 mt-6">Other</h3>
          <ul className="space-y-0.5">
            <li>
              <Link
                href="/settings"
                className="flex items-center gap-3 px-3 py-2 text-gray-500 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-colors"
              >
                <Settings className="w-5 h-5 text-gray-400" strokeWidth={1.75} />
                <span className="text-[12px] font-medium">Settings</span>
              </Link>
            </li>
            <li>
              <Link
                href="/help"
                className="flex items-center gap-3 px-3 py-2 text-gray-500 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-colors"
              >
                <HelpCircle className="w-5 h-5 text-gray-400" strokeWidth={1.75} />
                <span className="text-[12px] font-medium">Help</span>
              </Link>
            </li>
          </ul>
        </div>
        */}

        <button
          type="button"
          onClick={() => signOut({ callbackUrl: '/login' })}
          className="w-full flex items-center gap-3 px-3 py-2 text-gray-500 hover:text-gray-900 hover:bg-gray-50 rounded-lg transition-colors"
        >
          <LogOut className="w-5 h-5 text-gray-400" strokeWidth={1.75} />
          <span className="text-[12px] font-medium">Logout</span>
        </button>
      </div>
    </aside>
  )
}
