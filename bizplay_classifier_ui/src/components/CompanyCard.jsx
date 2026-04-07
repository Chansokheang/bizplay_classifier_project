'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { MoreHorizontal, Pencil, Trash2, GitPullRequest, FolderTree, Database } from 'lucide-react'

const TINTS = [
  '#1A32D8', '#0F766E', '#9333EA', '#DC2626', '#EAB308',
  '#2563EB', '#DB2777', '#059669', '#EA580C', '#4F46E5'
]

const GRADIENTS = [
  'linear-gradient(to right, #1A32D8, #60A5FA)',
  'linear-gradient(to right, #0F766E, #34D399)',
  'linear-gradient(to right, #9333EA, #F472B6)',
  'linear-gradient(to right, #DC2626, #FBBF24)',
  'linear-gradient(to right, #EAB308, #FDE047)',
  'linear-gradient(to right, #2563EB, #A78BFA)',
  'linear-gradient(to right, #DB2777, #FDA4AF)',
  'linear-gradient(to right, #059669, #A3E635)',
  'linear-gradient(to right, #EA580C, #FCA5A5)',
  'linear-gradient(to right, #4F46E5, #818CF8)'
]

function getColorIndex(id, name) {
  const seedString = String(id || name || 'a')
  let hash = 0
  for (let i = 0; i < seedString.length; i++) {
    hash = seedString.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash) % TINTS.length
}

export default function CompanyCard({ company, onEdit, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [hovered, setHovered] = useState(false)
  const [isRouting, setIsRouting] = useState(false)
  const router = useRouter()

  const initials = company.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()
  const index = getColorIndex(company.id, company.name)
  const tint = TINTS[index]
  const gradient = GRADIENTS[index]
  const identifier = company.industry && company.industry !== '—'
    ? company.industry
    : `ID-${String(company.id).padStart(4, '0')}`

  return (
    <div
      className="relative rounded-[22px] overflow-hidden cursor-pointer flex flex-col transition-all duration-300"
      style={{
        background: hovered
          ? `linear-gradient(180deg, ${tint}14 0%, rgba(255,255,255,0.85) 100%)`
          : `linear-gradient(180deg, ${tint}08 0%, rgba(255,255,255,0.5) 100%)`,
        backdropFilter: 'blur(16px)',
        border: hovered ? `1px solid ${tint}30` : '1px solid rgba(255,255,255,0.6)',
        minHeight: '210px',
        boxShadow: hovered
          ? `0 12px 32px -8px ${tint}25, inset 0 1px 0 rgba(255,255,255,1)`
          : '0 4px 16px -4px rgba(15,23,42,0.05), inset 0 1px 0 rgba(255,255,255,0.6)',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => { setHovered(false); setMenuOpen(false) }}
      onClick={() => {
        if (isRouting) return
        setIsRouting(true)
        router.push(`/companies/${company.id}/transactions`)
      }}
    >
      <div className="p-5 flex-1 flex flex-col">
        {/* Top Row: Avatar, Name and Menu */}
        <div className="flex items-start mb-4 gap-4">
          <div className="shrink-0">
            <div
              className="flex items-center justify-center w-12 h-12 rounded-xl shadow-sm text-lg font-black opacity-90 tracking-tighter"
              style={{ background: `${tint}1A`, border: `1px solid ${tint}29`, color: tint }}
            >
              {initials}
            </div>
          </div>

          <div className="flex-1 min-w-0 pt-0.5">
            <h3 className="text-xl font-bold tracking-tight text-slate-800 break-words leading-tight mb-0.5">
              {company.name}
            </h3>
            <p className="text-xs font-semibold text-slate-500 tracking-wide">
              {identifier}
            </p>
          </div>

          {/* Menu Dropdown */}
          <div className="relative shrink-0 -mt-1 -mr-2" onClick={e => e.stopPropagation()}>
            <button
              onClick={() => setMenuOpen(o => !o)}
              className="flex items-center justify-center w-8 h-8 rounded-full hover:bg-slate-100 transition-colors"
              style={{ color: '#9CA3AF' }}
            >
              <MoreHorizontal size={16} />
            </button>
            {menuOpen && (
              <div className="absolute right-0 top-8 z-30 rounded-xl py-1 w-32 shadow-lg bg-white border border-slate-200">
                <button onClick={() => { onEdit(company); setMenuOpen(false) }} className="flex items-center gap-2 w-full px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-50 hover:text-slate-900 transition-colors">
                  <Pencil size={13} />Edit
                </button>
                <button onClick={() => { onDelete(company.id); setMenuOpen(false) }} className="flex items-center gap-2 w-full px-3 py-2 text-xs font-semibold text-red-500 hover:bg-red-50 transition-colors">
                  <Trash2 size={13} />Delete
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Bottom Stats Footer */}
        <div className="mt-auto pt-2 flex items-center justify-between gap-1.5 text-[11px] sm:text-[11.5px] font-medium text-slate-500 whitespace-nowrap overflow-visible">
          <div className="flex items-center gap-1.5 hover:text-slate-800 transition-colors cursor-default">
            <GitPullRequest size={13} className="opacity-75" style={{ color: tint }} />
            <span><span className="font-bold text-slate-700">{company.rulesCount}</span> Rules</span>
          </div>
          <div className="flex items-center gap-1.5 hover:text-slate-800 transition-colors cursor-default">
            <FolderTree size={13} className="opacity-75" style={{ color: tint }} />
            <span><span className="font-bold text-slate-700">{company.categoriesCount}</span> Classes</span>
          </div>
          <div className="flex items-center gap-1.5 hover:text-slate-800 transition-colors cursor-default pr-1">
            <Database size={13} className="opacity-75" style={{ color: tint }} />
            <span><span className="font-bold text-slate-700">{(company.transactionsCount / 1000).toFixed(1)}k</span> Processed</span>
          </div>
        </div>
      </div>

      {/* Bottom Color Strip */}
      <div className="h-[5px] w-full flex rounded-b-2xl overflow-hidden shrink-0">
        <div className="flex-1" style={{ background: gradient }} />
      </div>
    </div>
  )
}
