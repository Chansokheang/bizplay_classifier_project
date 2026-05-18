'use client'

import { useEffect, useMemo, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useSession } from 'next-auth/react'
import {
  Building2,
  Tags,
  ShieldCheck,
  FileSpreadsheet,
  CheckCircle2,
  Clock,
  TrendingUp,
  ArrowRight,
  Sparkles,
  Loader2,
  AlertCircle,
} from 'lucide-react'
import { COMPANIES as MOCK_COMPANIES, TRANSACTIONS as MOCK_TX } from '../../../lib/mock-data'
import { getAllCorps } from '../../../service/companyService'
import { getOutputFiles } from '../../../service/transactionService'

const PRIMARY = '#1A32D8'
const PRIMARY_SOFT = 'rgba(26,50,216,0.08)'

const STATUS_CFG = {
  completed: { label: 'Completed', color: '#059669', bg: 'rgba(5,150,105,0.1)', Icon: CheckCircle2 },
  pending: { label: 'Pending', color: '#D97706', bg: 'rgba(217,119,6,0.1)', Icon: Clock },
  processing: { label: 'Processing', color: '#2563EB', bg: 'rgba(37,99,235,0.1)', Icon: TrendingUp },
}

const card = {
  background: '#FFFFFF',
  border: '1px solid #E2E8F0',
  boxShadow: '0 1px 3px rgba(15,23,42,0.06)',
}

const inner = { background: '#F8FAFC', border: '1px solid #F1F5F9' }

function mapApiCorp(c) {
  const rules = c.ruleDTOList ?? []
  const categories = rules.flatMap((r) => r.categoryDTOList ?? [])
  const uniqueCategories = new Set(categories.map((cat) => cat.categoryId))
  return {
    id: String(c.corpNo),
    name: c.corpName ?? 'Untitled Company',
    industry: c.corpNo ?? '—',
    status: 'active',
    rulesCount: rules.length,
    categoriesCount: uniqueCategories.size,
    transactionsCount: 0,
  }
}

function deriveUploadStatus(records, classified) {
  if (records == null) return 'pending'
  if (records === 0) return 'completed'
  if (classified == null) return 'pending'
  if (classified >= records) return 'completed'
  if (classified === 0) return 'pending'
  return 'processing'
}

function mapOutputRow({ file: f, classifySummary: s }, companyId, companyName) {
  const records = s?.totalRows ?? null
  const classified = s?.processedRows ?? null
  const created = f?.createdDate
  const sortTime = created ? new Date(created).getTime() : 0
  const uploadedAt = created
    ? new Date(created).toISOString().split('T')[0]
    : '—'
  return {
    id: String(f.fileId),
    companyId: String(companyId),
    companyName,
    fileName: f.originalFileName ?? '—',
    records: records ?? 0,
    classified: classified ?? 0,
    status: deriveUploadStatus(records, classified),
    uploadedAt,
    sortTime,
  }
}

function mockAggregatePct() {
  const total = MOCK_TX.reduce((s, t) => s + t.records, 0)
  const classified = MOCK_TX.reduce((s, t) => s + t.classified, 0)
  return total > 0 ? Math.round((classified / total) * 100) : 0
}

