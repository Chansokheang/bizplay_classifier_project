'use client'
import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { Tags, Plus, Search, Pencil, Trash2, X, Check, ChevronRight, ListFilter } from 'lucide-react'
import { COMPANIES, CATEGORIES as INIT_CATS } from '../../../../../lib/mock-data'
import {
  Table, TableBody, TableCell, TableHead,
  TableHeader, TableRow,
} from '../../../../../components/ui/table'

const COLORS = ['#EF4444','#F97316','#F59E0B','#EAB308','#84CC16','#10B981','#14B8A6','#06B6D4','#3B82F6','#6366F1','#8B5CF6','#A855F7','#EC4899','#F43F5E','#64748B','#475569']

function Modal({ category, onClose, onSave }) {
  const isEdit = !!category?.id
  const [form, setForm] = useState(category ?? { name:'', description:'', color:'#3B82F6' })
  const handleSubmit = (e) => { e.preventDefault(); if (!form.name.trim()) return; onSave({...form, id:form.id??String(Date.now()), rulesCount:form.rulesCount??0}) }
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{background:'rgba(15,23,42,0.4)'}} onClick={(e)=>e.target===e.currentTarget&&onClose()}>
      <div className="w-full max-w-md rounded-2xl overflow-hidden animate-fade-in" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 20px 60px rgba(0,0,0,0.15)'}}>
        <div className="flex items-center justify-between px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
          <div className="flex items-center gap-2.5">
            <div className="flex items-center justify-center w-8 h-8 rounded-lg" style={{background:`${form.color}15`}}><Tags size={16} color={form.color}/></div>
            <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>{isEdit?'Edit Category':'New Category'}</h2>
          </div>
          <button onClick={onClose} className="flex items-center justify-center w-8 h-8 rounded-lg cursor-pointer" style={{color:'#94A3B8'}}
            onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}} onMouseLeave={(e)=>{e.currentTarget.style.background='transparent'}}><X size={16}/></button>
        </div>
        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Name *</label>
            <input type="text" required placeholder="e.g. Travel & Transport" value={form.name} onChange={(e)=>setForm({...form,name:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
          </div>
          <div className="space-y-1.5">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Description</label>
            <textarea rows={2} placeholder="Brief description..." value={form.description} onChange={(e)=>setForm({...form,description:e.target.value})}
              className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none resize-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
              onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
          </div>
          <div className="space-y-2">
            <label className="text-xs font-semibold" style={{color:'#64748B'}}>Color</label>
            <div className="flex flex-wrap gap-2">
              {COLORS.map(c=>(
                <button key={c} type="button" onClick={()=>setForm({...form,color:c})}
                  className="w-7 h-7 rounded-full cursor-pointer flex items-center justify-center"
                  style={{background:c, outline:form.color===c?`3px solid ${c}`:'none', outlineOffset:'2px'}}>
                  {form.color===c && <Check size={13} color="#fff" strokeWidth={3}/>}
                </button>
              ))}
            </div>
          </div>
          <div className="flex gap-3 pt-1">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-xl text-sm font-medium cursor-pointer"
              style={{background:'#F8FAFC',color:'#64748B',border:'1px solid #E2E8F0'}}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F8FAFC'}}>Cancel</button>
            <button type="submit" className="flex-1 py-2.5 rounded-xl text-sm font-bold cursor-pointer"
              style={{background:'#F59E0B',color:'#fff'}}
              onMouseEnter={(e)=>{e.currentTarget.style.background='#D97706'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F59E0B'}}>
              {isEdit?'Save Changes':'Create Category'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default function CategoriesPage() {
  const { id: companyId } = useParams()
  const company = COMPANIES.find(c=>c.id===companyId)
  const baseCategories = INIT_CATS.filter(c=>c.companyId===companyId)
  const seedCategories = baseCategories.length ? baseCategories : INIT_CATS.map(c=>({ ...c, companyId }))
  const [categories, setCategories] = useState(seedCategories)
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState(null)
  const [confirmDelete, setConfirmDelete] = useState(null)

  const filtered = categories.filter(c=>c.name.toLowerCase().includes(search.toLowerCase())||c.description.toLowerCase().includes(search.toLowerCase()))

  const handleSave = (data) => { setCategories(prev=>{ const e=prev.find(c=>c.id===data.id); return e?prev.map(c=>c.id===data.id?{...c,...data}:c):[...prev,{...data,companyId}] }); setModal(null) }
  const handleDelete = (id) => { setCategories(prev=>prev.filter(c=>c.id!==id)); setConfirmDelete(null) }

  return (
    <div className="py-8 animate-fade-in">

      {/* Header */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{color:'#0F172A'}}>Categories</h1>
          <p className="mt-1 text-sm" style={{color:'#94A3B8'}}>{categories.length} categories · {categories.reduce((s,c)=>s+c.rulesCount,0)} total rules</p>
        </div>
        <button onClick={()=>setModal('create')} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold cursor-pointer" style={{background:'#F59E0B',color:'#fff'}}
          onMouseEnter={(e)=>{e.currentTarget.style.background='#D97706'}} onMouseLeave={(e)=>{e.currentTarget.style.background='#F59E0B'}}>
          <Plus size={16} strokeWidth={2.5}/>New Category
        </button>
      </div>

      {/* Category pills */}
      <div className="flex flex-wrap gap-2 mb-6">
        {categories.slice(0,6).map(cat=>(
          <span key={cat.id} className="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium" style={{background:'#F8FAFC',color:'#475569',border:'1px solid #E2E8F0'}}>
            <span className="w-1.5 h-1.5 rounded-full" style={{background:cat.color}}/>{cat.name}
          </span>
        ))}
        {categories.length>6 && <span className="px-3 py-1.5 rounded-full text-xs font-medium" style={{background:'#F8FAFC',color:'#94A3B8',border:'1px solid #E2E8F0'}}>+{categories.length-6} more</span>}
      </div>

      {/* Search */}
      <div className="relative mb-5 max-w-sm">
        <Search size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2" style={{color:'#CBD5E1'}}/>
        <input type="text" placeholder="Search categories..." value={search} onChange={e=>setSearch(e.target.value)}
          className="w-full pl-10 pr-4 py-2.5 rounded-xl text-sm outline-none" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',color:'#0F172A'}}
          onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#FFFFFF'}}/>
      </div>

      {/* Table */}
      <div className="rounded-2xl overflow-hidden" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 1px 3px rgba(0,0,0,0.06)'}}>
        {filtered.length === 0 ? (
          <div className="text-center py-16">
            <Tags size={32} color="#E2E8F0" className="mx-auto mb-3"/>
            <p className="text-sm" style={{color:'#CBD5E1'}}>No categories found</p>
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow style={{borderColor:'#F1F5F9',background:'#F8FAFC'}}>
                <TableHead style={{color:'#94A3B8'}}>Color</TableHead>
                <TableHead style={{color:'#94A3B8'}}>Name</TableHead>
                <TableHead style={{color:'#94A3B8'}}>Description</TableHead>
                <TableHead className="text-right" style={{color:'#94A3B8'}}>Rules</TableHead>
                <TableHead style={{color:'#94A3B8'}}/>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((cat) => (
                <TableRow
                  key={cat.id}
                  style={{borderColor:'#F1F5F9'}}
                  onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9'}}
                  onMouseLeave={(e)=>{e.currentTarget.style.background='transparent'}}
                >
                  <TableCell>
                    <div className="w-5 h-5 rounded-full" style={{background:cat.color}}/>
                  </TableCell>
                  <TableCell>
                    <span className="font-semibold text-sm" style={{color:'#0F172A'}}>{cat.name}</span>
                  </TableCell>
                  <TableCell>
                    <span className="text-sm" style={{color:'#94A3B8'}}>{cat.description}</span>
                  </TableCell>
                  <TableCell className="text-right">
                    <Link href={`/companies/${companyId}/rules`}
                      className="inline-flex items-center gap-1 text-xs font-medium px-2.5 py-1 rounded-full cursor-pointer"
                      style={{background:'#F1F5F9',color:'#64748B'}}
                      onMouseEnter={(e)=>{e.currentTarget.style.background='#E2E8F0'}}
                      onMouseLeave={(e)=>{e.currentTarget.style.background='#F1F5F9'}}>
                      <ListFilter size={11}/>{cat.rulesCount} rule{cat.rulesCount!==1?'s':''}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={()=>setModal(cat)}
                        className="flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer"
                        style={{color:'#CBD5E1'}}
                        onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9';e.currentTarget.style.color='#64748B'}}
                        onMouseLeave={(e)=>{e.currentTarget.style.background='transparent';e.currentTarget.style.color='#CBD5E1'}}>
                        <Pencil size={13}/>
                      </button>
                      <button onClick={()=>setConfirmDelete(cat)}
                        className="flex items-center justify-center w-7 h-7 rounded-lg cursor-pointer"
                        style={{color:'#CBD5E1'}}
                        onMouseEnter={(e)=>{e.currentTarget.style.background='#FEF2F2';e.currentTarget.style.color='#EF4444'}}
                        onMouseLeave={(e)=>{e.currentTarget.style.background='transparent';e.currentTarget.style.color='#CBD5E1'}}>
                        <Trash2 size={13}/>
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>

      {modal!==null && <Modal category={modal==='create'?null:modal} onClose={()=>setModal(null)} onSave={handleSave}/>}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{background:'rgba(15,23,42,0.4)'}} onClick={(e)=>e.target===e.currentTarget&&setConfirmDelete(null)}>
          <div className="w-full max-w-sm rounded-2xl p-6 animate-fade-in" style={{background:'#FFFFFF',border:'1px solid #E2E8F0',boxShadow:'0 20px 60px rgba(0,0,0,0.15)'}}>
            <div className="flex items-center justify-center w-12 h-12 rounded-xl mx-auto mb-4" style={{background:'rgba(239,68,68,0.1)'}}><Trash2 size={22} color="#EF4444"/></div>
            <h2 className="font-bold text-center mb-2" style={{color:'#0F172A'}}>Delete Category</h2>
            <p className="text-sm text-center mb-6" style={{color:'#64748B'}}>Delete <strong style={{color:'#0F172A'}}>{confirmDelete.name}</strong>? This will also remove its rules.</p>
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
