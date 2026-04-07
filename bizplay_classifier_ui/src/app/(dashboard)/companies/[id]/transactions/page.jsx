'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import {
  Upload, FileSpreadsheet, CheckCircle2, Clock,
  Loader2, AlertCircle, ArrowUpRight, FileUp, BarChart3, Layers, RefreshCw, RotateCcw, X
} from 'lucide-react'
import { uploadTransactions, getOutputFiles } from '../../../../../service/transactionService'

const STATUS_CFG = {
  completed:  { label: 'Completed',  color: '#059669', bg: 'rgba(5,150,105,0.08)',  border: 'rgba(5,150,105,0.2)',  Icon: CheckCircle2 },
  pending:    { label: 'Pending',    color: '#D97706', bg: 'rgba(217,119,6,0.08)',  border: 'rgba(217,119,6,0.2)',  Icon: Clock },
  processing: { label: 'Processing', color: '#2563EB', bg: 'rgba(37,99,235,0.08)',  border: 'rgba(37,99,235,0.2)',  Icon: Loader2 },
  failed:     { label: 'Failed',     color: '#DC2626', bg: 'rgba(220,38,38,0.08)',  border: 'rgba(220,38,38,0.2)',  Icon: AlertCircle },
}

function StatCard({ icon: Icon, label, value, color }) {
  return (
    <div className="flex items-center gap-4 px-5 py-4 rounded-2xl"
      style={{ background: 'rgba(255,255,255,0.6)', backdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.8)', boxShadow: '0 2px 8px rgba(15,23,42,0.04)' }}>
      <div className="flex items-center justify-center w-10 h-10 rounded-xl flex-shrink-0"
        style={{ background: `${color}15`, border: `1px solid ${color}25` }}>
        <Icon size={18} style={{ color }} />
      </div>
      <div>
        <p className="text-[22px] font-bold leading-none text-slate-800">{value}</p>
        <p className="text-[12px] font-semibold text-slate-400 mt-0.5">{label}</p>
      </div>
    </div>
  )
}

export default function TransactionsPage() {
  const { id: companyId } = useParams()
  const { data: session } = useSession()
  const token = session?.accessToken

  // ─── State ───────────────────────────────────────────────────────────────
  const [files, setFiles] = useState([])           // files from API
  const [localPending, setLocalPending] = useState([]) // locally added (uploading)
  const [loadingFiles, setLoadingFiles] = useState(true)
  const [uploadError, setUploadError] = useState('')
  const [isDragging, setIsDragging] = useState(false)
  const [sheetSelectData, setSheetSelectData] = useState(null)
  const fileRef = useRef(null)
  const reuploadRefs = useRef({}) // map of failedId -> input element

  // ─── Fetch output files from API ─────────────────────────────────────────
  const fetchFiles = useCallback(async () => {
    if (!token) return
    try {
      const res = await getOutputFiles(companyId, token)
      const payload = Array.isArray(res?.payload) ? res.payload : []
      const mapped = payload.map(({ file: f, classifySummary: s }) => ({
        id: f.fileId,
        fileName: f.originalFileName,
        sheetName: f.sheetName ?? null,
        uploadedAt: f.createdDate ? new Date(f.createdDate).toISOString().split('T')[0] : '—',
        fileUrl: f.fileUrl,
        status: 'completed',
        records: s?.totalRows ?? null,
        classified: s?.processedRows ?? null,
      }))
      setFiles(mapped)
    } catch (e) {
      console.error('Failed to fetch files:', e)
    } finally {
      setLoadingFiles(false)
    }
  }, [companyId, token])

  useEffect(() => { fetchFiles() }, [fetchFiles])

  // ─── Document Parsing & Selection ──────────────────────────────────────────
  const processFileSelection = async (file, failedId = null) => {
    if (!file) return

    if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
      try {
        const XLSX = await import('xlsx')
        const reader = new FileReader()
        reader.onload = (e) => {
          try {
            const data = new Uint8Array(e.target.result)
            const workbook = XLSX.read(data, { type: 'array' })
            const sheets = workbook.SheetNames
            
            if (sheets.length > 1) {
              setSheetSelectData({ file, sheets, failedId })
            } else {
              if (failedId) handleReupload(file, failedId, sheets[0])
              else handleUpload(file, sheets[0])
            }
          } catch (err) {
            console.error('Error reading excel file:', err)
            if (failedId) handleReupload(file, failedId)
            else handleUpload(file)
          }
        }
        reader.readAsArrayBuffer(file)
      } catch (err) {
        if (failedId) handleReupload(file, failedId)
        else handleUpload(file)
      }
    } else {
      if (failedId) handleReupload(file, failedId)
      else handleUpload(file)
    }
  }

  // ─── Upload handler ───────────────────────────────────────────────────────
  const handleUpload = async (file, sheetName = null) => {
    setUploadError('')
    const tempId = `uploading-${Date.now()}`
    const tempEntry = {
      id: tempId,
      fileName: file.name,
      uploadedAt: new Date().toISOString().split('T')[0],
      status: 'processing',
      records: null,
      classified: null,
      sheetName: null,
    }
    setLocalPending(prev => [tempEntry, ...prev])

    try {
      const res = await uploadTransactions(file, companyId, token, sheetName)
      const p = res?.payload ?? {}
      const summary = p.fileClassifySummary ?? {}

      // Immediately update the pending row with real data from the response
      const enrichedEntry = {
        ...tempEntry,
        id: p.fileId ?? tempId,
        status: 'completed',
        records: p.totalRows ?? summary.totalRows ?? null,
        classified: summary.processedRows ?? (p.ruleMatchedRows + p.aiMatchedRows) ?? null,
        sheetName: null, // not returned from upload endpoint
      }

      // Replace the temp entry with the enriched one
      setLocalPending(prev => prev.map(f => f.id === tempId ? enrichedEntry : f))

      // Then refresh the API list in the background to sync sheetName + fileUrl
      await fetchFiles()

      // Remove from pending since it's now in the API list
      setLocalPending(prev => prev.filter(f => f.id !== tempId && f.id !== p.fileId))
    } catch (err) {
      setLocalPending(prev =>
        prev.map(f => f.id === tempId ? { ...f, status: 'failed' } : f)
      )
      setUploadError(err.message || 'Upload failed. Please try again.')
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setIsDragging(false)
    const file = e.dataTransfer.files[0]
    if (file) processFileSelection(file)
  }

  // ─── Reupload handler for failed rows ────────────────────────────────────
  const handleReupload = async (file, failedId, sheetName = null) => {
    setUploadError('')
    // Replace the failed entry in-place with processing state
    setLocalPending(prev => prev.map(f =>
      f.id === failedId ? { ...f, status: 'processing', records: null, classified: null } : f
    ))
    try {
      const res = await uploadTransactions(file, companyId, token)
      const p = res?.payload ?? {}
      const summary = p.fileClassifySummary ?? {}
      setLocalPending(prev => prev.map(f =>
        f.id === failedId ? {
          ...f,
          id: p.fileId ?? failedId,
          status: 'completed',
          records: p.totalRows ?? summary.totalRows ?? null,
          classified: summary.processedRows ?? null,
        } : f
      ))
      await fetchFiles()
      setLocalPending(prev => prev.filter(f => f.id !== failedId && f.id !== p.fileId))
    } catch (err) {
      setLocalPending(prev => prev.map(f =>
        f.id === failedId ? { ...f, status: 'failed' } : f
      ))
      setUploadError(err.message || 'Reupload failed. Please try again.')
    }
  }

  // ─── Cancel failed upload ────────────────────────────────────────────────
  const handleCancelFailed = (failedId) => {
    setLocalPending(prev => prev.filter(f => f.id !== failedId))
    setUploadError('')
  }

  // ─── Merged file list (local pending first, then API files) ───────────────
  const allFiles = [...localPending, ...files]
  const totalFiles = allFiles.length
  const completedFiles = files.length

  return (
    <div className="py-8 animate-fade-in">

      {/* ── Page Header ── */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Transactions</h1>
          <p className="mt-1 text-sm text-slate-400">Upload files and review classification output.</p>
        </div>
        <button onClick={fetchFiles}
          className="flex items-center gap-2 px-3 py-2 rounded-xl text-[13px] font-bold text-slate-500 transition-colors hover:bg-white/70"
          style={{ border: '1px solid #E2E8F0' }}>
          <RefreshCw size={13} />
          Refresh
        </button>
      </div>

      {/* ── Stats Row ── */}
      <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-8">
        <StatCard icon={FileSpreadsheet} label="Total Files"      value={totalFiles}       color="#1A32D8" />
        <StatCard icon={BarChart3}       label="Completed Files"  value={completedFiles}   color="#059669" />
        <StatCard icon={Layers}          label="Processing"       value={localPending.length} color="#2563EB" />
      </div>

      {/* ── Upload Zone ── */}
      <div
        className="relative rounded-2xl mb-8 flex flex-col items-center justify-center text-center p-8 cursor-pointer transition-all duration-200"
        style={{
          background: isDragging ? 'rgba(26,50,216,0.04)' : 'rgba(255,255,255,0.5)',
          backdropFilter: 'blur(12px)',
          border: `2px dashed ${isDragging ? '#1A32D8' : 'rgba(26,50,216,0.2)'}`,
          boxShadow: isDragging ? '0 0 0 4px rgba(26,50,216,0.06)' : 'none',
          minHeight: '160px',
        }}
        onClick={() => fileRef.current?.click()}
        onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
      >
        <div className="flex items-center justify-center w-12 h-12 rounded-2xl mb-3"
          style={{ background: 'rgba(26,50,216,0.08)', border: '1px solid rgba(26,50,216,0.12)' }}>
          <FileUp size={22} style={{ color: '#1A32D8' }} />
        </div>
        <p className="text-[15px] font-bold text-slate-700 mb-1">
          {isDragging ? 'Drop your file here' : 'Upload transaction file'}
        </p>
        <p className="text-[12px] font-semibold text-slate-400">
          Drag & drop or click to browse · <span className="text-slate-500">.xlsx, .xls, .csv</span>
        </p>
        <button
          className="mt-4 flex items-center gap-2 px-4 py-2 rounded-xl text-[13px] font-bold transition-all"
          style={{ background: 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)', color: '#fff', boxShadow: '0 2px 8px rgba(26,50,216,0.2)' }}
          onClick={(e) => { e.stopPropagation(); fileRef.current?.click() }}
        >
          <Upload size={14} />
          Choose File
        </button>
        <input
          ref={fileRef} type="file" accept=".xlsx,.xls,.csv" className="hidden"
          onChange={(e) => { const f = e.target.files[0]; if (f) processFileSelection(f); e.target.value = '' }}
        />
      </div>

      {/* Upload error banner */}
      {uploadError && (
        <div className="mb-4 px-4 py-3 rounded-xl text-[13px] font-semibold flex items-center gap-2"
          style={{ background: 'rgba(220,38,38,0.06)', border: '1px solid rgba(220,38,38,0.15)', color: '#DC2626' }}>
          <AlertCircle size={14} />{uploadError}
        </div>
      )}

      {/* ── Files Table ── */}
      <div className="rounded-2xl overflow-hidden"
        style={{ background: 'rgba(255,255,255,0.7)', backdropFilter: 'blur(12px)', border: '1px solid rgba(255,255,255,0.9)', boxShadow: '0 4px 16px rgba(15,23,42,0.06)' }}>

        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100">
          <div>
            <h2 className="text-[14px] font-bold text-slate-800">All Files</h2>
            <p className="text-[12px] text-slate-400 font-medium">{totalFiles} file{totalFiles !== 1 ? 's' : ''} total</p>
          </div>
          <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[12px] font-bold text-slate-500"
            style={{ background: '#F8FAFC', border: '1px solid #E2E8F0' }}>
            <FileSpreadsheet size={13} className="text-slate-400" />
            OUTPUT
          </div>
        </div>

        <div className="overflow-x-auto">
          {loadingFiles ? (
            <div className="flex items-center justify-center gap-3 py-16 text-slate-400">
              <Loader2 size={18} className="animate-spin text-[#1A32D8]" />
              <span className="text-[13px] font-semibold">Loading files…</span>
            </div>
          ) : allFiles.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-400">
              <FileSpreadsheet size={36} className="mb-3 opacity-30" />
              <p className="text-[13px] font-semibold">No files uploaded yet</p>
              <p className="text-[12px] mt-1">Upload your first transaction file above</p>
            </div>
          ) : (
            <table className="w-full text-left" style={{ borderCollapse: 'collapse', tableLayout: 'fixed' }}>
              <colgroup>
                <col style={{ width: '50px' }} />
                <col />
                <col style={{ width: '160px' }} />
                <col style={{ width: '120px' }} />
                <col style={{ width: '200px' }} />
                <col style={{ width: '120px' }} />
                <col style={{ width: '140px' }} />
              </colgroup>
              <thead>
                <tr className="text-[12px] font-bold uppercase tracking-wider text-slate-500"
                  style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                  <th className="px-4 py-3 text-center">#</th>
                  <th className="px-4 py-3">File</th>
                  <th className="px-4 py-3">Sheet</th>
                  <th className="px-4 py-3 text-center">Uploaded</th>
                  <th className="px-4 py-3">Classification</th>
                  <th className="px-4 py-3 text-center">Status</th>
                  <th className="px-4 py-3 text-center">Action</th>
                </tr>
              </thead>
              <tbody>
                {allFiles.map((tx, idx) => {
                  const cfg = STATUS_CFG[tx.status] ?? STATUS_CFG.pending
                  const isProcessing = tx.status === 'processing'
                  return (
                    <tr key={tx.id}
                      className="group transition-colors hover:bg-slate-50/80"
                      style={{ borderBottom: idx < allFiles.length - 1 ? '1px solid #F1F5F9' : 'none' }}>

                      {/* Order */}
                      <td className="px-4 py-4 text-center">
                        <span className="text-[12px] font-bold text-slate-400">{idx + 1}</span>
                      </td>

                      {/* File Name */}
                      <td className="px-4 py-4">
                        <p className="text-[13px] font-bold text-slate-800 leading-none truncate">{tx.fileName}</p>
                        {isProcessing && (
                          <p className="text-[11px] font-semibold text-blue-500 mt-0.5">Uploading & processing…</p>
                        )}
                      </td>

                      {/* Sheet Name */}
                      <td className="px-4 py-4 text-[12px] font-medium text-slate-500">
                        {tx.sheetName ? (
                          <span className="px-2 py-0.5 rounded-md text-slate-600 font-semibold truncate block max-w-full"
                            style={{ background: '#F1F5F9' }}>{tx.sheetName}</span>
                        ) : <span className="text-slate-300">—</span>}
                      </td>

                      {/* Uploaded */}
                      <td className="px-4 py-4 text-[12px] font-medium text-slate-500 text-center">{tx.uploadedAt}</td>

                      {/* Classification — fraction · bar · % on one line */}
                      <td className="px-4 py-4">
                        {tx.records != null && tx.classified != null ? (
                          <div className="flex items-center gap-2">
                            <span className="text-[12px] font-bold text-slate-600 whitespace-nowrap flex-shrink-0">
                              {tx.classified}/{tx.records}
                            </span>
                            <div className="flex-1 h-1.5 rounded-full overflow-hidden bg-slate-100">
                              <div className="h-full rounded-full transition-all duration-500"
                                style={{ width: `${Math.round((tx.classified / tx.records) * 100)}%`, background: Math.round((tx.classified / tx.records) * 100) === 100 ? '#059669' : '#1A32D8' }} />
                            </div>
                            <span className="text-[11px] font-bold text-slate-400 flex-shrink-0">
                              {Math.round((tx.classified / tx.records) * 100)}%
                            </span>
                          </div>
                        ) : (
                          <div className="flex items-center gap-2">
                            <span className="inline-block w-10 h-2 rounded-full animate-pulse" style={{ background: '#E2E8F0' }} />
                            <span className="inline-block flex-1 h-1.5 rounded-full animate-pulse" style={{ background: '#E2E8F0' }} />
                            <span className="inline-block w-6 h-2 rounded-full animate-pulse" style={{ background: '#E2E8F0' }} />
                          </div>
                        )}
                      </td>

                      {/* Status */}
                      <td className="px-4 py-4 text-center">
                        <span className="inline-flex items-center gap-1.5 text-[11px] font-bold px-2.5 py-1 rounded-full"
                          style={{ background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.border}` }}>
                          <cfg.Icon size={10} className={isProcessing ? 'animate-spin' : ''} />
                          {cfg.label}
                        </span>
                      </td>

                      {/* Action */}
                      <td className="px-4 py-4 text-center">
                        {tx.status === 'completed' ? (
                          <Link
                            href={`/companies/${companyId}/transactions/${tx.id}`}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[12px] font-bold transition-all"
                            style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', color: '#1A32D8' }}
                            onMouseEnter={e => { e.currentTarget.style.background = 'rgba(26,50,216,0.06)'; e.currentTarget.style.borderColor = 'rgba(26,50,216,0.2)' }}
                            onMouseLeave={e => { e.currentTarget.style.background = '#F8FAFC'; e.currentTarget.style.borderColor = '#E2E8F0' }}
                          >
                            View <ArrowUpRight size={12} />
                          </Link>
                        ) : tx.status === 'failed' ? (
                          <div className="flex items-center justify-center gap-2">
                            <button
                              onClick={() => reuploadRefs.current[tx.id]?.click()}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[12px] font-bold transition-all"
                              style={{ background: 'rgba(220,38,38,0.06)', border: '1px solid rgba(220,38,38,0.2)', color: '#DC2626' }}
                              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(220,38,38,0.12)' }}
                              onMouseLeave={e => { e.currentTarget.style.background = 'rgba(220,38,38,0.06)' }}
                            >
                              <RotateCcw size={12} /> Reupload
                            </button>
                            <button
                              onClick={() => handleCancelFailed(tx.id)}
                              className="inline-flex items-center justify-center p-1.5 rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-all font-bold"
                              title="Cancel"
                            >
                              <X size={16} />
                            </button>
                            <input
                              type="file"
                              accept=".xlsx,.xls,.csv"
                              className="hidden"
                              ref={el => { reuploadRefs.current[tx.id] = el }}
                              onChange={e => {
                                const f = e.target.files[0]
                                if (f) processFileSelection(f, tx.id)
                                e.target.value = ''
                              }}
                            />
                          </div>
                        ) : (
                          <span className="text-[12px] font-semibold text-slate-300">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* ── Sheet Selection Modal ── */}
      {sheetSelectData && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-white rounded-2xl w-full max-w-md p-6 shadow-xl border border-slate-100">
            <h3 className="text-lg font-bold text-slate-800 mb-2">Select Sheet</h3>
            <p className="text-[13px] text-slate-500 mb-6">
              The file <strong className="text-slate-700">{sheetSelectData.file.name}</strong> contains multiple sheets. Please choose the one you want to upload.
            </p>
            <div className="flex flex-col gap-2 max-h-[300px] overflow-y-auto mb-6 pr-2"
              style={{ scrollbarWidth: 'thin', scrollbarColor: '#CBD5E1 transparent' }}>
              {sheetSelectData.sheets.map(sheet => (
                <button
                  key={sheet}
                  onClick={() => {
                    const { file, failedId } = sheetSelectData
                    if (failedId) handleReupload(file, failedId, sheet)
                    else handleUpload(file, sheet)
                    setSheetSelectData(null)
                  }}
                  className="flex items-center justify-between px-4 py-3 rounded-xl border border-slate-200 transition-all hover:border-[#1A32D8] hover:bg-[#1A32D8]/5 group text-left"
                >
                  <span className="text-[14px] font-bold text-slate-700 group-hover:text-[#1A32D8]">{sheet}</span>
                  <ArrowUpRight size={14} className="opacity-0 group-hover:opacity-100 text-[#1A32D8] transition-opacity" />
                </button>
              ))}
            </div>
            <div className="flex justify-end">
              <button
                onClick={() => setSheetSelectData(null)}
                className="px-5 py-2.5 rounded-xl text-[13px] font-bold text-slate-600 hover:bg-slate-100 transition-colors"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
