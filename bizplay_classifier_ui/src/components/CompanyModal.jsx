'use client'

import { useState } from 'react'
import { X, Building2, Loader2 } from 'lucide-react'

export default function CompanyModal({ company, onClose, onSave }) {
  const isEdit = !!company?.id
  const [form, setForm] = useState(company ?? { name: '', businessNumber: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [bnError, setBnError] = useState('')

  const validateBn = (value) => {
    if (!value) return ''
    if (!/^\d{10}$/.test(value)) return 'Business number must be exactly 10 digits.'
    return ''
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!form.name.trim()) return
    const bnErr = validateBn(form.businessNumber)
    if (bnErr) { setBnError(bnErr); return }
    setLoading(true)
    setError('')
    try {
      await onSave(form)
    } catch (err) {
      setError(err.message ?? 'Something went wrong. Please try again.')
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 transition-all duration-300" style={{ background:'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)' }}
      onClick={(e) => e.target===e.currentTarget && !loading && onClose()}>
      <div className="w-full max-w-[460px] rounded-[24px] overflow-hidden animate-fade-in shadow-2xl relative" style={{ background:'#FFFFFF', border:'1px solid #E2E8F0' }}>
        
        {/* Header Area */}
        <div className="flex items-center justify-between px-7 py-6 border-b border-slate-100 bg-slate-50/50">
          <div className="flex items-center gap-4">
            <div className="flex items-center justify-center w-11 h-11 rounded-2xl shadow-sm border" style={{ background: 'rgba(26,50,216,0.05)', borderColor: 'rgba(26,50,216,0.1)' }}>
              <Building2 size={20} style={{ color: '#1A32D8' }} />
            </div>
            <div>
              <h2 className="font-bold text-[17px] tracking-tight text-slate-800 leading-none mb-1">
                {isEdit ? 'Edit Workspace' : 'Create new Workspace'}
              </h2>
              <p className="text-[12px] font-semibold text-slate-500">Register a company to apply classifications</p>
            </div>
          </div>
          <button onClick={onClose} disabled={loading} className="w-8 h-8 -mr-1 -mt-4 flex items-center justify-center rounded-full hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors disabled:opacity-50">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-7 py-7 space-y-6">
          <div className="space-y-2">
            <label className="text-[13px] font-bold text-slate-700 block">Company Name</label>
            <input type="text" required placeholder="e.g. Acme Corporation" value={form.name}
              onChange={(e)=>setForm({...form,name:e.target.value})}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-medium outline-none transition-all placeholder:text-slate-400 bg-white border border-slate-200 shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300 focus:border-[#1A32D8] focus:ring-[4px] focus:ring-[#1A32D8]/10"
            />
          </div>
          
          <div className="space-y-2">
            <div className="flex justify-between items-end">
              <label className="text-[13px] font-bold text-slate-700 block">Business Number</label>
              <span className="text-[12px] font-bold" style={{ color: form.businessNumber.length === 10 ? '#10B981' : '#94A3B8' }}>
                {form.businessNumber.length}/10 digits
              </span>
            </div>
            <input
              type="text"
              inputMode="numeric"
              placeholder="e.g. 1234567890"
              value={form.businessNumber}
              maxLength={10}
              onChange={(e) => {
                const digits = e.target.value.replace(/\D/g, '')
                setForm({ ...form, businessNumber: digits })
                setBnError(validateBn(digits))
              }}
              className="w-full px-4 py-3 rounded-xl text-[14px] font-medium outline-none transition-all placeholder:text-slate-400 bg-white border shadow-[0_1px_2px_rgba(15,23,42,0.03)] hover:border-slate-300 focus:ring-[4px]"
              style={{ borderColor: bnError ? '#EF4444' : '#E2E8F0' }}
              onFocus={(e) => { 
                e.target.style.borderColor = bnError ? '#EF4444' : '#1A32D8'; 
                e.target.style.boxShadow = `0 0 0 4px ${bnError ? 'rgba(239,68,68,0.15)' : 'rgba(26,50,216,0.1)'}` 
              }}
              onBlur={(e) => { 
                e.target.style.borderColor = bnError ? '#EF4444' : '#E2E8F0'; 
                e.target.style.boxShadow = 'none' 
              }}
            />
            {bnError && <p className="text-[12px] font-semibold text-red-500 mt-1">{bnError}</p>}
          </div>

          {error && (
            <div className="px-4 py-3 rounded-xl text-[13px] font-semibold bg-red-50 text-red-600 border border-red-100 flex items-center gap-2">
              <div className="w-1.5 h-1.5 rounded-full bg-red-500" />
              {error}
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} disabled={loading} className="w-[120px] py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-40 transition-colors bg-slate-100 text-slate-600 hover:bg-slate-200">
              Cancel
            </button>
            <button type="submit" disabled={loading} className="flex-1 py-3.5 rounded-xl text-[14px] font-bold cursor-pointer disabled:opacity-60 flex items-center justify-center gap-2 transition-all text-white shadow-lg border border-transparent"
              style={{ background:'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)', border: '1px solid #14249B', color:'#fff', boxShadow: '0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset' }}
              onMouseEnter={(e)=>{if(!loading){e.currentTarget.style.background='linear-gradient(180deg, #1E3AF4 0%, #1A32D8 100%)'; e.currentTarget.style.boxShadow='0 4px 12px rgba(26,50,216,0.25), 0 1px 1px rgba(255,255,255,0.15) inset'}}}
              onMouseLeave={(e)=>{if(!loading){e.currentTarget.style.background='linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)'; e.currentTarget.style.boxShadow='0 4px 10px rgba(26,50,216,0.15), 0 1px 1px rgba(255,255,255,0.15) inset'}}}>
              {loading ? <><Loader2 size={16} className="animate-spin" />Processing…</> : isEdit ? 'Save Changes' : 'Create Workspace'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
