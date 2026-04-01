'use client'

import Link from 'next/link'
import {
  Building2, Tags, ListFilter, FileSpreadsheet,
  CheckCircle2, Clock, TrendingUp, ArrowRight,
} from 'lucide-react'
import { COMPANIES, CATEGORIES, RULES, TRANSACTIONS } from '../../../lib/mock-data'

const totalTransactions = TRANSACTIONS.reduce((s, t) => s + t.records, 0)
const totalClassified   = TRANSACTIONS.reduce((s, t) => s + t.classified, 0)

const STATS = [
  { label: 'Companies',    value: COMPANIES.length,                  sub: '+1 this month',    icon: Building2,     accent: '#8B5CF6', accentBg: 'rgba(139,92,246,0.1)' },
  { label: 'Categories',   value: CATEGORIES.length,                 sub: 'Across all companies', icon: Tags,      accent: '#3B82F6', accentBg: 'rgba(59,130,246,0.1)' },
  { label: 'Active Rules', value: RULES.filter(r=>r.status==='active').length, sub: `${RULES.length} total`, icon: ListFilter, accent: '#F59E0B', accentBg: 'rgba(245,158,11,0.1)' },
  { label: 'Transactions', value: totalTransactions.toLocaleString(), sub: `${Math.round((totalClassified/totalTransactions)*100)}% classified`, icon: FileSpreadsheet, accent: '#10B981', accentBg: 'rgba(16,185,129,0.1)' },
]

const STATUS_CFG = {
  completed:  { label: 'Completed',  color: '#10B981', bg: 'rgba(16,185,129,0.1)',  Icon: CheckCircle2 },
  pending:    { label: 'Pending',    color: '#F59E0B', bg: 'rgba(245,158,11,0.1)',  Icon: Clock },
  processing: { label: 'Processing', color: '#3B82F6', bg: 'rgba(59,130,246,0.1)',  Icon: TrendingUp },
}

const QUICK_ACTIONS = [
  { label: 'New Company',         href: '/companies',                icon: Building2,       color: '#8B5CF6' },
  { label: 'View Companies',      href: '/companies',                icon: ArrowRight,      color: '#F59E0B' },
  { label: 'Upload Transactions', href: '/companies/1/transactions', icon: FileSpreadsheet, color: '#10B981' },
  { label: 'Manage Rules',        href: '/companies/1/rules',        icon: ListFilter,      color: '#3B82F6' },
]

const card  = { background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }
const inner = { background: '#F8FAFC', border: '1px solid #F1F5F9' }

