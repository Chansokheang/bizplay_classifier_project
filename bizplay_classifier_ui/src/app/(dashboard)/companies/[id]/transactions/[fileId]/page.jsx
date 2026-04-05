'use client'

import { useMemo, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { Search, X } from 'lucide-react'
import TRANSACTION_DATA from '../../../../../../lib/transactions_enriched.json'
import { TRANSACTIONS as INIT_TX } from '../../../../../../lib/mock-data'

const card = { background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }

function formatDate(value) {
  if (!value) return '—'
  const s = String(value)
  if (/^\d{8}$/.test(s)) return `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}`
  return s
}

function formatTime(value) {
  if (!value) return '—'
  let s = String(value)
  if (/^\d{5}$/.test(s)) s = `0${s}`
  if (/^\d{6}$/.test(s)) return `${s.slice(0, 2)}:${s.slice(2, 4)}:${s.slice(4, 6)}`
  if (/^\d{4}$/.test(s)) return `${s.slice(0, 2)}:${s.slice(2, 4)}`
  return s
}

export default function TransactionTablePage() {
  const { id: companyId, fileId } = useParams()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(12)
  const [selectedRow, setSelectedRow] = useState(null)

  const baseTransactions = INIT_TX.filter((t) => t.companyId === companyId)
  const transactions = baseTransactions.length ? baseTransactions : INIT_TX.map((t) => ({ ...t, companyId }))
  const file = transactions.find((t) => String(t.id) === String(fileId))

  const filteredRows = useMemo(() => {
    const rows = Array.isArray(TRANSACTION_DATA?.rows) ? TRANSACTION_DATA.rows : []
    const q = query.trim().toLowerCase()
    if (!q) return rows
    return rows.filter((r) => {
      const merchant = String(r['가맹점명'] ?? '').toLowerCase()
      const usage = String(r['용도명'] ?? '').toLowerCase()
      return merchant.includes(q) || usage.includes(q)
    })
  }, [query])

  const totalPages = Math.max(1, Math.ceil(filteredRows.length / pageSize))
  const currentPage = Math.min(page, totalPages)
  const pagedRows = useMemo(() => {
    const start = (currentPage - 1) * pageSize
    return filteredRows.slice(start, start + pageSize)
  }, [filteredRows, currentPage, pageSize])

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Transaction Table</h1>
          <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
            {file?.fileName ?? `File ${fileId}`} · {filteredRows.length} rows
          </p>
        </div>
        <Link
          href={`/companies/${companyId}/transactions`}
          className="inline-flex items-center gap-2 px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm font-medium text-slate-600 hover:bg-slate-50"
        >
          Back to files
        </Link>
      </div>

      <div className="mb-4 flex flex-col lg:flex-row justify-between items-start lg:items-center gap-4">
        <div className="relative">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: '#94A3B8' }} />
          <input
            value={query}
            onChange={(e) => { setQuery(e.target.value); setPage(1) }}
            placeholder="Search transactions"
            className="pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-slate-400 h-[36px]"
          />
        </div>
      </div>

      <div className="rounded-xl overflow-hidden" style={card}>
        <div className="overflow-x-auto">
          <table className="w-full text-left" style={{ borderCollapse: 'separate', borderSpacing: 0, tableLayout: 'fixed' }}>
            <thead>
              <tr className="text-[12px] font-semibold" style={{ background: '#F8FAFC', color: '#64748B' }}>
                {['No', '승인일자', '승인시간', '가맹점명', '공급금액', '용도코드', '용도명', '방법'].map((h, idx) => {
                  const base = { borderTop: '1px solid #E2E8F0', borderBottom: '1px solid #E2E8F0', padding: '12px 16px', whiteSpace: 'nowrap' }
                  if (idx === 0) { base.borderLeft = '1px solid #E2E8F0'; base.width = '60px' }
                  if (h === '방법') { base.textAlign = 'right' }
                  if (h === '가맹점명') { base.width = '250px' }
                  if (h === '용도코드') { base.width = '240px' }
                  if (h === '용도명') { base.width = '200px' }
                  if (idx === 7) base.borderRight = '1px solid #E2E8F0'
                  return (
                    <th key={h} style={base}>{h}</th>
                  )
                })}
              </tr>
            </thead>
            <tbody className="text-sm">
              {pagedRows.map((row, idx) => (
                <tr
                  key={row.pk ?? idx}
                  className="group cursor-pointer hover:bg-slate-50 transition-colors"
                  style={{ borderBottom: '1px solid #F1F5F9', background: idx % 2 ? '#FBFCFF' : '#FFFFFF' }}
                  onClick={() => setSelectedRow(row)}
                >
                  <td style={{ padding: '14px 16px', color: '#64748B' }}>{(currentPage - 1) * pageSize + idx + 1}</td>
                  <td style={{ padding: '14px 16px', color: '#64748B' }}>{formatDate(row['승인일자'])}</td>
                  <td style={{ padding: '14px 16px', color: '#64748B' }}>{formatTime(row['승인시간'])}</td>
                  <td style={{ padding: '14px 16px', color: '#334155', fontWeight: 500, minWidth: '250px' }}>{row['가맹점명'] ?? '—'}</td>
                  <td style={{ padding: '14px 16px', color: '#0F172A' }}>{row['공급금액'] ?? '—'}</td>
                  <td style={{ padding: '14px 16px', color: '#64748B', minWidth: '240px', whiteSpace: 'normal', wordBreak: 'break-word', lineHeight: 1.2 }}>
                    {(() => {
                      const raw = row['용도코드']
                      if (!raw) return '—'
                      const parts = Array.isArray(raw)
                        ? raw
                        : String(raw).split(',').map((v) => v.trim()).filter(Boolean)
                      return parts.map((v, i) => (
                        <span
                          key={`${v}-${i}`}
                          className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold mb-1 mr-1.5"
                          style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.1)' }}
                        >
                          {v}
                        </span>
                      ))
                    })()}
                  </td>
                  <td style={{ padding: '14px 16px', color: '#64748B', minWidth: '200px' }}>{row['용도명'] ?? '—'}</td>
                  <td style={{ padding: '14px 16px', textAlign: 'right' }}>
                    {row['방법'] === 'AI' ? (
                      <span
                        className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold"
                        style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.1)' }}
                      >
                        AI
                      </span>
                    ) : (
                      <span style={{ color: '#64748B' }}>{row['방법'] ?? '—'}</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 px-6 py-4" style={{ borderTop: '1px solid #F1F5F9', background: '#F8FAFC' }}>
          <p className="text-xs" style={{ color: '#94A3B8' }}>
            Showing {(currentPage - 1) * pageSize + 1}–{Math.min(currentPage * pageSize, filteredRows.length)} of {filteredRows.length}
          </p>
          <div className="flex items-center gap-2">
            <span className="text-xs" style={{ color: '#94A3B8' }}>Rows</span>
            <select
              className="px-2.5 py-1.5 bg-white border border-slate-200 rounded-lg text-xs text-slate-700"
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1) }}
            >
              {[8, 12, 16, 20].map((n) => (
                <option key={n} value={n}>{n}</option>
              ))}
            </select>
            <button
              className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-50"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
            >
              Prev
            </button>
            <span className="text-xs" style={{ color: '#64748B' }}>{currentPage}/{totalPages}</span>
            <button
              className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-50"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={currentPage === totalPages}
            >
              Next
            </button>
          </div>
        </div>
      </div>

      {selectedRow && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4"
          onClick={() => setSelectedRow(null)}
        >
          <div
            className="bg-white w-full max-w-lg rounded-2xl shadow-2xl flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="px-6 py-4 flex items-start justify-between" style={{ borderBottom: '1px solid #E2E8F0', background: 'linear-gradient(135deg, #F8FAFC 0%, #FFFFFF 100%)' }}>
              <div>
                <h2 className="text-base font-bold text-slate-800">Transaction Details</h2>
                <p className="text-sm text-slate-500 mt-0.5 truncate max-w-xs">{selectedRow['가맹점명'] ?? '—'}</p>
              </div>
              <button
                onClick={() => setSelectedRow(null)}
                className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-1.5 rounded-lg transition-colors focus:outline-none ml-4 mt-0.5 flex-shrink-0"
              >
                <X size={18} />
              </button>
            </div>

            <div className="overflow-y-auto max-h-[75vh]">
              {/* Amount Cards */}
              <div className="px-6 pt-5 pb-4">
                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-xl p-3 text-center" style={{ background: 'rgba(26,50,216,0.06)', border: '1px solid rgba(26,50,216,0.14)' }}>
                    <p className="text-[10px] font-medium text-slate-400 mb-1">공급금액</p>
                    <p className="text-sm font-bold" style={{ color: '#1A32D8' }}>
                      {selectedRow['공급금액'] ? Number(selectedRow['공급금액']).toLocaleString() : '—'}
                    </p>
                  </div>
                  <div className="rounded-xl p-3 text-center" style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                    <p className="text-[10px] font-medium text-slate-400 mb-1">부가세액</p>
                    <p className="text-sm font-bold text-slate-700">
                      {selectedRow['부가세액'] ? Number(selectedRow['부가세액']).toLocaleString() : '—'}
                    </p>
                  </div>
                  <div className="rounded-xl p-3 text-center" style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                    <p className="text-[10px] font-medium text-slate-400 mb-1">과세유형</p>
                    <p className="text-sm font-semibold text-slate-700">{selectedRow['과세유형'] ?? '—'}</p>
                  </div>
                </div>
              </div>

              <div style={{ borderTop: '1px solid #F1F5F9' }} />

              {/* Basic Info */}
              <div className="px-6 py-4">
                <h3 className="text-[10px] font-semibold text-slate-400 uppercase tracking-widest mb-3">기본 정보</h3>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">승인일자</p>
                      <p className="text-sm font-medium text-slate-800">{formatDate(selectedRow['승인일자'])}</p>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">승인시간</p>
                      <p className="text-sm font-medium text-slate-800">{formatTime(selectedRow['승인시간'])}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">가맹점업종코드</p>
                      <p className="text-sm font-medium text-slate-800">{selectedRow['가맹점업종코드'] ?? '—'}</p>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">가맹점업종명</p>
                      <p className="text-sm font-medium text-slate-800">{selectedRow['가맹점업종명'] ?? '—'}</p>
                    </div>
                  </div>
                  <div>
                    <p className="text-[11px] text-slate-400 mb-0.5">가맹점사업자번호</p>
                    <p className="text-sm font-medium text-slate-800">{selectedRow['가맹점사업자번호'] ?? '—'}</p>
                  </div>
                </div>
              </div>

              <div style={{ borderTop: '1px solid #F1F5F9' }} />

              {/* Classification */}
              <div className="px-6 py-4">
                <h3 className="text-[10px] font-semibold text-slate-400 uppercase tracking-widest mb-3">분류 결과</h3>
                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] text-slate-400 mb-1">용도코드</p>
                      <div>
                        {(() => {
                          const raw = selectedRow['용도코드']
                          if (!raw) return <span className="text-sm text-slate-400">—</span>
                          const parts = Array.isArray(raw) ? raw : String(raw).split(',').map((v) => v.trim()).filter(Boolean)
                          return parts.length ? parts.map((v, i) => (
                            <span key={i} className="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold mr-1 mb-1" style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.1)' }}>{v}</span>
                          )) : <span className="text-sm text-slate-400">—</span>
                        })()}
                      </div>
                    </div>
                    <div>
                      <p className="text-[11px] text-slate-400 mb-0.5">용도명</p>
                      <p className="text-sm font-medium text-slate-800">{selectedRow['용도명'] ?? '—'}</p>
                    </div>
                  </div>
                  <div>
                    <p className="text-[11px] text-slate-400 mb-1.5">방법</p>
                    {selectedRow['방법'] === 'AI' ? (
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold" style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.1)' }}>
                        AI
                      </span>
                    ) : (
                      <span className="text-sm font-medium text-slate-700">{selectedRow['방법'] ?? '—'}</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Reason */}
              {(selectedRow['가맹점업종명'] || selectedRow['용도코드']) && (
                <>
                  <div style={{ borderTop: '1px solid #F1F5F9' }} />
                  <div className="px-6 py-4 pb-6">
                    <h3 className="text-[10px] font-semibold text-slate-400 uppercase tracking-widest mb-2">사유</h3>
                    <div className="rounded-xl p-4 text-sm text-slate-600 leading-relaxed" style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                      {selectedRow['가맹점업종명'] ? `업종코드 ${selectedRow['가맹점업종코드'] ?? ''}은 ${selectedRow['가맹점업종명']}에 해당합니다. ` : ''}
                      {selectedRow['가맹점명'] ? `${selectedRow['가맹점명']} 거래는 ${selectedRow['용도명'] ?? '해당 비용'}으로 분류될 가능성이 높습니다. ` : ''}
                      {selectedRow['용도코드'] ? `계정 ${selectedRow['용도코드']}${selectedRow['용도명'] ? ` (${selectedRow['용도명']})` : ''}로 지정했습니다.` : ''}
                    </div>
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
