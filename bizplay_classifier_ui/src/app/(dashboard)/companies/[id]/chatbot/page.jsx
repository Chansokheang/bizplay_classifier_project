'use client'

import { useState, useRef, useEffect } from 'react'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import {
  Bot, Save, CheckCircle2, Sliders, Cpu, Key, Eye, EyeOff,
  Info, Sparkles, RotateCcw, BookOpen, Loader2, AlertCircle, ChevronDown,
} from 'lucide-react'
import { COMPANIES, CATEGORIES } from '../../../../../lib/mock-data'
import { saveBotConfig, enhancePrompt, getBotConfig } from '../../../../../service/botConfigService'

const MODELS = [
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6', provider: 'Anthropic', color: '#F59E0B' },
  { value: 'EXAONE-3.5-7.8B-Instruct-AWQ', label: 'EXAONE-3.5-7.8B-Instruct-AWQ', provider: 'EXAONE', color: '#8B5CF6' },
]

const DEFAULT_PROMPT = `You are an intelligent expense classification assistant for {company}. Your role is to analyze transactions and classify them against the available rules and categories.\n\nWhen given a transaction, identify the best matching category and rule with a confidence score and brief reasoning.\n\nAlways be concise and accurate.`

const card = { background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }

const PROVIDER_COLORS = { Anthropic: '#F59E0B', EXAONE: '#8B5CF6' }