export default function DashboardOverviewPage() {
  return (
    <div className="py-8 animate-fade-in">
      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Overview</h1>
        <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>Welcome back — here's what's happening across all companies.</p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
        {STATS.map(({ label, value, sub, icon: Icon, accent, accentBg }) => (
          <div key={label} className="rounded-xl p-5" style={card}>
            <div className="flex items-center justify-between mb-4">
              <span className="text-sm font-medium" style={{ color: '#64748B' }}>{label}</span>
              <div className="flex items-center justify-center w-9 h-9 rounded-lg" style={{ background: accentBg }}>
                <Icon size={18} color={accent} strokeWidth={2} />
              </div>
            </div>
            <p className="text-3xl font-bold" style={{ color: '#0F172A' }}>{value}</p>
            <p className="mt-1 text-xs" style={{ color: '#94A3B8' }}>{sub}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Companies */}
        <div className="lg:col-span-2 rounded-xl overflow-hidden" style={card}>
          <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid #F1F5F9' }}>
            <h2 className="font-semibold text-sm" style={{ color: '#0F172A' }}>Companies</h2>
            <Link href="/companies" className="flex items-center gap-1 text-xs font-semibold cursor-pointer" style={{ color: '#F59E0B' }}>
              View all <ArrowRight size={13} />
            </Link>
          </div>
          {COMPANIES.map((c, idx) => (
            <Link
              key={c.id}
              href={`/companies/${c.id}/categories`}
              className="flex items-center gap-4 px-6 py-4 cursor-pointer"
              style={{ borderBottom: idx < COMPANIES.length-1 ? '1px solid #F1F5F9' : 'none' }}
              onMouseEnter={(e) => { e.currentTarget.style.background = '#F8FAFC' }}
              onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
            >
              <div className="flex items-center justify-center w-9 h-9 rounded-lg shrink-0" style={{ background: 'rgba(139,92,246,0.1)' }}>
                <Building2 size={17} color="#8B5CF6" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-semibold text-sm" style={{ color: '#0F172A' }}>{c.name}</p>
                <p className="text-xs mt-0.5" style={{ color: '#94A3B8' }}>{c.industry}</p>
              </div>
              <div className="flex items-center gap-5 text-center">
                {[['Categories', c.categoriesCount],['Rules', c.rulesCount],['Records', c.transactionsCount.toLocaleString()]].map(([l,v]) => (
                  <div key={l}><p className="text-sm font-bold" style={{ color: '#0F172A' }}>{v}</p><p className="text-xs" style={{ color: '#94A3B8' }}>{l}</p></div>
                ))}
              </div>
              <span className="text-xs font-semibold px-2.5 py-1 rounded-full shrink-0"
                style={c.status==='active' ? { background:'rgba(16,185,129,0.1)', color:'#059669' } : { background:'#F1F5F9', color:'#94A3B8' }}>
                {c.status === 'active' ? 'Active' : 'Inactive'}
              </span>
            </Link>
          ))}
        </div>

        <div className="flex flex-col gap-6">
          {/* Recent uploads */}
          <div className="rounded-xl overflow-hidden flex-1" style={card}>
            <div className="px-5 py-4" style={{ borderBottom: '1px solid #F1F5F9' }}>
              <h2 className="font-semibold text-sm" style={{ color: '#0F172A' }}>Recent Uploads</h2>
            </div>
            <div className="p-4 space-y-3">
              {TRANSACTIONS.slice(0,3).map((tx) => {
                const pct = tx.records > 0 ? Math.round((tx.classified/tx.records)*100) : 0
                const cfg = STATUS_CFG[tx.status] ?? STATUS_CFG.pending
                return (
                  <div key={tx.id} className="rounded-xl p-3" style={inner}>
                    <div className="flex items-start justify-between gap-2 mb-2">
                      <p className="text-xs font-semibold truncate" style={{ color: '#334155' }}>{tx.fileName}</p>
                      <span className="flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full shrink-0" style={{ background: cfg.bg, color: cfg.color }}>
                        <cfg.Icon size={10} />{cfg.label}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-1.5 rounded-full overflow-hidden" style={{ background: '#E2E8F0' }}>
                        <div className="h-full rounded-full" style={{ width: `${pct}%`, background: cfg.color }} />
                      </div>
                      <span className="text-xs shrink-0" style={{ color: '#94A3B8' }}>{pct}%</span>
                    </div>
                    <p className="text-xs mt-1.5" style={{ color: '#CBD5E1' }}>{tx.classified}/{tx.records} classified · {tx.uploadedAt}</p>
                  </div>
                )
              })}
            </div>
          </div>

          {/* Quick actions */}
          <div className="rounded-xl overflow-hidden" style={card}>
            <div className="px-5 py-4" style={{ borderBottom: '1px solid #F1F5F9' }}>
              <h2 className="font-semibold text-sm" style={{ color: '#0F172A' }}>Quick Actions</h2>
            </div>
            <div className="p-3 grid grid-cols-2 gap-2">
              {QUICK_ACTIONS.map(({ label, href, icon: Icon, color }) => (
                <Link key={label} href={href}
                  className="flex flex-col items-center gap-2 p-3 rounded-xl cursor-pointer text-center"
                  style={inner}
                  onMouseEnter={(e) => { e.currentTarget.style.background = '#F1F5F9'; e.currentTarget.style.borderColor = '#E2E8F0' }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = '#F8FAFC'; e.currentTarget.style.borderColor = '#F1F5F9' }}
                >
                  <div className="flex items-center justify-center w-8 h-8 rounded-lg" style={{ background: `${color}15` }}>
                    <Icon size={16} color={color} />
                  </div>
                  <span className="text-xs font-medium leading-tight" style={{ color: '#64748B' }}>{label}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
