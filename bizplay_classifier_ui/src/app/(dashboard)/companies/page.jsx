'use client'

import { useEffect, useState } from 'react'
import { useSession } from 'next-auth/react'
import Link from 'next/link'
import { Plus, Search, MoreHorizontal, ArrowRight, X, Check, Pencil, Trash2, Building2 } from 'lucide-react'
import { COMPANIES as INITIAL } from '../../../lib/mock-data'

const INDUSTRIES = ['Manufacturing','Software','Logistics','Retail','Finance','Healthcare','Education','Real Estate','Hospitality','Other']
const IND_COLORS = { Manufacturing:'#64748B', Software:'#64748B', Logistics:'#64748B', Retail:'#64748B', Finance:'#64748B', Healthcare:'#64748B', Education:'#64748B', 'Real Estate':'#64748B', Hospitality:'#64748B', Other:'#64748B' }

function CompanyModal({ company, onClose, onSave }) {
  const isEdit = !!company?.id
  const [form, setForm] = useState(company ?? { name:'', industry:'Software', status:'active' })
  const handleSubmit = (e) => { e.preventDefault(); if (!form.name.trim()) return; onSave({ ...form, id: form.id ?? String(Date.now()) }) }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background:'rgba(15,23,42,0.4)' }}
      onClick={(e) => e.target===e.currentTarget && onClose()}>
      <div className="w-full max-w-md rounded-2xl overflow-hidden animate-fade-in" style={{ background:'#FFFFFF', border:'1px solid #E2E8F0', boxShadow:'0 20px 60px rgba(0,0,0,0.15)' }}>
        <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom:'1px solid #F1F5F9', background:'#F8FAFC' }}>
          <div className="flex items-center gap-2.5">
            <div className="flex items-center justify-center w-8 h-8 rounded-lg" style={{ background:'rgba(139,92,246,0.1)' }}>
              <Building2 size={16} color="#8B5CF6" />
            </div>
            <h2 className="font-semibold text-sm" style={{ color:'#0F172A' }}>{isEdit ? 'Edit Company' : 'New Company'}</h2>
          </div>
          <button onClick={onClose} className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer" style={{ color:'#94A3B8' }}
            onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9';e.currentTarget.style.color='#64748B'}}
            onMouseLeave={(e)=>{e.currentTarget.style.background='transparent';e.currentTarget.style.color='#94A3B8'}}>
            <X size={16} />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{ color:'#64748B' }}>Company Name *</label>
            <input type="text" required placeholder="e.g. Acme Corporation" value={form.name}
              onChange={(e)=>setForm({...form,name:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
              style={{ background:'#F8FAFC', border:'1px solid #E2E8F0', color:'#0F172A' }}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}}
              onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}} />
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{ color:'#64748B' }}>Industry</label>
            <select value={form.industry} onChange={(e)=>setForm({...form,industry:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none cursor-pointer"
              style={{ background:'#F8FAFC', border:'1px solid #E2E8F0', color:'#0F172A' }}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B'}}
              onBlur={(e)=>{e.target.style.borderColor='#E2E8F0'}}>
              {INDUSTRIES.map(i=><option key={i}>{i}</option>)}
            </select>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{ color:'#64748B' }}>Status</label>
            <div className="flex gap-3">
              {['active','inactive'].map(s=>(
                <button key={s} type="button" onClick={()=>setForm({...form,status:s})}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium cursor-pointer flex-1 justify-center"
                  style={form.status===s
                    ? s==='active' ? {background:'rgba(16,185,129,0.1)',color:'#059669',border:'1.5px solid rgba(16,185,129,0.3)'}
                                   : {background:'#F1F5F9',color:'#64748B',border:'1.5px solid #E2E8F0'}
                    : {background:'#F8FAFC',color:'#94A3B8',border:'1px solid #E2E8F0'}}>
                  {form.status===s && <Check size={14}/>}{s.charAt(0).toUpperCase()+s.slice(1)}
                </button>
              ))}
            </div>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer"
              style={{ background:'#F8FAFC', color:'#64748B', border:'1px solid #E2E8F0' }}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}}
              onMouseLeave={(e)=>{e.currentTarget.style.background='#F8FAFC'}}>Cancel</button>
            <button type="submit" className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer"
              style={{ background:'#F59E0B', color:'#fff' }}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#D97706'}}
              onMouseLeave={(e)=>{e.currentTarget.style.background='#F59E0B'}}>
              {isEdit ? 'Save Changes' : 'Create Company'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function CompanyCard({ company, onEdit, onDelete }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [hovered, setHovered] = useState(false)
  const initials = company.name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()
  const tints = ['#1A32D8', '#0F766E', '#9333EA', '#DC2626']
  const tint = tints[(parseInt(company.id, 10) - 1) % tints.length] ?? '#1A32D8'

  return (
    <div
      className="relative rounded-2xl overflow-hidden cursor-pointer"
      style={{
        background: '#FFFFFF',
        border: '1px solid #E2E8F0',
        boxShadow: hovered ? '0 6px 18px rgba(15,23,42,0.08)' : '0 1px 3px rgba(15,23,42,0.04)',
        transition: 'box-shadow 0.2s ease, transform 0.2s ease',
        transform: hovered ? 'translateY(-1px)' : 'translateY(0)',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => { setHovered(false); setMenuOpen(false) }}
      onClick={() => { window.location.href = `/companies/${company.id}/transactions` }}
    >
      <div className="p-4">
        <div className="flex justify-between items-start mb-5">
          <div
            className="flex items-center justify-center w-10 h-10 rounded-lg shrink-0 text-xs font-bold"
            style={{ background: `${tint}14`, border: `1px solid ${tint}29`, color: tint }}
          >
            {initials}
          </div>
          <div className="relative shrink-0" onClick={e => e.stopPropagation()}>
            <button
              onClick={() => setMenuOpen(o => !o)}
              className="flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer"
              style={{ color: '#9CA3AF' }}
              onMouseEnter={e => { e.currentTarget.style.background = '#F8FAFC'; e.currentTarget.style.color = '#4B5563' }}
              onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#9CA3AF' }}
            >
              <MoreHorizontal size={15} />
            </button>
            {menuOpen && (
              <div className="absolute right-0 top-8 z-20 rounded-xl py-1 w-36 shadow-lg" style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>
                <button onClick={() => { onEdit(company); setMenuOpen(false) }}
                  className="flex items-center gap-2 w-full px-3 py-2 text-xs cursor-pointer" style={{ color: '#64748B' }}
                  onMouseEnter={e => { e.currentTarget.style.background = '#F8FAFC'; e.currentTarget.style.color = '#0F172A' }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.color = '#64748B' }}>
                  <Pencil size={13} />Edit
                </button>
                <button onClick={() => { onDelete(company.id); setMenuOpen(false) }}
                  className="flex items-center gap-2 w-full px-3 py-2 text-xs cursor-pointer" style={{ color: '#EF4444' }}
                  onMouseEnter={e => { e.currentTarget.style.background = '#FEF2F2' }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent' }}>
                  <Trash2 size={13} />Delete
                </button>
              </div>
            )}
          </div>
        </div>

        <div className="mb-4">
          <h3 className="text-base font-semibold text-gray-900 tracking-tight truncate">{company.name}</h3>
          <div className="flex items-center gap-2 mt-1">
            <span className="text-xs text-gray-500">ID: {String(company.id).padStart(4, '0')}</span>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-3 pt-3" style={{ borderTop: '1px solid #F1F5F9' }}>
          <div className="flex flex-col">
            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Rules</span>
            <span className="text-sm font-semibold text-gray-900">{company.rulesCount}</span>
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Categories</span>
            <span className="text-sm font-semibold text-gray-900">{company.categoriesCount}</span>
          </div>
          <div className="flex flex-col">
            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Processed</span>
            <span className="text-sm font-semibold text-gray-900">{(company.transactionsCount / 1000).toFixed(1)}k</span>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function CompaniesPage() {
  const { data: session } = useSession()
  const [companies, setCompanies] = useState(INITIAL)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState(null)

  useEffect(() => {
    const fetchCompanies = async () => {
      try {
        setLoading(true)
        setLoadError('')
        const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'
        const token = session?.accessToken ?? null
        const res = await fetch(`${baseUrl}/api/v1/companies/allCompanies`, {
          headers: {
            accept: '*/*',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
        })
        if (!res.ok) {
          throw new Error(`Request failed: ${res.status}`)
        }
        const data = await res.json()
        const mapped = Array.isArray(data?.payload) ? data.payload.map((c) => {
          const rules = c.ruleDTOList ?? []
          const categories = rules.flatMap((r) => r.categoryDTOList ?? [])
          const uniqueCategories = new Set(categories.map((cat) => cat.categoryId))
          return {
            id: c.companyId,
            name: c.companyName ?? 'Untitled Company',
            industry: c.businessNumber ?? '—',
            status: 'active',
            rulesCount: rules.length,
            categoriesCount: uniqueCategories.size,
            transactionsCount: 0,
            createdAt: c.createdDate?.split('T')[0] ?? '',
          }
        }) : []
        const finalCompanies = mapped.length ? mapped : INITIAL
        setCompanies(finalCompanies)
        try {
          const index = Object.fromEntries(finalCompanies.map((c) => [c.id, c.name]))
          window.localStorage.setItem('companiesIndex', JSON.stringify(index))
        } catch {}
      } catch (err) {
        setLoadError('Unable to load companies. Showing cached data.')
        setCompanies(INITIAL)
        try {
          const index = Object.fromEntries(INITIAL.map((c) => [c.id, c.name]))
          window.localStorage.setItem('companiesIndex', JSON.stringify(index))
        } catch {}
      } finally {
        setLoading(false)
      }
    }

    if (session) {
      fetchCompanies()
    }
  }, [session])

  const filtered = companies.filter(c =>
    c.name.toLowerCase().includes(search.toLowerCase()) ||
    c.industry.toLowerCase().includes(search.toLowerCase())
  )

  const handleSave = (data) => {
    setCompanies(prev => {
      const exists = prev.find(c=>c.id===data.id)
      if (exists) return prev.map(c=>c.id===data.id?{...c,...data}:c)
      return [...prev, {...data,categoriesCount:0,rulesCount:0,transactionsCount:0,createdAt:new Date().toISOString().split('T')[0]}]
    })
    setModal(null)
  }

  return (
    <div className="w-full min-h-screen py-8 animate-fade-in" style={{ background: 'linear-gradient(180deg, #F8FAFC 0%, #EEF2FF 45%, #F8FAFC 100%)' }}>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{color:'#0F172A'}}>Companies</h1>
          <p className="mt-1 text-sm" style={{color:'#94A3B8'}}>{companies.length} registered · {companies.filter(c=>c.status==='active').length} active</p>
        </div>
      </div>

        <div className="relative mb-6 max-w-xl">
        <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2" style={{color:'#CBD5E1'}}/>
        <input type="text" placeholder="Search companies..." value={search} onChange={e=>setSearch(e.target.value)}
          className="w-full pl-11 pr-4 py-3 rounded-xl text-sm outline-none"
          style={{background:'#FFFFFF',border:'1px solid #E2E8F0',color:'#0F172A',boxShadow:'0 1px 2px rgba(15,23,42,0.04)'}}
          onFocus={(e)=>{e.target.style.borderColor='#1A32D8';e.target.style.boxShadow='0 0 0 1px rgba(26,50,216,0.25)'}}
          onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.boxShadow='0 1px 2px rgba(15,23,42,0.04)'}}/>
      </div>

      {loadError && (
        <div className="mb-4 rounded-xl px-4 py-3 text-xs" style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#DC2626' }}>
          {loadError}
        </div>
      )}

      {filtered.length === 0 ? (
        <div className="text-center py-20"><Building2 size={40} color="#E2E8F0" className="mx-auto mb-3"/><p className="text-sm" style={{color:'#CBD5E1'}}>No companies found</p></div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4">
          <button onClick={()=>setModal('create')}
            className="rounded-2xl flex flex-col items-center justify-center gap-3 min-h-[140px] cursor-pointer"
            style={{background:'#FFFFFF',border:'2px dashed #1A32D8'}}
            onMouseEnter={(e)=>{e.currentTarget.style.borderColor='#1A32D8';e.currentTarget.style.background='#FFFFFF';e.currentTarget.style.boxShadow='0 6px 18px rgba(15,23,42,0.08)';e.currentTarget.style.transform='translateY(-1px)'}}
            onMouseLeave={(e)=>{e.currentTarget.style.borderColor='#1A32D8';e.currentTarget.style.background='#FFFFFF';e.currentTarget.style.boxShadow='0 1px 3px rgba(15,23,42,0.04)';e.currentTarget.style.transform='translateY(0)'}}>
            <div className="flex items-center justify-center w-10 h-10 rounded-xl" style={{background:'rgba(26,50,216,0.1)'}}>
              <Plus size={20} color="#1A32D8"/>
            </div>
            <p className="text-sm font-semibold" style={{color:'#1A32D8'}}>Add Company</p>
          </button>
          {filtered.map(c=>(
            <CompanyCard key={c.id} company={c} onEdit={c=>setModal(c)} onDelete={id=>setCompanies(prev=>prev.filter(c=>c.id!==id))}/>
          ))}
        </div>
      )}

        {modal !== null && (
          <CompanyModal company={modal==='create'?null:modal} onClose={()=>setModal(null)} onSave={handleSave}/>
        )}
    </div>
  )
}
