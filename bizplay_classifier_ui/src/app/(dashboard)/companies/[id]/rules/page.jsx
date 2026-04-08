'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import {
  ListFilter,
  Search,
  Trash2,
  X,
  MoreHorizontal,
  Upload,
  FileSpreadsheet,
  Loader2,
  Brain,
  ArrowUpRight,
  CheckCircle2,
  AlertCircle,
  Info,
} from 'lucide-react'
import {
  getRulesByCompany,
  deleteRule,
} from '../../../../../service/ruleService'
import { trainFromFile } from '../../../../../service/trainingService'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../../../../components/ui/table'

const PRIMARY = '#1A32D8'
const PRIMARY_HOVER = '#1529b8'

const CATEGORY_DOT_COLORS = [
  '#EF4444', '#F97316', '#10B981', '#3B82F6', '#8B5CF6', '#EC4899',
  '#14B8A6', '#6366F1', '#EAB308', '#84CC16', '#64748B',
]

function dotColorForLabel(str) {
  if (!str) return '#94A3B8'
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  return CATEGORY_DOT_COLORS[Math.abs(hash) % CATEGORY_DOT_COLORS.length]
}

const COND_BADGE = {
  amount_range: { label: 'Amount Range', color: '#475569', bg: '#F1F5F9' },
  mcc_industry: { label: 'MCC / Industry', color: '#475569', bg: '#F1F5F9' },
}

function mapApiRulesToRows(payload) {
  const list = Array.isArray(payload) ? payload.slice() : []
  list.sort((a, b) => {
    const ta = new Date(a.createdDate || 0).getTime()
    const tb = new Date(b.createdDate || 0).getTime()
    return tb - ta
  })
  return list.map((r) => {
    const hasRange = r.minAmount != null || r.maxAmount != null
    let pattern = ''
    if (hasRange) {
      const parts = []
      if (r.minAmount != null) parts.push(`≥ ${r.minAmount}`)
      if (r.maxAmount != null) parts.push(`≤ ${r.maxAmount}`)
      pattern = parts.join(' · ')
    } else {
      pattern = r.merchantIndustryCode ? `MCC ${r.merchantIndustryCode}` : '—'
    }
    const categoryDTOList = r.categoryDTOList || []
    const categorySearchText = categoryDTOList.map((c) => `${c.category} ${c.code}`).join(' ')
    return {
      id: r.ruleId,
      api: r,
      name: r.merchantIndustryName || '—',
      conditionType: hasRange ? 'amount_range' : 'mcc_industry',
      pattern,
      categoryDTOList,
      categorySearchText,
      status: r.usageStatus === 'Y' ? 'active' : 'inactive',
      usageStatus: r.usageStatus ?? 'N',
      description: r.description || '',
    }
  })
}

