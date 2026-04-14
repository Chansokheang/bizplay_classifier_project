'use client'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import {
  Tags, Plus, Search, Pencil, Trash2, X,
  FileSpreadsheet, Loader2, FileUp, ArrowUpRight,
} from 'lucide-react'
import {
  getCategoriesByCompany,
  createCategory,
  updateCategory,
  deleteCategory,
  uploadCategories,
} from '../../../../../service/categoryService'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../../../../components/ui/table'

const COLORS = [
  '#EF4444','#F97316','#F59E0B','#EAB308','#84CC16','#10B981',
  '#14B8A6','#06B6D4','#3B82F6','#6366F1','#8B5CF6','#A855F7',
  '#EC4899','#F43F5E','#64748B','#475569',
]

function getColor(str) {
  let hash = 0
  for (let i = 0; i < (str || '').length; i++) {
    hash = (str.charCodeAt(i) + ((hash << 5) - hash)) | 0
  }
  return COLORS[Math.abs(hash) % COLORS.length]
}

// ─── Create (manual) modal ─────────────────────────────────────────────────
function CreateModal({ onClose, onSave, loading, error }) {
  const [form, setForm] = useState({ code: '', category: '' })
  const [codeError, setCodeError] = useState('')

  const validateCode = (v) => {
    if (!v) return 'Code is required'
    if (!/^[A-Za-z0-9]{5}$/.test(v)) return 'Code must be exactly 5 alphanumeric characters'
    return ''
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    const err = validateCode(form.code)
    if (err) { setCodeError(err); return }
    if (!form.category.trim()) return
    onSave({ code: form.code.toUpperCase(), category: form.category.trim() })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 transition-all duration-300"
      style={{ background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)' }}
      onClick={(e) => e.target === e.currentTarget && !loading && onClose()}>
      <div className="w-full max-w-[460px] rounded-[24px] overflow-hidden animate-fade-in shadow-2xl"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>

        <div className="flex items-center justify-between px-7 py-6 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-4">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl shadow-sm border"
              style={{ background: 'rgba(26,50,216,0.05)', borderColor: 'rgba(26,50,216,0.1)' }}>
              <Tags size={20} style={{ color: '#1A32D8' }} />
            </div>
            <div>
              <h2 className="font-bold text-[17px] tracking-tight text-slate-800 leading-none mb-1">Create Category</h2>
              <p className="text-[12px] font-semibold text-slate-500">Add a new classification category</p>
            </div>
          </div>
          <button onClick={onClose} disabled={loading}
            className="w-8 h-8 -mr-1 -mt-4 flex items-center justify-center rounded-full hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors disabled:opacity-50">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-7 py-7 space-y-6">
          <div className="space-y-2">
            <label className="text-[13px] font-bold text-slate-700 block">
              Code <span className="text-red-500">*</span>
              <span className="ml-1 font-normal text-slate-400">(5 alphanumeric chars)</span>
            </label>
            <input
              type="text"
              required
              maxLength={5}
              placeholder="e.g. TRV01"
              value={form.code}
              onChange={(e) => { setForm({ ...form, code: e.target.value }); setCodeError(validateCode(e.target.value)) }}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-mono tracking-widest font-medium outline-none transition-all placeholder:text-slate-400 bg-white border shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300"
              style={{ borderColor: codeError ? '#EF4444' : '#CBD5E1' }}
              onFocus={(e) => { e.target.style.borderColor = codeError ? '#EF4444' : '#1A32D8'; e.target.style.boxShadow = `0 0 0 4px ${codeError ? 'rgba(239,68,68,0.1)' : 'rgba(26,50,216,0.1)'}` }}
              onBlur={(e) => { e.target.style.borderColor = codeError ? '#EF4444' : '#CBD5E1'; e.target.style.boxShadow = 'none' }}
            />
            {codeError && <p className="text-[12px] font-semibold text-red-500">{codeError}</p>}
          </div>

          <div className="space-y-2">
            <label className="text-[13px] font-bold text-slate-700 block">
              Category Name <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Travel & Transport"
              value={form.category}
              onChange={(e) => setForm({ ...form, category: e.target.value })}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-medium outline-none transition-all placeholder:text-slate-400 bg-white border border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300"
              onFocus={(e) => { e.target.style.borderColor = '#1A32D8'; e.target.style.boxShadow = '0 0 0 4px rgba(26,50,216,0.1)' }}
              onBlur={(e) => { e.target.style.borderColor = '#CBD5E1'; e.target.style.boxShadow = 'none' }}
            />
          </div>

          {error && (
            <div role="alert" className="px-4 py-3 rounded-xl text-[13px] bg-red-50 text-red-600 border border-red-100 flex items-start gap-2.5">
              <div className="w-1.5 h-1.5 rounded-full bg-red-500 shrink-0 mt-1.5" aria-hidden />
              <span className="min-w-0 flex-1 font-semibold leading-snug break-words">{error}</span>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} disabled={loading}
              className="w-[120px] py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-40 transition-colors bg-slate-100 text-slate-600 hover:bg-slate-200">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-60 flex items-center justify-center gap-2 transition-all text-white"
              style={{ background: 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)', border: '1px solid #14249B', boxShadow: '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' }}
              onMouseEnter={(e) => { if (!loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1E3AF4 0%, #1A32D8 100%)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(26,50,216,0.25), 0 1px 1px rgba(255,255,255,0.15) inset' } }}
              onMouseLeave={(e) => { if (!loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)'; e.currentTarget.style.boxShadow = '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' } }}>
              {loading ? <><Loader2 size={16} className="animate-spin" />Processing…</> : 'Create Category'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Edit modal ────────────────────────────────────────────────────────────
function EditModal({ category, onClose, onSave, loading, error }) {
  const [form, setForm] = useState({ ...category })
  const [codeError, setCodeError] = useState('')

  const validateCode = (v) => {
    if (!v) return 'Code is required'
    if (!/^[A-Za-z0-9]{5}$/.test(v)) return 'Code must be exactly 5 alphanumeric characters'
    return ''
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!form.category?.trim() && !form.name?.trim()) return
    const err = validateCode(form.code)
    if (err) { setCodeError(err); return }
    onSave(form)
  }

  const displayName = form.category ?? form.name ?? ''

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 transition-all duration-300"
      style={{ background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)' }}
      onClick={(e) => e.target === e.currentTarget && !loading && onClose()}>
      <div className="w-full max-w-[460px] rounded-[24px] overflow-hidden animate-fade-in shadow-2xl"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>

        <div className="flex items-center justify-between px-7 py-6 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-4">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl shadow-sm border"
              style={{ background: 'rgba(26,50,216,0.05)', borderColor: 'rgba(26,50,216,0.1)' }}>
              <Tags size={20} style={{ color: '#1A32D8' }} />
            </div>
            <div>
              <h2 className="font-bold text-[17px] tracking-tight text-slate-800 leading-none mb-1">Edit Category</h2>
              <p className="text-[12px] font-semibold text-slate-500">Update the classification category</p>
            </div>
          </div>
          <button onClick={onClose} disabled={loading}
            className="w-8 h-8 -mr-1 -mt-4 flex items-center justify-center rounded-full hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors disabled:opacity-50">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-7 py-7 space-y-6">
          <div className="space-y-2">
            <label className="text-[13px] font-bold text-slate-700 block">
              Code <span className="text-red-500">*</span>
              <span className="ml-1 font-normal text-slate-400">(5 alphanumeric chars)</span>
            </label>
            <input
              type="text"
              required
              maxLength={5}
              placeholder="e.g. TRV01"
              value={form.code ?? ''}
              onChange={(e) => { setForm({ ...form, code: e.target.value }); setCodeError(validateCode(e.target.value)) }}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-mono tracking-widest font-medium outline-none transition-all placeholder:text-slate-400 bg-white border shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300"
              style={{ borderColor: codeError ? '#EF4444' : '#CBD5E1' }}
              onFocus={(e) => { e.target.style.borderColor = codeError ? '#EF4444' : '#1A32D8'; e.target.style.boxShadow = `0 0 0 4px ${codeError ? 'rgba(239,68,68,0.1)' : 'rgba(26,50,216,0.1)'}` }}
              onBlur={(e) => { e.target.style.borderColor = codeError ? '#EF4444' : '#CBD5E1'; e.target.style.boxShadow = 'none' }}
            />
            {codeError && <p className="text-[12px] font-semibold text-red-500">{codeError}</p>}
          </div>
          <div className="space-y-2">
            <label className="text-[13px] font-bold text-slate-700 block">Category Name <span className="text-red-500">*</span></label>
            <input type="text" required value={displayName}
              onChange={(e) => setForm({ ...form, category: e.target.value, name: e.target.value })}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-medium outline-none transition-all placeholder:text-slate-400 bg-white border border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300"
              onFocus={(e) => { e.target.style.borderColor = '#1A32D8'; e.target.style.boxShadow = '0 0 0 4px rgba(26,50,216,0.1)' }}
              onBlur={(e) => { e.target.style.borderColor = '#CBD5E1'; e.target.style.boxShadow = 'none' }}
            />
          </div>

          {error && (
            <div role="alert" className="px-4 py-3 rounded-xl text-[13px] bg-red-50 text-red-600 border border-red-100 flex items-start gap-2.5">
              <div className="w-1.5 h-1.5 rounded-full bg-red-500 shrink-0 mt-1.5" aria-hidden />
              <span className="min-w-0 flex-1 font-semibold leading-snug break-words">{error}</span>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} disabled={loading}
              className="w-[120px] py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-40 transition-colors bg-slate-100 text-slate-600 hover:bg-slate-200">
              Cancel
            </button>
            <button type="submit" disabled={loading}
              className="flex-1 py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-60 flex items-center justify-center gap-2 transition-all text-white"
              style={{ background: 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)', border: '1px solid #14249B', boxShadow: '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' }}
              onMouseEnter={(e) => { if (!loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1E3AF4 0%, #1A32D8 100%)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(26,50,216,0.25), 0 1px 1px rgba(255,255,255,0.15) inset' } }}
              onMouseLeave={(e) => { if (!loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)'; e.currentTarget.style.boxShadow = '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' } }}>
              {loading ? <><Loader2 size={16} className="animate-spin" />Processing…</> : 'Save Changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Upload modal ──────────────────────────────────────────────────────────
function UploadModal({ onClose, onUpload, loading, error }) {
  const [file, setFile] = useState(null)
  const [sheets, setSheets] = useState([]) // populated when file has multiple sheets
  const [isDragging, setIsDragging] = useState(false)
  const fileRef = useRef(null)

  const processFile = async (f) => {
    setFile(f)
    setSheets([])

    if (f.name.endsWith('.xlsx') || f.name.endsWith('.xls')) {
      try {
        const XLSX = await import('xlsx')
        const reader = new FileReader()
        reader.onload = (e) => {
          try {
            const data = new Uint8Array(e.target.result)
            const wb = XLSX.read(data, { type: 'array' })
            if (wb.SheetNames.length > 1) setSheets(wb.SheetNames)
          } catch { /* proceed without sheet selection */ }
        }
        reader.readAsArrayBuffer(f)
      } catch { /* xlsx not available */ }
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setIsDragging(false)
    const f = e.dataTransfer.files[0]
    if (f) processFile(f)
  }

  const handleFileInput = (e) => {
    const f = e.target.files[0]
    if (f) processFile(f)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 transition-all duration-300"
      style={{ background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)' }}
      onClick={(e) => e.target === e.currentTarget && !loading && onClose()}>
      <div className="w-full max-w-[460px] rounded-[24px] overflow-hidden animate-fade-in shadow-2xl"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>

        <div className="flex items-center justify-between px-7 py-6 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-4">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl shadow-sm border"
              style={{ background: 'rgba(26,50,216,0.05)', borderColor: 'rgba(26,50,216,0.1)' }}>
              <FileSpreadsheet size={20} style={{ color: '#1A32D8' }} />
            </div>
            <div>
              <h2 className="font-bold text-[17px] tracking-tight text-slate-800 leading-none mb-1">Upload from Excel</h2>
              <p className="text-[12px] font-semibold text-slate-500">Import categories from .xlsx or .xls</p>
            </div>
          </div>
          <button onClick={onClose} disabled={loading}
            className="w-8 h-8 -mr-1 -mt-4 flex items-center justify-center rounded-full hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors disabled:opacity-50">
            <X size={18} />
          </button>
        </div>

        <div className="px-7 py-7 space-y-5">
          {/* Drop zone */}
          <div
            onClick={() => fileRef.current?.click()}
            onDragOver={(e) => { e.preventDefault(); setIsDragging(true) }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            className="rounded-xl p-6 flex flex-col items-center gap-3 cursor-pointer transition-all"
            style={{
              border: `2px dashed ${isDragging ? '#1A32D8' : file ? '#10B981' : '#CBD5E1'}`,
              background: isDragging ? 'rgba(26,50,216,0.03)' : file ? 'rgba(16,185,129,0.04)' : '#F8FAFC',
            }}>
            {file ? (
              <>
                <div className="flex items-center justify-center w-12 h-12 rounded-2xl"
                  style={{ background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.15)' }}>
                  <FileSpreadsheet size={22} color="#10B981" />
                </div>
                <div className="text-center">
                  <p className="text-[14px] font-semibold text-slate-800">{file.name}</p>
                  <p className="text-[12px] font-semibold text-slate-400 mt-0.5">
                    {(file.size / 1024).toFixed(1)} KB · Click to change
                  </p>
                </div>
              </>
            ) : (
              <>
                <div className="flex items-center justify-center w-12 h-12 rounded-2xl"
                  style={{ background: 'rgba(26,50,216,0.06)', border: '1px solid rgba(26,50,216,0.1)' }}>
                  <FileUp size={22} color="#1A32D8" />
                </div>
                <div className="text-center">
                  <p className="text-[14px] font-semibold text-slate-800">Drop your Excel file here</p>
                  <p className="text-[12px] font-semibold text-slate-400 mt-0.5">or click to browse · .xlsx, .xls</p>
                </div>
              </>
            )}
          </div>
          <input ref={fileRef} type="file" accept=".xlsx,.xls" className="hidden" onChange={handleFileInput} />

          {/* Inline sheet picker — shown inside the same modal */}
          {sheets.length > 0 && (
            <div className="space-y-2">
              <p className="text-[13px] text-slate-500">
                The file <strong className="text-slate-700">{file.name}</strong> contains multiple sheets. Choose one to upload.
              </p>
              <div className="flex flex-col gap-2 max-h-[200px] overflow-y-auto pr-1"
                style={{ scrollbarWidth: 'thin', scrollbarColor: '#CBD5E1 transparent' }}>
                {sheets.map(sheet => (
                  <button
                    key={sheet}
                    onClick={() => onUpload(file, sheet)}
                    disabled={loading}
                    className="flex items-center justify-between px-4 py-3 rounded-xl border border-slate-200 transition-all hover:border-[#1A32D8] hover:bg-[#1A32D8]/5 group text-left disabled:opacity-50">
                    <span className="text-[14px] font-bold text-slate-700 group-hover:text-[#1A32D8]">{sheet}</span>
                    <ArrowUpRight size={14} className="opacity-0 group-hover:opacity-100 text-[#1A32D8] transition-opacity" />
                  </button>
                ))}
              </div>
            </div>
          )}

          {error && (
            <div role="alert" className="px-4 py-3 rounded-xl text-[13px] bg-red-50 text-red-600 border border-red-100 flex items-start gap-2.5">
              <div className="w-1.5 h-1.5 rounded-full bg-red-500 shrink-0 mt-1.5" aria-hidden />
              <span className="min-w-0 flex-1 font-semibold leading-snug break-words">{error}</span>
            </div>
          )}

          {/* Bottom buttons — hide Upload when sheet picker is shown */}
          <div className="flex gap-3 pt-2">
            <button onClick={onClose} disabled={loading}
              className="w-[120px] py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-40 transition-colors bg-slate-100 text-slate-600 hover:bg-slate-200">
              Cancel
            </button>
            {sheets.length === 0 && (
              <button onClick={() => file && onUpload(file, null)} disabled={!file || loading}
                className="flex-1 py-3.5 rounded-xl text-[14px] font-bold cursor-pointer flex items-center justify-center gap-2 transition-all text-white"
                style={{
                  background: (!file || loading) ? '#CBD5E1' : 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)',
                  border: (!file || loading) ? '1px solid #CBD5E1' : '1px solid #14249B',
                  boxShadow: (!file || loading) ? 'none' : '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset',
                  cursor: (!file || loading) ? 'not-allowed' : 'pointer',
                }}
                onMouseEnter={(e) => { if (file && !loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1E3AF4 0%, #1A32D8 100%)'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(26,50,216,0.25), 0 1px 1px rgba(255,255,255,0.15) inset' } }}
                onMouseLeave={(e) => { if (file && !loading) { e.currentTarget.style.background = 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)'; e.currentTarget.style.boxShadow = '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' } }}>
                {loading ? <><Loader2 size={16} className="animate-spin" />Uploading…</> : 'Upload'}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Create method picker ──────────────────────────────────────────────────
function MethodPicker({ onChoose, onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 transition-all duration-300"
      style={{ background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)' }}
      onClick={(e) => e.target === e.currentTarget && onClose()}>
      <div className="w-full max-w-sm rounded-[24px] overflow-hidden animate-fade-in shadow-2xl"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>
        <div className="flex items-center justify-between px-7 py-6 border-b border-slate-100 bg-slate-50/50">
          <h2 className="font-bold text-[17px] tracking-tight text-slate-800">Add Categories</h2>
          <button onClick={onClose}
            className="w-8 h-8 -mr-1 -mt-4 flex items-center justify-center rounded-full hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors">
            <X size={18} />
          </button>
        </div>
        <div className="px-7 py-6 grid grid-cols-2 gap-3">
          <button onClick={() => onChoose('create')}
            className="flex flex-col items-center gap-3 p-5 rounded-2xl cursor-pointer transition-all border border-slate-200 bg-slate-50 hover:bg-blue-50/50 hover:border-blue-200">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl"
              style={{ background: 'rgba(26,50,216,0.08)', border: '1px solid rgba(26,50,216,0.1)' }}>
              <Tags size={20} color="#1A32D8" />
            </div>
            <div>
              <p className="text-[13px] font-bold text-center text-slate-800">Create manually</p>
              <p className="text-[12px] font-semibold text-center mt-0.5 text-slate-400">Fill in a form</p>
            </div>
          </button>

          <button onClick={() => onChoose('upload')}
            className="flex flex-col items-center gap-3 p-5 rounded-2xl cursor-pointer transition-all border border-slate-200 bg-slate-50 hover:bg-emerald-50/50 hover:border-emerald-200">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl"
              style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.15)' }}>
              <FileSpreadsheet size={20} color="#10B981" />
            </div>
            <div>
              <p className="text-[13px] font-bold text-center text-slate-800">Upload Excel</p>
              <p className="text-[12px] font-semibold text-center mt-0.5 text-slate-400">Import from .xlsx</p>
            </div>
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Page ──────────────────────────────────────────────────────────────────
export default function CategoriesPage() {
  const { id: companyId } = useParams()
  const { data: session } = useSession()
  const token = session?.accessToken

  const [categories, setCategories] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')

  // modal state: null | 'picker' | 'create' | 'upload' | {id,...} (edit)
  const [modal, setModal] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  // ── fetch ──
  const fetchCategories = useCallback(async () => {
    if (!token) return
    try {
      const res = await getCategoriesByCompany(companyId, token)
      const payload = Array.isArray(res?.payload) ? res.payload : []
      // normalise API shape → internal shape
      const mapped = payload.map((c) => ({
        id: c.id ?? c.categoryId,
        code: c.code ?? '',
        name: c.category ?? c.name ?? '',
        category: c.category ?? c.name ?? '',
        rulesCount: c.rulesCount ?? 0,
        isUsed: c.isUsed ?? false,
      }))
      setCategories(mapped)
    } catch (e) {
      console.error('Failed to fetch categories:', e)
    } finally {
      setLoading(false)
    }
  }, [companyId, token])

  useEffect(() => { fetchCategories() }, [fetchCategories])

  const filtered = categories.filter(
    (c) => c.name.toLowerCase().includes(search.toLowerCase()) ||
      c.code?.toLowerCase().includes(search.toLowerCase())
  )

  // ── create (manual) ──
  const handleCreate = async ({ code, category }) => {
    setActionLoading(true)
    setActionError('')
    try {
      await createCategory({ companyId, code, category }, token)
      await fetchCategories()
      setModal(null)
    } catch (e) {
      setActionError(e.message || 'Failed to create category')
    } finally {
      setActionLoading(false)
    }
  }

  // ── edit ──
  const handleEdit = async (data) => {
    setActionLoading(true)
    setActionError('')
    try {
      await updateCategory(data.id, { code: data.code, category: data.category ?? data.name }, token)
      await fetchCategories()
      setModal(null)
    } catch (e) {
      setActionError(e.message || 'Failed to update category')
    } finally {
      setActionLoading(false)
    }
  }

  // ── upload ──
  const handleUpload = async (file, sheetName) => {
    setActionLoading(true)
    setActionError('')
    try {
      await uploadCategories(file, companyId, token, sheetName)
      await fetchCategories()
      setModal(null)
    } catch (e) {
      setActionError(e.message || 'Upload failed. Please try again.')
    } finally {
      setActionLoading(false)
    }
  }

  // ── delete ──
  const handleDelete = async (cat) => {
    setDeleteLoading(true)
    try {
      await deleteCategory(cat.id, token)
      await fetchCategories()
      setConfirmDelete(null)
    } catch (e) {
      console.error('Delete failed:', e)
    } finally {
      setDeleteLoading(false)
    }
  }

  const openModal = (type) => {
    setActionError('')
    setModal(type)
  }

  return (
    <div className="py-8 animate-fade-in">

      {/* Header */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Categories</h1>
          <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
            {categories.length} categories · {categories.reduce((s, c) => s + (c.rulesCount ?? 0), 0)} total rules
          </p>
        </div>
        <button
          onClick={() => openModal('picker')}
          className="flex items-center gap-2 pl-2 pr-4 py-2 rounded-xl text-sm font-semibold cursor-pointer"
          style={{ background: '#1A32D8', color: '#fff', boxShadow: '0 4px 12px rgba(26,50,216,0.35)' }}
          onMouseEnter={(e) => { e.currentTarget.style.background = '#1529AB'; e.currentTarget.style.boxShadow = '0 4px 16px rgba(26,50,216,0.5)' }}
          onMouseLeave={(e) => { e.currentTarget.style.background = '#1A32D8'; e.currentTarget.style.boxShadow = '0 4px 12px rgba(26,50,216,0.35)' }}>
          {/* mini logo */}
          <div className="flex items-center justify-center w-7 h-7 rounded-lg bg-white/25 font-bold text-sm select-none">
            <Plus size={15} strokeWidth={3} />
          </div>
          New Category
        </button>
      </div>

      {/* Category pills */}
      {categories.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-6">
          {categories.slice(0, 6).map(cat => (
            <span key={cat.id} className="flex items-center gap-1.5 pl-1.5 pr-3 py-1.5 rounded-full text-xs font-medium"
              style={{ background: '#F8FAFC', color: '#475569', border: '1px solid #E2E8F0' }}>
              <span className="flex items-center justify-center w-4 h-4 rounded-full text-white font-bold"
                style={{ background: getColor(cat.code || cat.name), fontSize: '9px' }}>
                {cat.name.charAt(0).toUpperCase()}
              </span>
              {cat.name}
            </span>
          ))}
          {categories.length > 6 && (
            <span className="px-3 py-1.5 rounded-full text-xs font-medium"
              style={{ background: '#F8FAFC', color: '#94A3B8', border: '1px solid #E2E8F0' }}>
              +{categories.length - 6} more
            </span>
          )}
        </div>
      )}

      {/* Search */}
      <div className="relative mb-5 max-w-sm">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2" style={{ color: '#CBD5E1' }} />
        <input
          type="text"
          placeholder="Search categories or codes..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full pl-10 pr-4 py-2.5 rounded-xl text-sm outline-none"
          style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', color: '#0F172A' }}
          onFocus={(e) => { e.target.style.borderColor = '#1A32D8' }}
          onBlur={(e) => { e.target.style.borderColor = '#E2E8F0' }}
        />
      </div>

      {/* Table */}
      <div className="rounded-2xl overflow-hidden"
        style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }}>
        {loading ? (
          <div className="flex items-center justify-center py-16 gap-2" style={{ color: '#CBD5E1' }}>
            <Loader2 size={20} className="animate-spin" />
            <span className="text-sm">Loading categories…</span>
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-16">
            <Tags size={32} color="#E2E8F0" className="mx-auto mb-3" />
            <p className="text-sm" style={{ color: '#CBD5E1' }}>
              {search ? 'No categories match your search' : 'No categories yet'}
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table className="table-fixed">
              <colgroup>
                <col style={{ width: '50px' }} />
                <col style={{ width: '110px' }} />
                <col />
                <col style={{ width: '140px' }} />
                <col style={{ width: '88px' }} />
              </colgroup>
              <TableHeader>
                <TableRow className="bg-[#F8FAFC] hover:bg-[#F8FAFC]">
                  <TableHead className="text-center w-12">#</TableHead>
                  <TableHead>Code</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead className="text-right">Situation</TableHead>
                  <TableHead className="text-right w-20 p-0 pr-2" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((cat, idx) => (
                  <TableRow key={cat.id} className="group">
                    <TableCell className="text-center whitespace-nowrap">
                      <span className="text-[12px] font-semibold tabular-nums" style={{ color: '#64748B' }}>{idx + 1}</span>
                    </TableCell>
                    <TableCell className="whitespace-nowrap">
                      <span className="font-mono text-xs px-2 py-0.5 rounded-md font-semibold"
                        style={{ background: '#F1F5F9', color: '#475569' }}>
                        {cat.code || '—'}
                      </span>
                    </TableCell>
                    <TableCell className="whitespace-normal">
                      <div className="flex items-center gap-3">
                        <div className="flex items-center justify-center w-8 h-8 rounded-xl flex-shrink-0 text-white font-bold text-sm select-none"
                          style={{ background: getColor(cat.code || cat.name) }}>
                          {cat.name.charAt(0).toUpperCase()}
                        </div>
                        <span className="font-semibold text-sm" style={{ color: '#0F172A' }}>{cat.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap">
                      {cat.isUsed ? (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold"
                          style={{ background: 'rgba(16,185,129,0.1)', color: '#059669', border: '1px solid rgba(16,185,129,0.2)' }}>
                          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
                          In Use
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold"
                          style={{ background: 'rgba(148,163,184,0.1)', color: '#94A3B8', border: '1px solid rgba(148,163,184,0.2)' }}>
                          <span className="w-1.5 h-1.5 rounded-full bg-slate-300" />
                          Unused
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right whitespace-nowrap py-3.5">
                      <div className="flex items-center justify-end gap-1">
                        <button type="button" onClick={() => { setActionError(''); setModal(cat) }}
                          className="flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer"
                          style={{ color: '#CBD5E1' }}
                          onMouseEnter={(e) => { e.currentTarget.style.background = '#F1F5F9'; e.currentTarget.style.color = '#64748B' }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#CBD5E1' }}>
                          <Pencil size={13} />
                        </button>
                        <button type="button" onClick={() => setConfirmDelete(cat)}
                          className="flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer"
                          style={{ color: '#CBD5E1' }}
                          onMouseEnter={(e) => { e.currentTarget.style.background = '#FEF2F2'; e.currentTarget.style.color = '#EF4444' }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#CBD5E1' }}>
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* ── Modals ── */}
      {modal === 'picker' && (
        <MethodPicker onClose={() => setModal(null)} onChoose={(type) => openModal(type)} />
      )}
      {modal === 'create' && (
        <CreateModal onClose={() => setModal(null)} onSave={handleCreate} loading={actionLoading} error={actionError} />
      )}
      {modal === 'upload' && (
        <UploadModal onClose={() => setModal(null)} onUpload={handleUpload} loading={actionLoading} error={actionError} />
      )}
      {modal && modal !== 'picker' && modal !== 'create' && modal !== 'upload' && (
        <EditModal category={modal} onClose={() => setModal(null)} onSave={handleEdit} loading={actionLoading} error={actionError} />
      )}

      {/* Delete confirm */}
      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
          style={{ background: 'rgba(15,23,42,0.45)' }}
          onClick={(e) => e.target === e.currentTarget && setConfirmDelete(null)}>
          <div className="w-full max-w-sm rounded-2xl p-6 animate-fade-in"
            style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 20px 60px rgba(0,0,0,0.15)' }}>
            <div className="flex items-center justify-center w-12 h-12 rounded-xl mx-auto mb-4"
              style={{ background: 'rgba(239,68,68,0.1)' }}>
              <Trash2 size={22} color="#EF4444" />
            </div>
            <h2 className="font-bold text-center mb-2" style={{ color: '#0F172A' }}>Delete Category</h2>
            <p className="text-sm text-center mb-6" style={{ color: '#64748B' }}>
              Delete <strong style={{ color: '#0F172A' }}>{confirmDelete.name}</strong>? This will also remove its rules.
            </p>
            <div className="flex gap-3">
              <button onClick={() => setConfirmDelete(null)} className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer"
                style={{ background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0' }}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#F1F5F9' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#F8FAFC' }}>
                Cancel
              </button>
              <button onClick={() => handleDelete(confirmDelete)} disabled={deleteLoading}
                className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer flex items-center justify-center gap-2"
                style={{ background: deleteLoading ? '#FCA5A5' : '#EF4444', color: '#fff' }}
                onMouseEnter={(e) => { if (!deleteLoading) e.currentTarget.style.background = '#DC2626' }}
                onMouseLeave={(e) => { if (!deleteLoading) e.currentTarget.style.background = '#EF4444' }}>
                {deleteLoading && <Loader2 size={14} className="animate-spin" />}
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