function ModelDropdown({ value, onChange }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const selected = MODELS.find((m) => m.value === value)

  useEffect(() => {
    const handler = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const grouped = MODELS.reduce((acc, m) => {
    if (!acc[m.provider]) acc[m.provider] = []
    acc[m.provider].push(m)
    return acc
  }, {})

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3.5 py-2.5 rounded-xl text-sm cursor-pointer"
        style={{ background: '#F8FAFC', border: `1.5px solid ${open ? '#1A32D8' : '#E2E8F0'}`, color: '#0F172A', background: open ? '#FFFFFF' : '#F8FAFC' }}
      >
        <div className="flex items-center gap-2.5">
          <span className="w-2 h-2 rounded-full shrink-0" style={{ background: selected?.color }} />
          <span className="font-semibold text-sm" style={{ color: '#0F172A' }}>{selected?.label}</span>
          <span className="text-xs px-2 py-0.5 rounded-full font-medium" style={{ background: `${selected?.color}15`, color: selected?.color }}>
            {selected?.provider}
          </span>
        </div>
        <ChevronDown
          size={15}
          style={{ color: '#94A3B8', transform: open ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}
        />
      </button>

      {open && (
        <div
          className="absolute z-20 w-full mt-1.5 rounded-xl overflow-hidden py-1"
          style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 8px 24px rgba(0,0,0,0.1)' }}
        >
          {Object.entries(grouped).map(([provider, models], gIdx) => (
            <div key={provider}>
              {gIdx > 0 && <div style={{ height: '1px', background: '#F1F5F9', margin: '4px 0' }} />}
              <p className="px-3.5 pt-2 pb-1 text-[10px] font-bold uppercase tracking-widest" style={{ color: '#CBD5E1' }}>
                {provider}
              </p>
              {models.map((model) => {
                const isSelected = model.value === value
                return (
                  <button
                    key={model.value}
                    type="button"
                    onClick={() => { onChange(model.value); setOpen(false) }}
                    className="w-full flex items-center gap-2.5 px-3.5 py-2.5 text-left cursor-pointer"
                    style={{ background: isSelected ? `${model.color}08` : 'transparent' }}
                    onMouseEnter={(e) => { if (!isSelected) e.currentTarget.style.background = '#F8FAFC' }}
                    onMouseLeave={(e) => { if (!isSelected) e.currentTarget.style.background = 'transparent' }}
                  >
                    <span className="w-2 h-2 rounded-full shrink-0" style={{ background: model.color }} />
                    <span className="text-sm font-medium flex-1" style={{ color: isSelected ? '#0F172A' : '#334155' }}>
                      {model.label}
                    </span>
                    {isSelected && <CheckCircle2 size={13} style={{ color: model.color }} />}
                  </button>
                )
              })}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function Slider({ label, value, min, max, step = 0.01, onChange, format }) {
  return (
    <div className="space-y-2">
      <div className="flex justify-between">
        <label className="text-xs font-semibold" style={{ color: '#64748B' }}>{label}</label>
        <span className="text-xs font-mono font-bold" style={{ color: '#1A32D8' }}>{format ? format(value) : value}</span>
      </div>
      <input
        type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(parseFloat(e.target.value))}
        className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
        style={{
          background: `linear-gradient(to right, #1A32D8 ${((value - min) / (max - min)) * 100}%, #E2E8F0 ${((value - min) / (max - min)) * 100}%)`,
          accentColor: '#1A32D8',
        }}
      />
      <div className="flex justify-between text-xs" style={{ color: '#CBD5E1' }}>
        <span>{min}</span><span>{max}</span>
      </div>
    </div>
  )
}

function SampleRowsDropdown({ value, onChange }) {
  const [open, setOpen] = useState(false)
  const options = [100, 200, 500, 600, 800, 1000]

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1 bg-transparent text-xs font-mono font-bold outline-none cursor-pointer"
        style={{ color: '#1A32D8' }}
      >
        <span>{value} rows</span>
        <ChevronDown size={14} style={{ transform: open ? 'rotate(180deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }} />
      </button>

      {open && (
        <div 
          className="absolute bottom-full mb-3 left-1/2 -translate-x-1/2 w-28 rounded-xl overflow-hidden py-1 z-50 text-xs font-mono font-bold"
          style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 8px 24px rgba(0,0,0,0.1)' }}
        >
          {options.map(opt => (
            <button
              key={opt}
              onClick={() => { onChange(opt); setOpen(false) }}
              className="w-full text-center px-3 py-2 transition-colors cursor-pointer block"
              style={{
                color: opt === value ? '#1A32D8' : '#64748B',
                background: opt === value ? 'rgba(26,50,216,0.05)' : 'transparent'
              }}
              onMouseEnter={(e) => { e.currentTarget.style.background = opt === value ? 'rgba(26,50,216,0.05)' : '#F8FAFC'; e.currentTarget.style.color = opt === value ? '#1A32D8' : '#0F172A' }}
              onMouseLeave={(e) => { e.currentTarget.style.background = opt === value ? 'rgba(26,50,216,0.05)' : 'transparent'; e.currentTarget.style.color = opt === value ? '#1A32D8' : '#64748B' }}
            >
              {opt}
            </button>
          ))}
        </div>
      )}
      
      {open && <div className="fixed inset-0 z-40" onClick={() => setOpen(false)} />}
    </div>
  )
}

export default function ChatbotPage() {
  const { id: companyId } = useParams()
  const { data: session } = useSession()
  const token = session?.accessToken

  const company    = COMPANIES.find((c) => c.id === companyId)
  const categories = CATEGORIES.filter((c) => c.companyId === companyId)

  const [showKey, setShowKey] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [config, setConfig]   = useState({
    model: 'claude-sonnet-4-6',
    apiKey: '',
    temperature: 0.3,
    systemPrompt: DEFAULT_PROMPT.replace('{company}', company?.name ?? 'your company'),
  })
  const [sampleRows, setSampleRows] = useState(500)

  // Fetch standard config on mount
  useEffect(() => {
    if (!companyId || !token) return
    const fetchConfig = async () => {
      try {
        const res = await getBotConfig(companyId, token)
        if (res && res.payload && res.payload.config) {
           let c = res.payload.config
           // The API might return config as a JSON string, sometimes double-stringified
           try {
               while (typeof c === 'string') {
                   // Clean up literal control characters that break JSON.parse
                   const sanitized = c.replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
                   c = JSON.parse(sanitized)
               }
           } catch(err) {
               console.error("Config JSON Parsing constraint:", err)
           }
           if (c && typeof c === 'object') {
             setConfig((prev) => ({
               ...prev,
               model: c.modelName || 'claude-sonnet-4-6',
               apiKey: c.apiKey || prev.apiKey,
               temperature: c.temperature ?? 0.3,
               systemPrompt: c.systemPrompt || prev.systemPrompt
             }))
           }
        }
      } catch (e) {
        console.error("Failed to load generic config", e)
      } finally {
        setIsLoading(false)
      }
    }
    fetchConfig()
  }, [companyId, token])

  // Save config state
  const [saveStatus, setSaveStatus]   = useState(null)  // null | 'loading' | 'success' | 'error'
  const [saveMsg, setSaveMsg]         = useState('')

  // Enhance prompt state
  const [enhanceStatus, setEnhanceStatus] = useState(null)  // null | 'loading' | 'success' | 'error'
  const [enhanceMsg, setEnhanceMsg]       = useState('')

  const handleSave = async () => {
    setSaveStatus('loading')
    setSaveMsg('')
    try {
      await saveBotConfig(
        companyId,
        { 
          modelName: config.model, 
          temperature: config.temperature, 
          apiKey: config.apiKey,
          systemPrompt: config.systemPrompt 
        },
        token
      )
      setSaveStatus('success')
      setSaveMsg('Configuration saved.')
      setTimeout(() => setSaveStatus(null), 3000)
    } catch (err) {
      setSaveStatus('error')
      setSaveMsg(err.message)
    }
  }

  const handleEnhance = async () => {
    setEnhanceStatus('loading')
    setEnhanceMsg('')
    try {
      const res = await enhancePrompt(companyId, sampleRows, token)
      
      let newPrompt = null;
      if (res && res.payload) {
         if (typeof res.payload === 'string') {
             newPrompt = res.payload;
         } else if (res.payload.systemPrompt) {
             newPrompt = res.payload.systemPrompt;
         } else if (res.payload.config) {
             let c = res.payload.config
             try {
                 while (typeof c === 'string') {
                     const sanitized = c.replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
                     c = JSON.parse(sanitized)
                 }
             } catch(err) {
                 console.error("Enhance Parse Error:", err)
             }
             if (c && typeof c === 'object' && c.systemPrompt) {
                 newPrompt = c.systemPrompt;
             }
         }
      }

      if (newPrompt) {
          setConfig((prev) => ({ ...prev, systemPrompt: String(newPrompt) }))
      }

      setEnhanceStatus('success')
      setEnhanceMsg('System prompt updated from training data.')
      setTimeout(() => setEnhanceStatus(null), 4000)
    } catch (err) {
      setEnhanceStatus('error')
      setEnhanceMsg(err.message)
    }
  }

  const selModel = MODELS.find((m) => m.value === config.model)

  return (
    <div className="py-8 animate-fade-in relative min-h-[70vh]">
      {isLoading && (
        <div className="absolute inset-0 z-50 flex items-center justify-center rounded-2xl bg-slate-50/50 backdrop-blur-sm">
          <Loader2 size={32} className="animate-spin text-blue-600" />
        </div>
      )}

      {/* Header */}
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Chatbot Configuration</h1>
          <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
            Configure the AI model and system prompt used to classify transactions.
          </p>
        </div>
      </div>

      {/* Main Unified Settings Card */}
      <div className="bg-[#FFFFFF] rounded-2xl shadow-[0_2px_12px_rgba(15,23,42,0.04)] border border-slate-200 overflow-hidden flex flex-col w-full">

        <div className="grid grid-cols-1 lg:grid-cols-3 relative w-full items-stretch flex-1">
          {/* Vertical Line Divider */}
          <div className="hidden lg:block absolute left-[66.66%] top-6 bottom-6 w-px bg-slate-100" />
          
          {/* ── Left: Main Prompt Engineering Workspace ── */}
          <div className="lg:col-span-2 flex flex-col h-full pl-6 pr-6 md:pl-8 lg:pr-10 py-6 md:py-8 min-h-[500px]">
            {/* Header: Enhance Section */}
            <div className="flex flex-wrap items-center justify-between gap-4 mb-5">
              <div>
                <div className="flex items-center gap-2">
                  <Sparkles size={18} color="#1A32D8" />
                  <h2 className="font-bold text-[15px]" style={{ color: '#0F172A' }}>Enhanced System Prompt</h2>
                </div>
                <p className="text-xs mt-1" style={{ color: '#64748B' }}>
                  The core instructions injected into the AI. <span className="hidden sm:inline">Enhance it using patterns from your training data.</span>
                </p>
              </div>
            </div>

            {/* Prompt Editor */}
            <div className="flex-1 flex flex-col min-h-0">
              <div className="relative flex-1 flex flex-col min-h-[350px]">
                {enhanceStatus === 'loading' && (
                  <div className="absolute inset-0 z-10 flex flex-col items-center justify-center rounded-xl" style={{ background: 'rgba(248, 250, 252, 0.7)', backdropFilter: 'blur(4px)' }}>
                    <div className="flex flex-col items-center gap-3 animate-fade-in">
                      <div className="p-3 rounded-full shadow-sm" style={{ background: '#FFFFFF', border: '1px solid #E2E8F0' }}>
                         <Loader2 size={24} className="animate-spin" color="#1A32D8" />
                      </div>
                      <p className="text-sm font-semibold" style={{ color: '#1A32D8' }}>Analyzing training data & enhancing prompt...</p>
                    </div>
                  </div>
                )}
                <textarea
                  value={config.systemPrompt}
                  onChange={(e) => setConfig({ ...config, systemPrompt: e.target.value })}
                  className="w-full flex-1 p-4 pb-16 rounded-xl text-sm outline-none resize-y leading-relaxed font-mono transition-all"
                  style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', color: '#0F172A' }}
                  onFocus={(e) => { e.target.style.borderColor = '#1A32D8'; e.target.style.background = '#FFFFFF'; e.target.style.boxShadow = '0 0 0 4px rgba(26,50,216,0.05)' }}
                  onBlur={(e) => { e.target.style.borderColor = '#E2E8F0'; e.target.style.background = '#F8FAFC'; e.target.style.boxShadow = 'none' }}
                />

                {/* Floating AI Toolbar */}
                <div className="absolute bottom-3 right-3 flex items-center gap-2 p-1.5 rounded-full shadow-lg" style={{ background: '#FFFFFF', border: '1px solid rgba(226, 232, 240, 0.8)', backdropFilter: 'blur(8px)' }}>
                   <div className="flex items-center gap-1.5 pl-3 pr-2 border-r border-slate-100">
                      <span className="text-[10px] font-bold uppercase tracking-wider" style={{ color: '#94A3B8' }}>Train on</span>
                      <SampleRowsDropdown value={sampleRows} onChange={setSampleRows} />
                   </div>
                   
                   <button
                     onClick={handleEnhance}
                     disabled={enhanceStatus === 'loading'}
                     className="flex items-center gap-1.5 px-4 py-1.5 rounded-full text-xs font-bold cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed transition-all"
                     style={{ background: 'linear-gradient(to right, #1A32D8, #3B82F6)', color: '#FFFFFF' }}
                     onMouseEnter={(e) => { if (enhanceStatus !== 'loading') { e.currentTarget.style.boxShadow = '0 4px 12px rgba(26,50,216,0.3)'; } }}
                     onMouseLeave={(e) => { e.currentTarget.style.boxShadow = 'none'; }}
                   >
                     {enhanceStatus === 'loading'
                       ? <Loader2 size={13} className="animate-spin" />
                       : <Sparkles size={13} />
                     }
                     {enhanceStatus === 'loading' ? 'Auto-Enhancing' : 'Enhance'}
                   </button>
                </div>
              </div>
            </div>
            
            <div className="pb-2 mt-4">
              {/* Subtly placed enhancement status messages directly below editor */}
              {enhanceStatus === 'success' && (
                <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-lg text-xs font-medium" style={{ background: 'rgba(16,185,129,0.08)', color: '#059669' }}>
                  <CheckCircle2 size={14} />{enhanceMsg}
                </div>
              )}
              {enhanceStatus === 'error' && (
                <div className="flex items-center gap-2 mb-3 px-3 py-2 rounded-lg text-xs font-medium" style={{ background: 'rgba(239,68,68,0.08)', color: '#DC2626' }}>
                  <AlertCircle size={14} />{enhanceMsg}
                </div>
              )}

              {/* Minimal Prerequisite Info */}
              <div className="flex items-center gap-2 text-xs" style={{ color: '#94A3B8' }}>
                <Info size={14} />
                <p>Ensure your AI model is trained on historical data before using prompt enhancement.</p>
              </div>
            </div>
          </div>

        {/* ── Right: Model & API Settings sidebar ── */}
        <div className="flex flex-col space-y-8 pr-6 pl-6 md:pr-8 lg:pl-10 py-6 md:py-8">
          <div>
            <div className="flex items-center justify-between mb-5">
               <div className="flex items-center gap-2">
                 <Cpu size={18} color="#1A32D8" />
                 <h2 className="font-bold text-[15px]" style={{ color: '#0F172A' }}>Model Settings</h2>
               </div>
            </div>
            
            <div className="space-y-6">
              <div className="space-y-2">
                <label className="text-xs font-semibold" style={{ color: '#64748B' }}>AI Model</label>
                <ModelDropdown
                  value={config.model}
                  onChange={(v) => setConfig({ ...config, model: v })}
                />
              </div>

              <div className="space-y-1.5">
                <label className="text-xs font-semibold flex items-center justify-between" style={{ color: '#64748B' }}>
                  <span className="flex items-center gap-1.5"><Key size={13} />API Key</span>
                </label>
                <div className="relative">
                  <input
                    type={showKey ? 'text' : 'password'}
                    value={config.apiKey}
                    onChange={(e) => setConfig({ ...config, apiKey: e.target.value })}
                    placeholder="sk-••••••••"
                    className="w-full pr-8 pl-3 py-2.5 rounded-xl text-xs outline-none font-mono transition-colors"
                    style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', color: '#0F172A' }}
                    onFocus={(e) => { e.target.style.borderColor = '#1A32D8'; e.target.style.background = '#FFFFFF' }}
                    onBlur={(e) => { e.target.style.borderColor = '#E2E8F0'; e.target.style.background = '#F8FAFC' }}
                  />
                  <button
                    onClick={() => setShowKey((s) => !s)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 cursor-pointer"
                    style={{ color: '#CBD5E1' }}
                    onMouseEnter={(e) => { e.currentTarget.style.color = '#94A3B8' }}
                    onMouseLeave={(e) => { e.currentTarget.style.color = '#CBD5E1' }}
                  >
                    {showKey ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              <div className="pt-2">
                <Slider
                  label="Temperature"
                  value={config.temperature}
                  min={0} max={2} step={0.05}
                  onChange={(v) => setConfig({ ...config, temperature: v })}
                  format={(v) => v.toFixed(2)}
                />
              </div>

              <div className="pt-6 mt-4 border-t border-slate-100 flex flex-col gap-3">
                 {saveStatus === 'error' && (
                   <span className="flex items-center justify-center gap-1.5 text-xs font-medium" style={{ color: '#EF4444' }}>
                     <AlertCircle size={13} />{saveMsg}
                   </span>
                 )}
                 <button
                   onClick={saveStatus === 'loading' ? undefined : handleSave}
                   className={`flex items-center justify-center gap-2 w-full px-4 py-2 rounded-xl text-xs font-semibold transition-all ${saveStatus === 'loading' ? 'cursor-not-allowed opacity-70' : 'cursor-pointer shadow-[0_2px_8px_rgba(26,50,216,0.2)] hover:shadow-[0_4px_12px_rgba(26,50,216,0.3)] hover:-translate-y-0.5'}`}
                   style={{ 
                       background: saveStatus === 'success' ? '#10B981' : 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)', 
                       color: '#FFFFFF' 
                   }}
                 >
                   {saveStatus === 'loading' ? (
                     <><Loader2 size={13} className="animate-spin" />Saving…</>
                   ) : saveStatus === 'success' ? (
                     <><CheckCircle2 size={13} />Saved Successfully</>
                   ) : (
                     <><Save size={13} />Save Configurations</>
                   )}
                 </button>
              </div>

            </div>
          </div>
          
          {/* Integration Info */}
          <div className="mt-4 pt-6 border-t border-slate-100">
            <div className="flex items-start gap-2.5">
              <BookOpen size={14} color="#1A32D8" className="shrink-0 mt-0.5 opacity-80" />
              <p className="text-[12px] leading-relaxed font-medium" style={{ color: '#64748B' }}>
                This configuration serves as the global business logic parameter passed into the selected LLM whenever users upload their daily transaction data grids.
              </p>
            </div>
          </div>

        </div>
      </div>
    </div>
  </div>
  )
}