/** Upload labelled Excel/CSV → POST /data/train */
function LabelledDatasetTrainModal({ onClose, companyId, token, onDone }) {
  const fileRef = useRef(null)
  const [file, setFile] = useState(null)
  const [sheetName, setSheetName] = useState('')
  const [excelSheets, setExcelSheets] = useState([])
  const [status, setStatus] = useState(null)
  const [errorMsg, setErrorMsg] = useState('')

  const isRunning = status === 'training'
  const needsSheetPick = excelSheets.length > 1
  const canRun = Boolean(file && (!needsSheetPick || sheetName))

  const handleFileChange = async (e) => {
    const f = e.target.files[0]
    e.target.value = ''
    if (!f) return
    setFile(f)
    setStatus(null)
    setErrorMsg('')
    setSheetName('')
    setExcelSheets([])

    const isExcel = f.name.endsWith('.xlsx') || f.name.endsWith('.xls')
    if (!isExcel) return

    try {
      const XLSX = await import('xlsx')
      const buf = await f.arrayBuffer()
      const wb = XLSX.read(buf, { type: 'array' })
      if (wb.SheetNames.length > 1) setExcelSheets(wb.SheetNames)
    } catch { /* ignore */ }
  }

  const handleRun = async () => {
    if (!canRun) return
    setStatus(null)
    setErrorMsg('')
    setStatus('training')
    try {
      await trainFromFile(file, companyId, sheetName || null, token)
      setStatus('success')
      onDone?.()
    } catch (err) {
      setStatus('error')
      setErrorMsg(err.message || 'Training failed')
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 overflow-y-auto"
      style={{ background: 'rgba(15,23,42,0.4)' }}
      onClick={(e) => e.target === e.currentTarget && !isRunning && onClose()}
    >
      <div
        className="w-full max-w-md rounded-2xl overflow-hidden animate-fade-in my-8"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
          <div className="flex items-center gap-2.5">
            <div className="flex items-center justify-center w-8 h-8 rounded-lg" style={{ background: 'rgba(26,50,216,0.1)' }}>
              <Brain size={16} color={PRIMARY} />
            </div>
            <div>
              <h2 className="font-semibold text-sm" style={{ color: '#0F172A' }}>Train from labelled file</h2>
              <p className="text-xs" style={{ color: '#94A3B8' }}>Excel, CSV · updates rules via dataset</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => !isRunning && onClose()}
            className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer disabled:opacity-40"
            style={{ color: '#94A3B8' }}
            disabled={isRunning}
            onMouseEnter={(e) => { if (!isRunning) e.currentTarget.style.background = '#F1F5F9' }}
            onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
          >
            <X size={16} />
          </button>
        </div>

        <div className="p-6 space-y-4 max-h-[70vh] overflow-y-auto">
          <div>
            <label className="text-xs font-semibold block mb-2" style={{ color: '#64748B' }}>Training file</label>
            <input ref={fileRef} type="file" accept=".xlsx,.xls,.csv" className="hidden" onChange={handleFileChange} />
            {file ? (
              <div
                className="flex items-center justify-between px-4 py-3 rounded-xl"
                style={{ background: 'rgba(26,50,216,0.04)', border: '1.5px solid rgba(26,50,216,0.2)' }}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="flex items-center justify-center w-9 h-9 rounded-lg shrink-0" style={{ background: 'rgba(59,130,246,0.08)' }}>
                    <FileSpreadsheet size={16} color="#3B82F6" />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm font-semibold truncate" style={{ color: '#0F172A' }}>{file.name}</p>
                    <p className="text-xs" style={{ color: '#94A3B8' }}>{(file.size / 1024).toFixed(1)} KB</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    if (isRunning) return
                    setFile(null)
                    setSheetName('')
                    setExcelSheets([])
                    setStatus(null)
                    setErrorMsg('')
                  }}
                  className="text-xs font-medium cursor-pointer shrink-0 ml-2 disabled:opacity-40"
                  style={{ color: '#EF4444' }}
                  disabled={isRunning}
                >
                  Remove
                </button>
              </div>
            ) : (
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="w-full flex flex-col items-center justify-center gap-2 py-8 rounded-xl border-2 border-dashed cursor-pointer"
                style={{ borderColor: '#E2E8F0', background: '#F8FAFC', color: '#94A3B8' }}
                onMouseEnter={(e) => { e.currentTarget.style.borderColor = PRIMARY; e.currentTarget.style.background = 'rgba(26,50,216,0.02)' }}
                onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#E2E8F0'; e.currentTarget.style.background = '#F8FAFC' }}
              >
                <Upload size={22} />
                <div className="text-center">
                  <p className="text-sm font-semibold" style={{ color: '#64748B' }}>Click to select file</p>
                  <p className="text-xs mt-0.5">.xlsx, .xls, .csv</p>
                </div>
              </button>
            )}
          </div>

          {needsSheetPick && (
            <div className="space-y-2">
              <p className="text-[13px]" style={{ color: '#64748B' }}>
                The file <strong style={{ color: '#0F172A' }}>{file.name}</strong> has multiple sheets. Choose one for training.
              </p>
              <div
                className="flex flex-col gap-2 max-h-[160px] overflow-y-auto pr-1"
                style={{ scrollbarWidth: 'thin', scrollbarColor: '#CBD5E1 transparent' }}
              >
                {excelSheets.map((sheet) => {
                  const selected = sheetName === sheet
                  return (
                    <button
                      key={sheet}
                      type="button"
                      disabled={isRunning}
                      onClick={() => { setSheetName(sheet); setStatus(null); setErrorMsg('') }}
                      className="flex items-center justify-between px-4 py-3 rounded-xl border transition-all text-left disabled:opacity-50"
                      style={{
                        borderColor: selected ? PRIMARY : '#E2E8F0',
                        background: selected ? 'rgba(26,50,216,0.06)' : '#FFFFFF',
                      }}
                    >
                      <span className="text-[14px] font-bold" style={{ color: selected ? PRIMARY : '#0F172A' }}>{sheet}</span>
                      <ArrowUpRight size={14} style={{ color: selected ? PRIMARY : '#94A3B8' }} />
                    </button>
                  )
                })}
              </div>
            </div>
          )}

          {status === 'success' && (
            <div className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-xs font-medium" style={{ background: 'rgba(16,185,129,0.08)', color: '#059669' }}>
              <CheckCircle2 size={13} /> Training complete. Rules have been updated.
            </div>
          )}
          {status === 'error' && (
            <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg text-xs font-medium" style={{ background: 'rgba(239,68,68,0.08)', color: '#DC2626' }}>
              <AlertCircle size={13} className="shrink-0 mt-0.5" />
              <span className="break-words">{errorMsg}</span>
            </div>
          )}

          <div className="flex gap-2.5 pt-1">
            <button
              type="button"
              onClick={() => !isRunning && onClose()}
              className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer disabled:opacity-50"
              style={{ background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0' }}
              disabled={isRunning}
            >
              {status === 'success' ? 'Close' : 'Cancel'}
            </button>
            {status !== 'success' && (
              <button
                type="button"
                onClick={handleRun}
                disabled={!canRun || isRunning}
                className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50"
                style={{ background: PRIMARY, color: '#fff' }}
              >
                {isRunning ? <><Loader2 size={14} className="animate-spin" />Training…</> : <><Brain size={14} />Run training</>}
              </button>
            )}
          </div>

          <div className="flex gap-2.5 px-3 py-2.5 rounded-xl" style={{ background: 'rgba(26,50,216,0.04)', border: '1px solid rgba(26,50,216,0.12)' }}>
            <Info size={14} color={PRIMARY} className="shrink-0 mt-0.5" />
            <p className="text-[11px] leading-relaxed" style={{ color: '#334155' }}>
              For the AI assistant prompt, use <span className="font-semibold">Chatbot</span> → Enhance Prompt.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function RulesPage() {
  const { id: companyId } = useParams()
  const { data: session } = useSession()
  const token = session?.accessToken ?? null

  const [rules, setRules] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('all')
  const [menuOpen, setMenuOpen] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [deleteError, setDeleteError] = useState('')
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [trainOpen, setTrainOpen] = useState(false)

  const fetchRules = useCallback(async () => {
    if (!companyId) return
    setLoading(true)
    setLoadError('')
    try {
      const data = await getRulesByCompany(companyId, token)
      const payload = Array.isArray(data) ? data : (data?.payload ?? [])
      setRules(mapApiRulesToRows(payload))
    } catch (e) {
      setLoadError(e.message || 'Failed to load rules')
      setRules([])
    } finally {
      setLoading(false)
    }
  }, [companyId, token])

  useEffect(() => {
    fetchRules()
  }, [fetchRules])

  const filtered = rules.filter((r) => {
    const q = search.toLowerCase().trim()
    const matchesSearch =
      !q ||
      r.name.toLowerCase().includes(q) ||
      r.pattern.toLowerCase().includes(q) ||
      r.categorySearchText.toLowerCase().includes(q) ||
      (r.description && r.description.toLowerCase().includes(q))
    const matchesFilter = filterStatus === 'all' || r.status === filterStatus
    return matchesSearch && matchesFilter
  })

  const handleDelete = async () => {
    if (!confirmDelete || !token) return
    setDeleteLoading(true)
    setDeleteError('')
    try {
      await deleteRule(confirmDelete.id, token)
      await fetchRules()
      setConfirmDelete(null)
    } catch (e) {
      setDeleteError(e.message || 'Delete failed')
    } finally {
      setDeleteLoading(false)
    }
  }

  return (
    <div className="py-8 animate-fade-in" onClick={() => setMenuOpen(null)}>
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Classification Rules</h1>
          <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
            {loading ? 'Loading…' : `${rules.length} rules · ${rules.filter((r) => r.status === 'active').length} active`}
          </p>
        </div>
        <div className="flex items-center gap-2 flex-wrap justify-end">
          <button
            type="button"
            onClick={() => setTrainOpen(true)}
            className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold cursor-pointer"
            style={{ background: PRIMARY, color: '#fff', border: `1px solid ${PRIMARY}` }}
            onMouseEnter={(e) => { e.currentTarget.style.background = PRIMARY_HOVER }}
            onMouseLeave={(e) => { e.currentTarget.style.background = PRIMARY }}
          >
            <Upload size={15} />Train from labelled file
          </button>
        </div>
      </div>

      {loadError && (
        <div className="mb-5 px-4 py-3 rounded-xl text-sm font-medium" style={{ background: 'rgba(239,68,68,0.08)', color: '#DC2626', border: '1px solid rgba(239,68,68,0.2)' }}>
          {loadError}
        </div>
      )}

      <div className="flex items-center gap-3 mb-5 flex-wrap">
        <div className="relative max-w-xs flex-1">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: '#CBD5E1' }} />
          <input
            type="text"
            placeholder="Search rules…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl text-sm outline-none"
            style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', color: '#0F172A' }}
            onFocus={(e) => { e.target.style.borderColor = PRIMARY }}
            onBlur={(e) => { e.target.style.borderColor = '#E2E8F0' }}
          />
        </div>
        <div className="flex rounded-xl overflow-hidden" style={{ border: '1px solid #E2E8F0' }}>
          {['all', 'active', 'inactive'].map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setFilterStatus(s)}
              className="px-4 py-2 text-sm font-medium cursor-pointer capitalize"
              style={
                filterStatus === s
                  ? { background: PRIMARY, color: '#fff' }
                  : { background: '#FFFFFF', color: '#94A3B8' }
              }
              onMouseEnter={(e) => {
                if (filterStatus !== s) {
                  e.currentTarget.style.background = '#F8FAFC'
                  e.currentTarget.style.color = '#64748B'
                }
              }}
              onMouseLeave={(e) => {
                if (filterStatus !== s) {
                  e.currentTarget.style.background = '#FFFFFF'
                  e.currentTarget.style.color = '#94A3B8'
                }
              }}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      <div className="rounded-2xl overflow-hidden" style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}>
        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <Loader2 size={28} className="animate-spin" style={{ color: PRIMARY }} />
            <p className="text-sm" style={{ color: '#94A3B8' }}>Loading rules…</p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16">
            <ListFilter size={32} color="#E2E8F0" className="mx-auto mb-3" />
            <p className="text-sm" style={{ color: '#CBD5E1' }}>No rules found</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow className="bg-[#F8FAFC] hover:bg-[#F8FAFC]">
                <TableHead className="w-12 text-center">#</TableHead>
                <TableHead>Rule Name</TableHead>
                <TableHead>Condition</TableHead>
                <TableHead>Pattern</TableHead>
                <TableHead>Categories</TableHead>
                <TableHead className="text-center w-[110px]">Use status</TableHead>
                <TableHead className="text-right w-12 pr-3" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((rule, rowIdx) => {
                const badge = COND_BADGE[rule.conditionType] ?? COND_BADGE.mcc_industry
                return (
                  <TableRow key={rule.id} className="group">
                    <TableCell className="text-center whitespace-nowrap">
                      <span className="text-[12px] font-semibold tabular-nums" style={{ color: '#64748B' }}>{rowIdx + 1}</span>
                    </TableCell>
                    <TableCell>
                      <div>
                        <span className="font-semibold text-sm" style={{ color: '#0F172A' }}>{rule.name}</span>
                        {rule.description && (
                          <p className="text-[11px] mt-0.5" style={{ color: '#94A3B8' }}>{rule.description}</p>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center text-xs font-semibold px-2.5 py-1 rounded-full" style={{ background: badge.bg, color: badge.color }}>
                        {badge.label}
                      </span>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs font-mono px-2.5 py-1.5 rounded-lg" style={{ background: '#F1F5F9', color: '#64748B' }}>{rule.pattern}</span>
                    </TableCell>
                    <TableCell className="whitespace-normal">
                      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 max-w-[280px]">
                        {rule.categoryDTOList.length === 0 ? (
                          <span className="text-sm" style={{ color: '#CBD5E1' }}>—</span>
                        ) : (
                          rule.categoryDTOList.map((c) => (
                            <span key={c.categoryId} className="inline-flex items-center gap-1 text-sm" style={{ color: '#334155' }}>
                              <span className="w-2 h-2 rounded-full shrink-0" style={{ background: dotColorForLabel(c.category) }} />
                              <span className="truncate max-w-[120px]" title={`${c.code} · ${c.category}`}>{c.category}</span>
                            </span>
                          ))
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <div className="inline-flex items-center justify-center gap-1.5">
                        {rule.usageStatus === 'Y' ? (
                          <CheckCircle2 size={16} style={{ color: '#10B981' }} aria-hidden />
                        ) : (
                          <AlertCircle size={16} style={{ color: '#CBD5E1' }} aria-hidden />
                        )}
                        <span
                          className="text-xs font-bold font-mono tabular-nums"
                          style={{ color: rule.usageStatus === 'Y' ? '#0F172A' : '#94A3B8' }}
                          title={rule.usageStatus === 'Y' ? 'In use' : 'Not in use'}
                        >
                          {rule.usageStatus}
                        </span>
                      </div>
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap py-3.5">
                      <div className="relative flex w-full justify-end" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          onClick={() => setMenuOpen(menuOpen === rule.id ? null : rule.id)}
                          className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer"
                          style={{ color: '#CBD5E1' }}
                          onMouseEnter={(e) => { e.currentTarget.style.background = '#F1F5F9'; e.currentTarget.style.color = '#94A3B8' }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#CBD5E1' }}
                        >
                          <MoreHorizontal size={16} />
                        </button>
                        {menuOpen === rule.id && (
                          <div className="absolute right-0 top-9 z-20 rounded-xl py-1 w-36 shadow-lg" style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>
                            <button
                              type="button"
                              onClick={() => { setConfirmDelete(rule); setMenuOpen(null) }}
                              className="flex items-center gap-2 w-full px-3 py-2 text-xs cursor-pointer"
                              style={{ color: '#EF4444' }}
                              onMouseEnter={(e) => { e.currentTarget.style.background = '#FEF2F2' }}
                              onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
                            >
                              <Trash2 size={13} />Delete
                            </button>
                          </div>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </div>

      {trainOpen && companyId && (
        <LabelledDatasetTrainModal
          onClose={() => setTrainOpen(false)}
          companyId={companyId}
          token={token}
          onDone={fetchRules}
        />
      )}

      {confirmDelete && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          style={{ background: 'rgba(15,23,42,0.4)' }}
          onClick={(e) => e.target === e.currentTarget && !deleteLoading && setConfirmDelete(null)}
        >
          <div className="w-full max-w-sm rounded-2xl p-6 animate-fade-in" style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }}>
            <div className="flex items-center justify-center w-12 h-12 rounded-xl mx-auto mb-4" style={{ background: 'rgba(239,68,68,0.1)' }}>
              <Trash2 size={22} color="#EF4444" />
            </div>
            <h2 className="font-bold text-center mb-2" style={{ color: '#0F172A' }}>Delete Rule</h2>
            <p className="text-sm text-center mb-4" style={{ color: '#64748B' }}>
              Delete <strong style={{ color: '#0F172A' }}>{confirmDelete.name}</strong>? This cannot be undone.
            </p>
            {deleteError && (
              <p className="text-xs font-medium text-center mb-4 px-2" style={{ color: '#DC2626' }}>{deleteError}</p>
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => { if (!deleteLoading) { setConfirmDelete(null); setDeleteError('') } }}
                className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer disabled:opacity-50"
                style={{ background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0' }}
                disabled={deleteLoading}
                onMouseEnter={(e) => { if (!deleteLoading) e.currentTarget.style.background = '#F1F5F9' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#F8FAFC' }}
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleteLoading || !token}
                className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer flex items-center justify-center gap-2 disabled:opacity-50"
                style={{ background: '#EF4444', color: '#fff' }}
                onMouseEnter={(e) => { if (!deleteLoading) e.currentTarget.style.background = '#DC2626' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#EF4444' }}
              >
                {deleteLoading ? <Loader2 size={14} className="animate-spin" /> : null}
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
