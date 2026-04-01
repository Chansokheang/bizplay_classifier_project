'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import {
  Upload,
  FileSpreadsheet,
  CheckCircle2,
  Clock,
  Loader2,
  AlertCircle,
} from 'lucide-react'
import { TRANSACTIONS as INIT_TX } from '../../../../../lib/mock-data'

const STATUS_CFG = {
  completed: { label: 'Completed', color: '#10B981', bg: 'rgba(16,185,129,0.1)', Icon: CheckCircle2 },
  pending: { label: 'Pending', color: '#F59E0B', bg: 'rgba(245,158,11,0.1)', Icon: Clock },
  processing: { label: 'Processing', color: '#3B82F6', bg: 'rgba(59,130,246,0.1)', Icon: Loader2 },
  failed: { label: 'Failed', color: '#EF4444', bg: 'rgba(239,68,68,0.1)', Icon: AlertCircle },
}

const card = { background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }

export default function TransactionsPage() {
  const { id: companyId } = useParams()
  const baseTransactions = INIT_TX.filter((t) => t.companyId === companyId)
  const [transactions, setTransactions] = useState(
    baseTransactions.length ? baseTransactions : INIT_TX.map((t) => ({ ...t, companyId }))
  )
  const fileRef = useRef(null)

  useEffect(() => {
    try {
      const index = transactions.reduce((acc, tx) => {
        acc[String(tx.id)] = tx.fileName
        return acc
      }, {})
      const raw = window.localStorage.getItem('filesIndex')
      const existing = raw ? JSON.parse(raw) : {}
      existing[companyId] = index
      window.localStorage.setItem('filesIndex', JSON.stringify(existing))
    } catch {
      // ignore storage errors
    }
  }, [transactions, companyId])

  const addFile = (file) => {
    const n = {
      id: String(Date.now()),
      fileName: file.name,
      uploadedAt: new Date().toISOString().split('T')[0],
      records: Math.floor(Math.random() * 300 + 50),
      classified: 0,
      unclassified: 0,
      status: 'pending',
      companyId,
    }
    n.unclassified = n.records
    setTransactions((prev) => [n, ...prev])
  }

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Transactions</h1>
          <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
            Upload files and open a file to view the transaction table.
          </p>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-3">
        <button
          onClick={() => fileRef.current?.click()}
          className="flex items-center gap-2 px-3 py-2 bg-white border border-slate-200 rounded-lg text-sm font-medium text-slate-600 transition-colors hover:bg-slate-50"
        >
          <Upload size={16} className="text-slate-400" />
          Upload File
        </button>
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx,.xls,.csv"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files[0]
            if (f) addFile(f)
            e.target.value = ''
          }}
        />
      </div>

      <div className="rounded-xl overflow-hidden" style={card}>
        <div className="overflow-x-auto">
          <table className="w-full text-left" style={{ borderCollapse: 'separate', borderSpacing: 0 }}>
            <thead>
              <tr className="text-[12px] font-semibold" style={{ background: '#F8FAFC', color: '#64748B' }}>
                {['File', 'Uploaded', 'Records', 'Classified', 'Status', 'Action'].map((h, idx) => {
                  const base = {
                    borderTop: '1px solid #E2E8F0',
                    borderBottom: '1px solid #E2E8F0',
                    padding: '12px 16px',
                    whiteSpace: 'nowrap',
                  }
                  if (idx === 0) base.borderLeft = '1px solid #E2E8F0'
                  if (idx === 5) base.borderRight = '1px solid #E2E8F0'
                  return (
                    <th key={h} style={base}>{h}</th>
                  )
                })}
              </tr>
            </thead>
            <tbody className="text-sm">
              {transactions.map((tx, idx) => {
                const cfg = STATUS_CFG[tx.status] ?? STATUS_CFG.pending
                return (
                  <tr key={tx.id} style={{ borderBottom: '1px solid #F1F5F9', background: idx % 2 ? '#FBFCFF' : '#FFFFFF' }}>
                    <td style={{ padding: '14px 16px' }}>
                      <div className="flex items-center gap-2.5">
                        <div className="flex items-center justify-center w-9 h-9 rounded-lg" style={{ background: 'rgba(59,130,246,0.08)' }}>
                          <FileSpreadsheet size={16} color="#3B82F6" />
                        </div>
                        <div>
                          <p className="text-sm font-semibold" style={{ color: '#0F172A' }}>{tx.fileName}</p>
                          <p className="text-xs" style={{ color: '#94A3B8' }}>{tx.records} rows</p>
                        </div>
                      </div>
                    </td>
                    <td style={{ padding: '14px 16px', color: '#64748B' }}>{tx.uploadedAt}</td>
                    <td style={{ padding: '14px 16px', color: '#0F172A' }}>{tx.records}</td>
                    <td style={{ padding: '14px 16px', color: '#64748B' }}>{tx.classified}/{tx.records}</td>
                    <td style={{ padding: '14px 16px' }}>
                      <span className="inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full" style={{ background: cfg.bg, color: cfg.color }}>
                        <cfg.Icon size={11} />{cfg.label}
                      </span>
                    </td>
                    <td style={{ padding: '14px 16px' }}>
                      <Link
                        href={`/companies/${companyId}/transactions/${tx.id}`}
                        className="inline-flex items-center justify-center px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                      >
                        View Table
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