export default function DashboardOverviewPage() {
  const { data: session, status } = useSession()
  const token = session?.accessToken

  const [companies, setCompanies] = useState(MOCK_COMPANIES)
  const [recentUploads, setRecentUploads] = useState(() =>
    MOCK_TX.slice(0, 3).map((tx) => ({
      id: tx.id,
      companyId: tx.companyId,
      companyName: '',
      fileName: tx.fileName,
      records: tx.records,
      classified: tx.classified,
      status: tx.status,
      uploadedAt: tx.uploadedAt,
      sortTime: new Date(tx.uploadedAt).getTime() || 0,
    })),
  )
  const [classifyPct, setClassifyPct] = useState(() => mockAggregatePct())
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const firstCompanyId = companies[0]?.id ?? '1'

  const insights = useMemo(
    () => [
      {
        title: 'Live classification',
        desc: 'Monitor uploads and rule coverage across every entity.',
        href: `/companies/${firstCompanyId}/transactions`,
        image: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?auto=format&fit=crop&w=800&q=80',
        alt: 'Analytics charts on a screen',
      },
      {
        title: 'Rules & training',
        desc: 'Keep MCC mapping sharp with labelled datasets.',
        href: `/companies/${firstCompanyId}/rules`,
        image: 'https://images.unsplash.com/photo-1552664730-d307ca884978?auto=format&fit=crop&w=800&q=80',
        alt: 'Team collaborating at a desk',
      },
      {
        title: 'Company setup',
        desc: 'Jump into categories, bots, and project settings.',
        href: '/companies',
        image: 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=800&q=80',
        alt: 'Modern office interior',
      },
    ],
    [firstCompanyId],
  )

  const quickActions = useMemo(
    () => [
      { label: 'All companies', href: '/companies', icon: Building2 },
      { label: 'Transactions', href: `/companies/${firstCompanyId}/transactions`, icon: FileSpreadsheet },
      { label: 'Categories', href: `/companies/${firstCompanyId}/categories`, icon: Tags },
      { label: 'Rules', href: `/companies/${firstCompanyId}/rules`, icon: ShieldCheck },
    ],
    [firstCompanyId],
  )

  useEffect(() => {
    if (status === 'loading') return

    if (status !== 'authenticated' || !token) {
      setCompanies(MOCK_COMPANIES)
      setRecentUploads(
        MOCK_TX.slice(0, 3).map((tx) => ({
          id: tx.id,
          companyId: tx.companyId,
          companyName: '',
          fileName: tx.fileName,
          records: tx.records,
          classified: tx.classified,
          status: tx.status,
          uploadedAt: tx.uploadedAt,
          sortTime: new Date(tx.uploadedAt).getTime() || 0,
        })),
      )
      setClassifyPct(mockAggregatePct())
      setLoading(false)
      setLoadError('')
      return
    }

    let cancelled = false

    ;(async () => {
      setLoading(true)
      setLoadError('')
      try {
        const companiesRes = await getAllCorps(token)
        const payload = Array.isArray(companiesRes?.payload) ? companiesRes.payload : []
        const rows = payload.map(mapApiCorp)

        const fetchableRows = rows.filter((r) => Boolean(r.id))
        const settled =
          fetchableRows.length > 0
            ? await Promise.allSettled(fetchableRows.map((r) => getOutputFiles(r.id, token)))
            : []

        const rowsById = {}
        rows.forEach((r) => {
          rowsById[r.id] = { ...r, transactionsCount: 0 }
        })

        const allRecent = []
        let totalR = 0
        let totalC = 0

        fetchableRows.forEach((row, i) => {
          const result = settled[i]
          if (result.status !== 'fulfilled') return
          const list = Array.isArray(result.value?.payload) ? result.value.payload : []
          let sumRows = 0
          for (const item of list) {
            const s = item.classifySummary
            const tr = s?.totalRows ?? 0
            const pc = s?.processedRows ?? 0
            sumRows += tr
            totalR += tr
            totalC += pc
            allRecent.push(mapOutputRow(item, row.id, row.name))
          }
          if (rowsById[row.id]) rowsById[row.id].transactionsCount = sumRows
        })

        const mergedRows = rows.map((r) => rowsById[r.id] ?? r)

        allRecent.sort((a, b) => b.sortTime - a.sortTime)
        const topRecent = allRecent.slice(0, 5)

        if (!cancelled) {
          setCompanies(mergedRows)
          setRecentUploads(topRecent.length ? topRecent : [])
          setClassifyPct(totalR > 0 ? Math.round((totalC / totalR) * 100) : 0)
          try {
            const index = Object.fromEntries(mergedRows.map((c) => [c.id, c.name]))
            window.localStorage.setItem('companiesIndex', JSON.stringify(index))
          } catch {
            /* ignore */
          }
        }
      } catch (e) {
        if (!cancelled) {
          setLoadError(e?.message || 'Could not load dashboard data.')
          setCompanies(MOCK_COMPANIES)
          setRecentUploads(
            MOCK_TX.slice(0, 3).map((tx) => ({
              id: tx.id,
              companyId: tx.companyId,
              companyName: '',
              fileName: tx.fileName,
              records: tx.records,
              classified: tx.classified,
              status: tx.status,
              uploadedAt: tx.uploadedAt,
              sortTime: new Date(tx.uploadedAt).getTime() || 0,
            })),
          )
          setClassifyPct(mockAggregatePct())
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()

    return () => {
      cancelled = true
    }
  }, [status, token])

  return (
    <div className="pb-10 animate-fade-in relative">
      <div
        className="pointer-events-none absolute -top-24 -right-24 h-72 w-72 rounded-full blur-3xl opacity-40"
        style={{ background: 'radial-gradient(circle, rgba(26,50,216,0.35) 0%, transparent 70%)' }}
        aria-hidden
      />
      <div
        className="pointer-events-none absolute top-48 -left-16 h-64 w-64 rounded-full blur-3xl opacity-30"
        style={{ background: 'radial-gradient(circle, rgba(16,185,129,0.25) 0%, transparent 70%)' }}
        aria-hidden
      />

      <section className="relative mb-10 grid gap-8 lg:grid-cols-2 lg:items-center lg:gap-12">
        <div className="relative z-[1] space-y-6" style={{ animationDelay: '0ms' }}>
          <div
            className="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-widest"
            style={{ background: PRIMARY_SOFT, color: PRIMARY, border: '1px solid rgba(26,50,216,0.15)' }}
          >
            <Sparkles size={12} strokeWidth={2.5} />
            Bizplay classifier
          </div>
          <h1 className="text-3xl font-bold leading-tight tracking-tight sm:text-4xl" style={{ color: '#0F172A' }}>
            Expense intelligence,{' '}
            <span
              className="bg-clip-text text-transparent"
              style={{ backgroundImage: `linear-gradient(135deg, ${PRIMARY} 0%, #6366F1 50%, #0F172A 100%)` }}
            >
              beautifully simple
            </span>
          </h1>
          <p className="max-w-xl text-base leading-relaxed" style={{ color: '#64748B' }}>
            Upload ledgers, train rules, and route every line item to the right category—then drill into file-level
            detail without leaving the workspace.
          </p>
          <div className="flex flex-wrap gap-3">
            <Link
              href="/companies"
              className="inline-flex items-center gap-2 rounded-xl px-5 py-3 text-sm font-bold text-white shadow-lg transition-all hover:opacity-95 hover:shadow-xl"
              style={{
                background: `linear-gradient(180deg, ${PRIMARY} 0%, #1529AB 100%)`,
                boxShadow: '0 10px 28px rgba(26,50,216,0.35)',
              }}
            >
              Open companies
              <ArrowRight size={16} strokeWidth={2.5} />
            </Link>
          </div>
        </div>

        <div
          className="relative z-[1] aspect-[4/3] w-full max-w-xl overflow-hidden rounded-3xl shadow-2xl lg:justify-self-end"
          style={{
            boxShadow: '0 25px 50px -12px rgba(26,50,216,0.25), 0 0 0 1px rgba(255,255,255,0.08) inset',
          }}
        >
          <Image
            src="https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=1200&q=85"
            alt="Financial data visualization and business workflow"
            fill
            className="object-cover"
            sizes="(max-width: 1024px) 100vw, 480px"
            priority
          />
          <div
            className="absolute inset-0 bg-gradient-to-t from-slate-900/75 via-slate-900/20 to-transparent"
            aria-hidden
          />
          <div className="absolute bottom-0 left-0 right-0 p-6 text-white">
            <p className="text-xs font-semibold uppercase tracking-wider text-white/70">At a glance</p>
            <p className="mt-1 text-lg font-bold leading-snug">
              {companies.length} companies · {classifyPct}% classified
            </p>
          </div>
        </div>
      </section>

      <section className="relative z-[1] mb-10 grid gap-4 md:grid-cols-3">
        {insights.map(({ title, desc, href, image, alt }) => (
          <Link
            key={title}
            href={href}
            className="group overflow-hidden rounded-2xl transition-all hover:-translate-y-1 hover:shadow-lg"
            style={card}
          >
            <div className="relative aspect-[16/10] overflow-hidden">
              <Image
                src={image}
                alt={alt}
                fill
                className="object-cover transition-transform duration-500 group-hover:scale-105"
                sizes="(max-width: 768px) 100vw, 33vw"
              />
              <div
                className="absolute inset-0 bg-gradient-to-t from-slate-900/80 via-transparent to-transparent opacity-90"
                aria-hidden
              />
              <div className="absolute bottom-3 left-4 right-4">
                <p className="text-sm font-bold text-white">{title}</p>
              </div>
            </div>
            <div className="p-4">
              <p className="text-[13px] leading-relaxed" style={{ color: '#64748B' }}>
                {desc}
              </p>
              <span className="mt-3 inline-flex items-center gap-1 text-xs font-bold" style={{ color: PRIMARY }}>
                Explore
                <ArrowRight size={12} className="transition-transform group-hover:translate-x-0.5" />
              </span>
            </div>
          </Link>
        ))}
      </section>

      {loadError && (
        <div
          className="relative z-[1] mb-4 flex items-center gap-2 rounded-xl px-4 py-3 text-[13px] font-semibold"
          style={{ background: 'rgba(220,38,38,0.06)', border: '1px solid rgba(220,38,38,0.15)', color: '#DC2626' }}
        >
          <AlertCircle size={16} />
          {loadError}
        </div>
      )}

      <div className="relative z-[1] grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 overflow-hidden rounded-2xl" style={card}>
          <div
            className="flex items-center justify-between px-6 py-4"
            style={{ borderBottom: '1px solid #F1F5F9', background: '#FAFBFC' }}
          >
            <h2 className="text-sm font-bold" style={{ color: '#0F172A' }}>
              Companies
            </h2>
            <Link href="/companies" className="flex items-center gap-1 text-xs font-bold cursor-pointer" style={{ color: PRIMARY }}>
              View all
              <ArrowRight size={13} />
            </Link>
          </div>
          {loading ? (
            <div className="flex items-center justify-center gap-2 py-16" style={{ color: '#94A3B8' }}>
              <Loader2 size={20} className="animate-spin" style={{ color: PRIMARY }} />
              <span className="text-[13px] font-semibold">Loading companies…</span>
            </div>
          ) : companies.length === 0 ? (
            <p className="px-6 py-12 text-center text-[13px] font-medium" style={{ color: '#94A3B8' }}>
              No companies yet.{' '}
              <Link href="/companies" style={{ color: PRIMARY }} className="font-bold">
                Add one
              </Link>
            </p>
          ) : (
            companies.map((c, idx) => (
              <Link
                key={c.id}
                href={`/companies/${c.id}/categories`}
                className="flex items-center gap-4 px-6 py-4 transition-colors"
                style={{
                  borderBottom: idx < companies.length - 1 ? '1px solid #F1F5F9' : 'none',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = '#F8FAFC'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = 'transparent'
                }}
              >
                <div
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-sm font-bold text-white shadow-sm"
                  style={{ background: `linear-gradient(135deg, ${PRIMARY}, #6366F1)` }}
                >
                  {c.name.charAt(0)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold" style={{ color: '#0F172A' }}>
                    {c.name}
                  </p>
                  <p className="text-xs font-medium" style={{ color: '#94A3B8' }}>
                    {c.industry}
                  </p>
                </div>
                <div className="hidden flex-wrap items-center justify-end gap-6 text-center sm:flex">
                  {[
                    ['Categories', c.categoriesCount],
                    ['Rules', c.rulesCount],
                    ['Records', c.transactionsCount.toLocaleString()],
                  ].map(([l, v]) => (
                    <div key={l}>
                      <p className="text-sm font-bold tabular-nums" style={{ color: '#0F172A' }}>
                        {v}
                      </p>
                      <p className="text-[11px] font-medium" style={{ color: '#94A3B8' }}>
                        {l}
                      </p>
                    </div>
                  ))}
                </div>
                <span
                  className="shrink-0 rounded-full px-2.5 py-1 text-xs font-bold"
                  style={
                    c.status === 'active'
                      ? { background: 'rgba(16,185,129,0.1)', color: '#059669' }
                      : { background: '#F1F5F9', color: '#94A3B8' }
                  }
                >
                  {c.status === 'active' ? 'Active' : 'Inactive'}
                </span>
              </Link>
            ))
          )}
        </div>

        <div className="flex flex-col gap-6">
          <div className="flex-1 overflow-hidden rounded-2xl" style={card}>
            <div className="px-5 py-4" style={{ borderBottom: '1px solid #F1F5F9', background: '#FAFBFC' }}>
              <h2 className="text-sm font-bold" style={{ color: '#0F172A' }}>
                Recent uploads
              </h2>
            </div>
            <div className="space-y-3 p-4">
              {loading ? (
                <div className="flex items-center justify-center gap-2 py-10" style={{ color: '#94A3B8' }}>
                  <Loader2 size={18} className="animate-spin" style={{ color: PRIMARY }} />
                  <span className="text-[12px] font-semibold">Loading uploads…</span>
                </div>
              ) : recentUploads.length === 0 ? (
                <p className="py-8 text-center text-[12px] font-medium" style={{ color: '#94A3B8' }}>
                  No output files yet. Upload a transaction file from a company’s Transactions page.
                </p>
              ) : (
                recentUploads.map((tx) => {
                  const pct = tx.records > 0 ? Math.round((tx.classified / tx.records) * 100) : 0
                  const cfg = STATUS_CFG[tx.status] ?? STATUS_CFG.pending
                  const detailHref = `/companies/${tx.companyId}/transactions/${tx.id}`
                  return (
                    <Link
                      key={`${tx.companyId}-${tx.id}`}
                      href={detailHref}
                      className="block rounded-xl p-3 transition-colors hover:bg-slate-50/80"
                      style={inner}
                    >
                      <div className="mb-2 flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="truncate text-xs font-semibold" style={{ color: '#334155' }}>
                            {tx.fileName}
                          </p>
                          {tx.companyName ? (
                            <p className="mt-0.5 truncate text-[10px] font-semibold uppercase tracking-wide" style={{ color: '#CBD5E1' }}>
                              {tx.companyName}
                            </p>
                          ) : null}
                        </div>
                        <span
                          className="flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-bold"
                          style={{ background: cfg.bg, color: cfg.color }}
                        >
                          <cfg.Icon size={10} />
                          {cfg.label}
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 flex-1 overflow-hidden rounded-full" style={{ background: '#E2E8F0' }}>
                          <div className="h-full rounded-full" style={{ width: `${pct}%`, background: cfg.color }} />
                        </div>
                        <span className="shrink-0 text-xs font-bold" style={{ color: '#94A3B8' }}>
                          {pct}%
                        </span>
                      </div>
                      <p className="mt-1.5 text-xs font-medium" style={{ color: '#CBD5E1' }}>
                        {tx.classified}/{tx.records} classified · {tx.uploadedAt}
                      </p>
                    </Link>
                  )
                })
              )}
            </div>
          </div>

          <div className="overflow-hidden rounded-2xl" style={card}>
            <div className="px-5 py-4" style={{ borderBottom: '1px solid #F1F5F9', background: '#FAFBFC' }}>
              <h2 className="text-sm font-bold" style={{ color: '#0F172A' }}>
                Quick actions
              </h2>
            </div>
            <div className="grid grid-cols-2 gap-2 p-3">
              {quickActions.map(({ label, href, icon: Icon }) => (
                <Link
                  key={label}
                  href={href}
                  className="flex flex-col items-center gap-2 rounded-xl p-3 text-center transition-colors"
                  style={inner}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.background = PRIMARY_SOFT
                    e.currentTarget.style.borderColor = 'rgba(26,50,216,0.2)'
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.background = '#F8FAFC'
                    e.currentTarget.style.borderColor = '#F1F5F9'
                  }}
                >
                  <div
                    className="flex h-9 w-9 items-center justify-center rounded-lg"
                    style={{ background: PRIMARY_SOFT }}
                  >
                    <Icon size={17} style={{ color: PRIMARY }} strokeWidth={2} />
                  </div>
                  <span className="text-xs font-semibold leading-tight" style={{ color: '#475569' }}>
                    {label}
                  </span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
