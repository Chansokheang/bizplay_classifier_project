'use client'
import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { ListFilter, Plus, Search, Pencil, Trash2, X, ChevronRight, MoreHorizontal, ToggleLeft, ToggleRight, AlertCircle, CheckCircle2 } from 'lucide-react'
import { COMPANIES, CATEGORIES, RULES as INIT_RULES } from '../../../../../lib/mock-data'
import {
  Table, TableBody, TableCell, TableHead,
  TableHeader, TableRow,
} from '../../../../../components/ui/table'

const CONDITION_TYPES = [
  { value:'contains',     label:'Contains',     desc:'Field contains any keyword' },
  { value:'starts_with',  label:'Starts With',  desc:'Field starts with keyword' },
  { value:'ends_with',    label:'Ends With',    desc:'Field ends with keyword' },
  { value:'regex',        label:'Regex',        desc:'Pattern match (regular expression)' },
  { value:'amount_range', label:'Amount Range', desc:'Transaction amount condition' },
]
const COND_BADGE = {
  contains:     { label:'Contains',     color:'#475569', bg:'#F1F5F9' },
  starts_with:  { label:'Starts With',  color:'#475569', bg:'#F1F5F9' },
  ends_with:    { label:'Ends With',    color:'#475569', bg:'#F1F5F9' },
  regex:        { label:'Regex',        color:'#475569', bg:'#F1F5F9' },
  amount_range: { label:'Amount Range', color:'#475569', bg:'#F1F5F9' },
}
const card = { background:'#FFFFFF', border:'1px solid #E2E8F0', boxShadow:'0 1px 3px rgba(0,0,0,0.06)' }

