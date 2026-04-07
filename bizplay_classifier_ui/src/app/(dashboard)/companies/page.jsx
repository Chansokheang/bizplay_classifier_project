'use client'

import { useEffect, useState } from 'react'
import { useSession } from 'next-auth/react'
import { Plus, Search, Building2 } from 'lucide-react'
import { COMPANIES as INITIAL } from '../../../lib/mock-data'
import { getAllCompanies, createCompany } from '../../../service/companyService'
import CompanyCard from '../../../components/CompanyCard'
import CompanyModal from '../../../components/CompanyModal'

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
        const token = session?.accessToken ?? null
        const data = await getAllCompanies(token)
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

  const handleSave = async (data) => {
    if (data.id) {
      setCompanies(prev => prev.map(c => c.id === data.id ? { ...c, name: data.name, industry: data.businessNumber } : c))
      setModal(null)
      return
    }

    const token = session?.accessToken ?? null
    const result = await createCompany(
      { companyName: data.name, businessNumber: data.businessNumber },
      token,
    )
    const payload = result?.payload ?? {}
    const created = {
      id: payload.companyId ?? String(Date.now()),
      name: payload.companyName ?? data.name,
      industry: payload.businessNumber ?? data.businessNumber,
      status: 'active',
      categoriesCount: 0,
      rulesCount: 0,
      transactionsCount: 0,
      createdAt: new Date().toISOString().split('T')[0],
    }
    setCompanies(prev => [...prev, created])
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
          onFocus={(e)=>{e.target.style.borderColor='#1A32D8'}}
          onBlur={(e)=>{e.target.style.borderColor='#E2E8F0'}}/>
      </div>

      {loadError && (
        <div className="mb-4 rounded-xl px-4 py-3 text-xs" style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#DC2626' }}>
          {loadError}
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 2xl:grid-cols-4 gap-6">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="rounded-[22px] overflow-hidden min-h-[210px] flex flex-col p-5 gap-4"
              style={{ background: 'rgba(255,255,255,0.5)', backdropFilter: 'blur(16px)', border: '1px solid rgba(255,255,255,0.6)', boxShadow: '0 4px 16px -4px rgba(15,23,42,0.05)' }}>
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl animate-pulse" style={{ background: 'rgba(226,232,240,0.8)' }} />
                <div className="flex-1 space-y-2 pt-1">
                  <div className="h-5 rounded-lg animate-pulse w-3/4" style={{ background: 'rgba(226,232,240,0.8)' }} />
                  <div className="h-3 rounded-lg animate-pulse w-1/2" style={{ background: 'rgba(226,232,240,0.5)' }} />
                </div>
              </div>
              <div className="mt-auto flex items-center justify-between gap-2">
                {[...Array(3)].map((_, j) => (
                  <div key={j} className="h-3 rounded-lg animate-pulse flex-1" style={{ background: 'rgba(226,232,240,0.6)' }} />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-20"><Building2 size={40} color="#E2E8F0" className="mx-auto mb-3"/><p className="text-sm" style={{color:'#CBD5E1'}}>No companies found</p></div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 2xl:grid-cols-4 gap-6">
          <button onClick={()=>setModal('create')}
            className="rounded-[22px] flex flex-col items-center justify-center gap-3 min-h-[210px] cursor-pointer transition-all duration-300"
            style={{
              background: 'rgba(255,255,255,0.3)',
              backdropFilter: 'blur(16px)',
              border:'2px dashed rgba(26,50,216,0.3)',
              boxShadow: '0 4px 16px -4px rgba(15,23,42,0.02)'
            }}
            onMouseEnter={(e)=>{e.currentTarget.style.borderColor='rgba(26,50,216,0.6)';e.currentTarget.style.background='rgba(255,255,255,0.7)';e.currentTarget.style.boxShadow='0 12px 32px -8px rgba(26,50,216,0.12)';e.currentTarget.style.transform='translateY(-2px)'}}
            onMouseLeave={(e)=>{e.currentTarget.style.borderColor='rgba(26,50,216,0.3)';e.currentTarget.style.background='rgba(255,255,255,0.3)';e.currentTarget.style.boxShadow='0 4px 16px -4px rgba(15,23,42,0.02)';e.currentTarget.style.transform='translateY(0)'}}>
            <div className="flex items-center justify-center w-10 h-10 rounded-xl" style={{background:'rgba(26,50,216,0.1)'}}>
              <Plus size={20} color="#1A32D8"/>
            </div>
            <p className="text-sm font-semibold" style={{color:'#1A32D8'}}>Add Company</p>
          </button>
          {filtered.map(c=>(
            <CompanyCard key={c.id} company={c} onEdit={c=>setModal({ ...c, businessNumber: c.industry ?? '' })} onDelete={id=>setCompanies(prev=>prev.filter(c=>c.id!==id))}/>
          ))}
        </div>
      )}

      {modal !== null && (
        <CompanyModal company={modal==='create'?null:modal} onClose={()=>setModal(null)} onSave={handleSave}/>
      )}
    </div>
  )
}
