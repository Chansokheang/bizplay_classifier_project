'use client'

import { useCallback, useEffect, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import { X, Loader2, AlertCircle } from 'lucide-react'
import { getFileTransactions } from '../../../../../../service/transactionService'

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
  const { data: session } = useSession()
  const token = session?.accessToken

  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(100)
  
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  
  const [data, setData] = useState({
    items: [],
    totalRows: 0,
    totalPages: 1
  })
  
  const [selectedRow, setSelectedRow] = useState(null)

  const fetchData = useCallback(async () => {
    if (!token) return
    setLoading(true)
    setError('')
    try {
      const res = await getFileTransactions(fileId, page, pageSize, token)
      const p = res?.payload || {}
      setData({
        items: p.items || [],
        totalRows: p.totalRows || 0,
        totalPages: p.totalPages || 1
      })
    } catch (err) {
      console.error(err)
      setError('Failed to load transactions.')
    } finally {
      setLoading(false)
    }
  }, [fileId, page, pageSize, token])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const headers = ['No', '승인일자', '가맹점명', '가맹점업종명', '공급금액', '용도코드', '방법']

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Transaction Data</h1>
          <p className="mt-1 text-[13px] font-medium" style={{ color: '#94A3B8' }}>
            {data.totalRows} records found in file
          </p>
        </div>
        <Link
          href={`/companies/${companyId}/transactions`}
          className="inline-flex items-center gap-2 px-4 py-2 bg-white border border-slate-200 rounded-xl text-[13px] font-bold text-slate-600 hover:bg-slate-50 transition-colors shadow-sm"
        >
          Back to files
        </Link>
      </div>

      {error && (
        <div className="mb-4 px-4 py-3 rounded-xl text-[13px] font-semibold flex items-center gap-2 text-red-600 bg-red-50 border border-red-100">
          <AlertCircle size={14} />{error}
        </div>
      )}

      <div className="rounded-2xl overflow-hidden" style={card}>
        <div className="overflow-x-auto min-h-[400px] relative">
          {loading && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/50 backdrop-blur-[2px]">
              <Loader2 size={28} className="animate-spin text-[#1A32D8]" />
            </div>
          )}
          <table className="w-full text-left" style={{ borderCollapse: 'collapse', tableLayout: 'fixed' }}>
            <colgroup>
              <col style={{ width: '60px' }} />
              <col style={{ width: '110px' }} />
              <col />
              <col style={{ width: '180px' }} />
              <col style={{ width: '130px' }} />
              <col style={{ width: '160px' }} />
              <col style={{ width: '120px' }} />
            </colgroup>
            <thead>
              <tr className="text-[12px] font-bold uppercase tracking-wider text-slate-500" style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                <th className="px-4 py-3 text-center">No</th>
                <th className="px-4 py-3">승인일자</th>
                <th className="px-4 py-3">가맹점명</th>
                <th className="px-4 py-3">가맹점업종명</th>
                <th className="px-4 py-3">공급금액</th>
                <th className="px-4 py-3">용도코드</th>
                <th className="px-4 py-3 text-center">방법</th>
              </tr>
            </thead>
            <tbody className="text-[13px]">
              {data.items.length === 0 && !loading ? (
                <tr>
                  <td colSpan={headers.length} className="px-6 py-20 text-center text-slate-400">
                    <p className="text-[14px] font-bold text-slate-500">No transactions found</p>
                  </td>
                </tr>
              ) : (
                data.items.map((row, idx) => (
                  <tr
                    key={row.pk ?? idx}
                    className="group cursor-pointer transition-colors hover:bg-slate-50/80"
                    style={{ borderBottom: idx < data.items.length - 1 ? '1px solid #F1F5F9' : 'none' }}
                    onClick={() => setSelectedRow(row)}
                  >
                    {/* Index */}
                    <td className="px-4 py-4 text-center font-bold text-slate-400">{row.row_index || (page - 1) * pageSize + idx + 1}</td>
                    
                    {/* 승인일자 */}
                    <td className="px-4 py-4 font-medium text-slate-500">{formatDate(row['승인일자'])}</td>
                    
                    {/* 가맹점명 */}
                    <td className="px-4 py-4 font-bold text-slate-800 truncate" title={row['가맹점명']}>{row['가맹점명'] ?? '—'}</td>
                    
                    {/* 가맹점업종명 */}
                    <td className="px-4 py-4 font-semibold text-slate-500 truncate" title={row['가맹점업종명']}>{row['가맹점업종명'] ?? '—'}</td>
                    
                    {/* 공급금액 */}
                    <td className="px-4 py-4 font-bold text-slate-700">{row['공급금액'] ? Number(row['공급금액']).toLocaleString() : '—'}</td>
                    
                    {/* 용도코드 */}
                    <td className="px-4 py-4" style={{ whiteSpace: 'normal', wordBreak: 'break-word', lineHeight: 1.2 }}>
                      {(() => {
                        const raw = row['용도코드']
                        if (!raw) return <span className="text-slate-400 font-semibold">—</span>
                        const parts = Array.isArray(raw) ? raw : String(raw).split(',').map((v) => v.trim()).filter(Boolean)
                        return parts.map((v, i) => (
                          <span
                            key={`${v}-${i}`}
                            className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold mb-1 mr-1.5"
                            style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.06)', border: '1px solid rgba(26,50,216,0.1)' }}
                          >
                            {v}
                          </span>
                        ))
                      })()}
                    </td>
                    
                    {/* 방법 */}
                    <td className="px-4 py-4 text-center">
                      <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-bold text-slate-600 bg-slate-100 border border-slate-200">
                        {row['방법'] ?? '—'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 px-6 py-4" style={{ borderTop: '1px solid #F1F5F9', background: '#F8FAFC' }}>
          <p className="text-[12px] font-medium text-slate-500">
            Showing <strong className="text-slate-700">{data.totalRows ? (page - 1) * pageSize + 1 : 0}</strong> to <strong className="text-slate-700">{Math.min(page * pageSize, data.totalRows)}</strong> of <strong className="text-slate-700">{data.totalRows}</strong> records
          </p>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1.5 mr-2">
              <span className="text-[12px] font-medium text-slate-500 hidden sm:inline-block">Rows per page</span>
              <select
                className="px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-[12px] font-bold text-slate-700 focus:outline-none focus:border-[#1A32D8]"
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1) }}
              >
                {[50, 100, 200, 500].map((n) => (
                  <option key={n} value={n}>{n}</option>
                ))}
              </select>
            </div>
            
            <div className="flex items-center gap-1.5">
              <button
                className="px-3 py-1.5 rounded-lg text-[12px] font-bold transition-all border disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-50 shadow-sm"
                style={{ background: '#FFFFFF', borderColor: '#E2E8F0', color: '#475569' }}
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page <= 1 || loading}
              >
                Prev
              </button>
              <div className="px-3 py-1.5 text-[12px] font-bold text-slate-600 min-w-[60px] text-center">
                {page} / {data.totalPages || 1}
              </div>
              <button
                className="px-3 py-1.5 rounded-lg text-[12px] font-bold transition-all border disabled:opacity-40 disabled:cursor-not-allowed hover:bg-slate-50 shadow-sm"
                style={{ background: '#FFFFFF', borderColor: '#E2E8F0', color: '#475569' }}
                onClick={() => setPage(p => Math.min(data.totalPages, p + 1))}
                disabled={page >= data.totalPages || loading}
              >
                Next
              </button>
            </div>
          </div>
        </div>
      </div>

      {selectedRow && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-fade-in"
          onClick={() => setSelectedRow(null)}
        >
          <div
            className="bg-white w-full max-w-lg rounded-2xl shadow-2xl flex flex-col overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            {/* Header */}
            <div className="px-6 py-4 flex items-start justify-between" style={{ borderBottom: '1px solid #E2E8F0', background: 'linear-gradient(135deg, #F8FAFC 0%, #FFFFFF 100%)' }}>
              <div>
                <h2 className="text-[16px] font-bold text-slate-800">Transaction Details</h2>
                <p className="text-[13px] font-semibold text-slate-500 mt-0.5 truncate max-w-xs">{selectedRow['가맹점명'] ?? '—'}</p>
              </div>
              <button
                onClick={() => setSelectedRow(null)}
                className="text-slate-400 hover:text-slate-600 hover:bg-slate-100 p-1.5 rounded-lg transition-colors focus:outline-none ml-4 mt-0.5 flex-shrink-0"
              >
                <X size={18} />
              </button>
            </div>

            <div className="overflow-y-auto max-h-[75vh]" style={{ WebkitOverflowScrolling: 'touch' }}>
              {/* Amount Cards */}
              <div className="px-6 pt-5 pb-4">
                <div className="grid grid-cols-3 gap-3">
                  <div className="rounded-xl p-3 text-center" style={{ background: 'rgba(26,50,216,0.06)', border: '1px solid rgba(26,50,216,0.14)' }}>
                    <p className="text-[10px] font-bold text-[#1A32D8] mb-1">공급금액</p>
                    <p className="text-[15px] font-bold" style={{ color: '#1A32D8' }}>
                      {selectedRow['공급금액'] ? Number(selectedRow['공급금액']).toLocaleString() : '—'}
                    </p>
                  </div>
                  <div className="rounded-xl p-3 text-center" style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                    <p className="text-[10px] font-bold text-slate-400 mb-1">부가세액</p>
                    <p className="text-[14px] font-bold text-slate-700">
                      {selectedRow['부가세액'] ? Number(selectedRow['부가세액']).toLocaleString() : '—'}
                    </p>
                  </div>
                  <div className="rounded-xl p-3 text-center" style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
                    <p className="text-[10px] font-bold text-slate-400 mb-1">과세유형</p>
                    <p className="text-[14px] font-bold text-slate-700">{selectedRow['과세유형'] ?? '—'}</p>
                  </div>
                </div>
              </div>

              <div style={{ borderTop: '1px solid #F1F5F9' }} />

              {/* Basic Info */}
              <div className="px-6 py-5">
                <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4 border-b border-dashed border-slate-200 pb-2">기본 정보</h3>
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-0.5">승인일자</p>
                      <p className="text-[13px] font-bold text-slate-800">{formatDate(selectedRow['승인일자'])}</p>
                    </div>
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-0.5">승인시간</p>
                      <p className="text-[13px] font-bold text-slate-800">{formatTime(selectedRow['승인시간'])}</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-0.5">가맹점업종코드</p>
                      <p className="text-[13px] font-bold text-slate-800">{selectedRow['가맹점업종코드'] ?? '—'}</p>
                    </div>
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-0.5">가맹점업종명</p>
                      <p className="text-[13px] font-bold text-slate-800">{selectedRow['가맹점업종명'] ?? '—'}</p>
                    </div>
                  </div>
                  <div>
                    <p className="text-[11px] font-bold text-slate-400 mb-0.5">가맹점사업자번호</p>
                    <p className="text-[13px] font-bold text-slate-800">{selectedRow['가맹점사업자번호'] ?? '—'}</p>
                  </div>
                </div>
              </div>

              <div style={{ borderTop: '1px solid #F1F5F9' }} />

              {/* Classification */}
              <div className="px-6 py-5">
                <h3 className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-4 border-b border-dashed border-slate-200 pb-2">분류 결과</h3>
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-1">용도코드</p>
                      <div>
                        {(() => {
                          const raw = selectedRow['용도코드']
                          if (!raw) return <span className="text-[13px] font-semibold text-slate-400">—</span>
                          const parts = Array.isArray(raw) ? raw : String(raw).split(',').map((v) => v.trim()).filter(Boolean)
                          return parts.length ? parts.map((v, i) => (
                            <span key={i} className="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-bold mr-1 mb-1" style={{ color: '#1A32D8', background: 'rgba(26,50,216,0.08)', border: '1px solid rgba(26,50,216,0.1)' }}>{v}</span>
                          )) : <span className="text-[13px] font-semibold text-slate-400">—</span>
                        })()}
                      </div>
                    </div>
                    <div>
                      <p className="text-[11px] font-bold text-slate-400 mb-0.5">용도명</p>
                      <p className="text-[13px] font-bold text-slate-800">{selectedRow['용도명'] ?? '—'}</p>
                    </div>
                  </div>
                  <div>
                    <p className="text-[11px] font-bold text-slate-400 mb-1.5">방법 / Reason</p>
                    <div className="flex items-center gap-2">
                       <span className="inline-flex items-center px-2.5 py-1 rounded-full text-[11px] font-bold text-slate-600 bg-slate-100 border border-slate-200">
                         {selectedRow['방법'] ?? '—'}
                       </span>
                       <span className="text-[12px] font-medium text-slate-500">
                         {selectedRow['Reason'] || ''}
                       </span>
                    </div>
                  </div>
                </div>
              </div>

            </div>
          </div>
        </div>
      )}
    </div>
  )
}