function RuleModal({ rule, categories, onClose, onSave }) {
  const isEdit = !!rule?.id
  const [form, setForm] = useState(rule ?? { name:'', conditionType:'contains', pattern:'', categoryId:categories[0]?.id??'', priority:1, status:'active' })
  const selCat = categories.find(c=>c.id===form.categoryId)
  const handleSubmit = (e) => { e.preventDefault(); if (!form.name.trim()||!form.pattern.trim()) return; onSave({...form,id:form.id??String(Date.now()),categoryName:selCat?.name??''}) }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{background:'rgba(15,23,42,0.4)'}} onClick={(e)=>e.target===e.currentTarget&&onClose()}>
      <div className="w-full max-w-lg rounded-2xl overflow-hidden animate-fade-in" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 20px 60px rgba(0,0,0,0.15)'}}>
        <div className="flex items-center justify-between px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
          <div className="flex items-center gap-2.5">
            <div className="flex items-center justify-center w-8 h-8 rounded-lg" style={{background:'rgba(245,158,11,0.1)'}}><ListFilter size={16} color="#F59E0B"/></div>
            <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>{isEdit?'Edit Rule':'New Rule'}</h2>
          </div>
          <button onClick={onClose} className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer" style={{color:'#94A3B8'}}
            onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}} onMouseLeave={(e)=>{e.currentTarget.style.background='transparent'}}><X size={16}/></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Rule Name *</label>
            <input type="text" required placeholder="e.g. Airline Booking" value={form.name} onChange={(e)=>setForm({...form,name:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold" style={{color:'#64748B'}}>Condition Type</label>
              <select value={form.conditionType} onChange={(e)=>setForm({...form,conditionType:e.target.value})}
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none cursor-pointer" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
                onFocus={(e)=>{e.target.style.borderColor='#F59E0B'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0'}}>
                {CONDITION_TYPES.map(ct=><option key={ct.value} value={ct.value}>{ct.label}</option>)}
              </select>
            </div>
            <div className="space-y-1.5">
              <label className="text-xs font-semibold" style={{color:'#64748B'}}>Priority</label>
              <input type="number" min={0} max={99} value={form.priority} onChange={(e)=>setForm({...form,priority:parseInt(e.target.value)||0})}
                className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
                onFocus={(e)=>{e.target.style.borderColor='#F59E0B'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0'}}/>
            </div>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Pattern / Keywords *<span className="ml-2 font-normal" style={{color:'#94A3B8'}}>{CONDITION_TYPES.find(ct=>ct.value===form.conditionType)?.desc}</span></label>
            <input type="text" required placeholder={form.conditionType==='regex'?'(RESTAURANT|CAFE)':form.conditionType==='amount_range'?'>= 5000':'AIRLINE, AIR ASIA'} value={form.pattern} onChange={(e)=>setForm({...form,pattern:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none font-mono" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Target Category</label>
            <div className="grid grid-cols-2 gap-2 max-h-32 overflow-y-auto">
              {categories.map(cat=>(
                <button key={cat.id} type="button" onClick={()=>setForm({...form,categoryId:cat.id})}
                  className="flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-medium cursor-pointer text-left"
                  style={form.categoryId===cat.id ? {background:`${cat.color}12`,color:cat.color,border:`1.5px solid ${cat.color}30`} : {background:'#F8FAFC',color:'#64748B',border:'1px solid #E2E8F0'}}>
                  <span className="w-2 h-2 rounded-full shrink-0" style={{background:cat.color}}/><span className="truncate">{cat.name}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="flex items-center justify-between px-4 py-3 rounded-xl" style={{background:'#F8FAFC',border:'1px solid #E2E8F0'}}>
            <div>
              <p className="text-sm font-medium" style={{color:'#0F172A'}}>Rule Status</p>
              <p className="text-xs" style={{color:'#94A3B8'}}>{form.status==='active'?'Enabled and applied during classification':'Disabled and will be skipped'}</p>
            </div>
            <button type="button" onClick={()=>setForm({...form,status:form.status==='active'?'inactive':'active'})} className="cursor-pointer" style={{color:form.status==='active'?'#10B981':'#CBD5E1'}}>
              {form.status==='active'?<ToggleRight size={30}/>:<ToggleLeft size={30}/>}
            </button>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer" style={{background:'#F8FAFC',color:'#64748B',border:'1px solid #E2E8F0'}}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F8FAFC'}}>Cancel</button>
            <button type="submit" className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer" style={{background:'#F59E0B',color:'#fff'}}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#D97706'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F59E0B'}}>
              {isEdit?'Save Changes':'Create Rule'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function RulesPage() {
  const { id: companyId } = useParams()
  const company = COMPANIES.find(c=>c.id===companyId)
  const baseCategories = CATEGORIES.filter(c=>c.companyId===companyId)
  const categories = baseCategories.length ? baseCategories : CATEGORIES.map(c=>({ ...c, companyId }))
  const baseRules = INIT_RULES.filter(r=>r.companyId===companyId)
  const [rules, setRules] = useState(baseRules.length ? baseRules : INIT_RULES.map(r=>({ ...r, companyId })))
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('all')
  const [modal, setModal] = useState(null)
  const [menuOpen, setMenuOpen] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)

  const filtered = rules.filter(r=>{
    const ms = r.name.toLowerCase().includes(search.toLowerCase())||r.pattern.toLowerCase().includes(search.toLowerCase())||r.categoryName.toLowerCase().includes(search.toLowerCase())
    const mf = filterStatus==='all'||r.status===filterStatus
    return ms&&mf
  })

  const handleSave = (data) => { setRules(prev=>{ const e=prev.find(r=>r.id===data.id); return e?prev.map(r=>r.id===data.id?{...r,...data}:r):[...prev,{...data,companyId}] }); setModal(null) }
  const handleDelete = (id) => { setRules(prev=>prev.filter(r=>r.id!==id)); setConfirmDelete(null) }
  const toggleStatus = (id) => setRules(prev=>prev.map(r=>r.id===id?{...r,status:r.status==='active'?'inactive':'active'}:r))

  return (
    <div className="py-8 animate-fade-in" onClick={()=>setMenuOpen(null)}>
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{color:'#0F172A'}}>Classification Rules</h1>
          <p className="mt-1 text-sm" style={{color:'#94A3B8'}}>{rules.length} rules · {rules.filter(r=>r.status==='active').length} active</p>
        </div>
        <button onClick={()=>setModal('create')} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold cursor-pointer" style={{background:'#F59E0B',color:'#fff'}}
          onMouseEnter={(e)=>{e.currentTarget.style.background='#D97706'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F59E0B'}}>
          <Plus size={16} strokeWidth={2.5}/>New Rule
        </button>
      </div>

      <div className="flex items-center gap-3 mb-5 flex-wrap">
        <div className="relative max-w-xs flex-1">
          <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2" style={{color:'#CBD5E1'}}/>
          <input type="text" placeholder="Search rules..." value={search} onChange={e=>setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl text-sm outline-none" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',color:'#0F172A'}}
            onFocus={(e)=>{e.target.style.borderColor='#F59E0B'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0'}}/>
        </div>
        <div className="flex rounded-xl overflow-hidden" style={{border:'1px solid #E2E8F0'}}>
          {['all','active','inactive'].map(s=>(
            <button key={s} onClick={()=>setFilterStatus(s)} className="px-4 py-2 text-sm font-medium cursor-pointer capitalize"
              style={filterStatus===s ? {background:'#F59E0B',color:'#fff'} : {background:'#FFFFFF',color:'#94A3B8'}}
              onMouseEnter={(e)=>{if(filterStatus!==s){e.currentTarget.style.background='#F8FAFC';e.currentTarget.style.color='#64748B'}}}
              onMouseLeave={(e)=>{if(filterStatus!==s){e.currentTarget.style.background='#FFFFFF';e.currentTarget.style.color='#94A3B8'}}}>{s}</button>
          ))}
        </div>
      </div>

      <div className="rounded-2xl overflow-hidden" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 1px 3px rgba(0,0,0,0.06)'}}>
        {filtered.length===0 ? (
          <div className="text-center py-16"><ListFilter size={32} color="#E2E8F0" className="mx-auto mb-3"/><p className="text-sm" style={{color:'#CBD5E1'}}>No rules found</p></div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow style={{borderColor:'#F1F5F9',background:'#F8FAFC'}}>
                <TableHead style={{color:'#94A3B8'}}>Rule Name</TableHead>
                <TableHead style={{color:'#94A3B8'}}>Condition</TableHead>
                <TableHead style={{color:'#94A3B8'}}>Pattern</TableHead>
                <TableHead style={{color:'#94A3B8'}}>Category</TableHead>
                <TableHead className="text-center" style={{color:'#94A3B8'}}>Priority</TableHead>
                <TableHead className="text-center" style={{color:'#94A3B8'}}>Status</TableHead>
                <TableHead/>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.sort((a,b)=>a.priority-b.priority).map((rule)=>{
                const badge = COND_BADGE[rule.conditionType]??COND_BADGE.contains
                const cat = categories.find(c=>c.id===rule.categoryId)
                return (
                  <TableRow key={rule.id} style={{borderColor:'#F1F5F9'}}
                    onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}}
                    onMouseLeave={(e)=>{e.currentTarget.style.background='transparent'}}>
                    <TableCell>
                      <span className="font-semibold text-sm" style={{color:'#0F172A'}}>{rule.name}</span>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center text-xs font-semibold px-2.5 py-1 rounded-full" style={{background:badge.bg,color:badge.color}}>{badge.label}</span>
                    </TableCell>
                    <TableCell>
                      <span className="text-xs font-mono px-2.5 py-1.5 rounded-lg" style={{background:'#F1F5F9',color:'#64748B'}}>{rule.pattern}</span>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-1.5">
                        {cat && <span className="w-2 h-2 rounded-full shrink-0" style={{background:cat.color}}/>}
                        <span className="text-sm" style={{color:'#334155'}}>{rule.categoryName}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <span className="w-7 h-7 inline-flex items-center justify-center rounded-lg text-xs font-bold" style={{background:'#F1F5F9',color:'#64748B'}}>{rule.priority}</span>
                    </TableCell>
                    <TableCell className="text-center">
                      <button onClick={()=>toggleStatus(rule.id)} className="cursor-pointer" style={{color:rule.status==='active'?'#10B981':'#CBD5E1'}} title={rule.status==='active'?'Disable':'Enable'}>
                        {rule.status==='active'?<CheckCircle2 size={18}/>:<AlertCircle size={18}/>}
                      </button>
                    </TableCell>
                    <TableCell>
                      <div className="relative flex justify-end" onClick={(e)=>e.stopPropagation()}>
                        <button onClick={()=>setMenuOpen(menuOpen===rule.id?null:rule.id)}
                          className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer" style={{color:'#CBD5E1'}}
                          onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9';e.currentTarget.style.color='#94A3B8'}} onMouseLeave={(e)=>{e.currentTarget.style.background='transparent';e.currentTarget.style.color='#CBD5E1'}}>
                          <MoreHorizontal size={16}/>
                        </button>
                        {menuOpen===rule.id && (
                          <div className="absolute right-0 top-9 z-20 rounded-xl py-1 w-36 shadow-lg" style={{background:'#FFFFFF',border:'1px solid #E2E8F0'}}>
                            <button onClick={()=>{setModal(rule);setMenuOpen(null)}} className="flex items-center gap-2 w-full px-3 py-2 text-xs cursor-pointer" style={{color:'#64748B'}}
                              onMouseEnter={(e)=>{e.currentTarget.style.background='#F8FAFC';e.currentTarget.style.color='#0F172A'}} onMouseLeave={(e)=>{e.currentTarget.style.background='transparent';e.currentTarget.style.color='#64748B'}}>
                              <Pencil size={13}/>Edit
                            </button>
                            <button onClick={()=>{setConfirmDelete(rule);setMenuOpen(null)}} className="flex items-center gap-2 w-full px-3 py-2 text-xs cursor-pointer" style={{color:'#EF4444'}}
                              onMouseEnter={(e)=>{e.currentTarget.style.background='#FEF2F2'}} onMouseLeave={(e)=>{e.currentTarget.style.background='transparent'}}>
                              <Trash2 size={13}/>Delete
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

      {modal!==null && <RuleModal rule={modal==='create'?null:modal} categories={categories} onClose={()=>setModal(null)} onSave={handleSave}/>}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{background:'rgba(15,23,42,0.4)'}} onClick={(e)=>e.target===e.currentTarget&&setConfirmDelete(null)}>
          <div className="w-full max-w-sm rounded-2xl p-6 animate-fade-in" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 20px 60px rgba(0,0,0,0.15)'}}>
            <div className="flex items-center justify-center w-12 h-12 rounded-xl mx-auto mb-4" style={{background:'rgba(239,68,68,0.1)'}}><Trash2 size={22} color="#EF4444"/></div>
            <h2 className="font-bold text-center mb-2" style={{color:'#0F172A'}}>Delete Rule</h2>
            <p className="text-sm text-center mb-6" style={{color:'#64748B'}}>Delete <strong style={{color:'#0F172A'}}>{confirmDelete.name}</strong>? This action cannot be undone.</p>
            <div className="flex gap-3">
              <button onClick={()=>setConfirmDelete(null)} className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer" style={{background:'#F8FAFC',color:'#64748B',border:'1px solid #E2E8F0'}}
                onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F8FAFC'}}>Cancel</button>
              <button onClick={()=>handleDelete(confirmDelete.id)} className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer" style={{background:'#EF4444',color:'#fff'}}
                onMouseEnter={(e)=>{e.currentTarget.style.background='#DC2626'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#EF4444'}}>Delete</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
