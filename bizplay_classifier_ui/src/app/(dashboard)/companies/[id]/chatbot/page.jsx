'use client'
import { useState } from 'react'
import { useParams } from 'next/navigation'
import Link from 'next/link'
import { Bot, ChevronRight, ToggleLeft, ToggleRight, Save, CheckCircle2, Sliders, Cpu, Palette, Key, Eye, EyeOff, Info, Send, User, Sparkles, RotateCcw, BookOpen } from 'lucide-react'
import { COMPANIES, CATEGORIES } from '../../../../../lib/mock-data'

const MODELS = [
  { value:'gpt-4o',            label:'GPT-4o',            provider:'OpenAI',    color:'#10B981' },
  { value:'gpt-4o-mini',       label:'GPT-4o Mini',       provider:'OpenAI',    color:'#10B981' },
  { value:'claude-sonnet-4-6', label:'Claude Sonnet 4.6', provider:'Anthropic', color:'#F59E0B' },
  { value:'claude-haiku-4-5',  label:'Claude Haiku 4.5',  provider:'Anthropic', color:'#F59E0B' },
  { value:'gemini-1.5-pro',    label:'Gemini 1.5 Pro',    provider:'Google',    color:'#3B82F6' },
  { value:'gemini-1.5-flash',  label:'Gemini 1.5 Flash',  provider:'Google',    color:'#3B82F6' },
]
const DEFAULT_PROMPT = `You are an intelligent expense classification assistant for {company}. Your role is to help users understand and manage their expense classification rules and categories.\n\nWhen a user asks about a transaction, analyze it against the available rules and provide a classification recommendation with confidence reasoning.\n\nAlways be concise, professional, and helpful.`
const CHAT_PREVIEW = [
  { role:'assistant', text:"Hello! I'm your expense classification assistant. How can I help you today?" },
  { role:'user',      text:'Classify: SINGAPORE AIR SQ-1234, $1,250?' },
  { role:'assistant', text:'Based on your rules, this should be **Travel & Transport** (97% confidence). The keyword "SINGAPORE AIR" matches rule #1 "Airline Booking".' },
]
const card = { background:'#FFFFFF', border:'1px solid #E2E8F0', boxShadow:'0 1px 3px rgba(0,0,0,0.06)' }

function Slider({ label, value, min, max, step=0.01, onChange, format }) {
  return (
    <div className="space-y-2">
      <div className="flex justify-between">
        <label className="text-xs font-semibold" style={{color:'#64748B'}}>{label}</label>
        <span className="text-xs font-mono font-bold" style={{color:'#F59E0B'}}>{format?format(value):value}</span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value} onChange={(e)=>onChange(parseFloat(e.target.value))}
        className="w-full h-1.5 rounded-full appearance-none cursor-pointer"
        style={{background:`linear-gradient(to right, #F59E0B ${((value-min)/(max-min))*100}%, #E2E8F0 ${((value-min)/(max-min))*100}%)`,accentColor:'#F59E0B'}}/>
      <div className="flex justify-between text-xs" style={{color:'#CBD5E1'}}><span>{min}</span><span>{max}</span></div>
    </div>
  )
}

export default function ChatbotPage() {
  const { id: companyId } = useParams()
  const company = COMPANIES.find(c=>c.id===companyId)
  const categories = CATEGORIES.filter(c=>c.companyId===companyId)
  const [enabled, setEnabled] = useState(true)
  const [saved, setSaved] = useState(false)
  const [showKey, setShowKey] = useState(false)
  const [section, setSection] = useState('model')
  const [config, setConfig] = useState({
    model:'claude-sonnet-4-6', apiKey:'sk-••••••••••••••••••••••', temperature:0.3, maxTokens:1024,
    systemPrompt:DEFAULT_PROMPT.replace('{company}',company?.name??'your company'),
    botName:'BizBot', greeting:"Hello! I'm your expense classification assistant. How can I help you today?",
    contextWindow:10, includeCategories:true, includeRules:true, allowFreeform:false,
  })

  const handleSave = () => { setSaved(true); setTimeout(()=>setSaved(false),2500) }
  const selModel = MODELS.find(m=>m.value===config.model)

  const SECTIONS = [
    { id:'model',      label:'Model & API',  Icon:Cpu },
    { id:'behavior',   label:'Behavior',     Icon:Sliders },
    { id:'knowledge',  label:'Knowledge',    Icon:BookOpen },
    { id:'appearance', label:'Appearance',   Icon:Palette },
  ]

  return (
    <div className="py-8 animate-fade-in">
      <div className="flex items-start justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold" style={{color:'#0F172A'}}>Chatbot Configuration</h1>
          <p className="mt-1 text-sm" style={{color:'#94A3B8'}}>Configure an AI assistant to help classify and query expense transactions.</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2.5 px-4 py-2 rounded-xl" style={{background:'#FFFFFF',border:'1px solid #E2E8F0'}}>
            <span className="text-sm font-medium" style={{color:'#64748B'}}>{enabled?'Enabled':'Disabled'}</span>
            <button onClick={()=>setEnabled(e=>!e)} className="cursor-pointer" style={{color:enabled?'#10B981':'#CBD5E1'}}>
              {enabled?<ToggleRight size={28}/>:<ToggleLeft size={28}/>}
            </button>
          </div>
          <button onClick={handleSave} className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold cursor-pointer"
            style={saved ? {background:'rgba(16,185,129,0.1)',color:'#059669',border:'1px solid rgba(16,185,129,0.25)'} : {background:'#F59E0B',color:'#fff'}}
            onMouseEnter={(e)=>{if(!saved)e.currentTarget.style.background='#D97706'}} onMouseLeave={(e)=>{if(!saved)e.currentTarget.style.background='#F59E0B'}}>
            {saved?<><CheckCircle2 size={16}/>Saved</>:<><Save size={16}/>Save Config</>}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 space-y-4">
          {/* Section tabs */}
          <div className="flex gap-1 p-1 rounded-xl" style={{background:'#F1F5F9',border:'1px solid #E2E8F0'}}>
            {SECTIONS.map(({id,label,Icon})=>(
              <button key={id} onClick={()=>setSection(id)}
                className="flex items-center gap-2 flex-1 justify-center py-2 rounded-lg text-xs font-semibold cursor-pointer"
                style={section===id ? {background:'#FFFFFF',color:'#0F172A',boxShadow:'0 1px 3px rgba(0,0,0,0.08)'} : {background:'transparent',color:'#94A3B8'}}
                onMouseEnter={(e)=>{if(section!==id){e.currentTarget.style.color='#64748B'}}}
                onMouseLeave={(e)=>{if(section!==id){e.currentTarget.style.color='#94A3B8'}}}>
                <Icon size={14}/>{label}
              </button>
            ))}
          </div>

          {section==='model' && (
            <div className="rounded-xl overflow-hidden" style={card}>
              <div className="px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
                <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>Model & API Configuration</h2>
                <p className="text-xs mt-0.5" style={{color:'#94A3B8'}}>Select the AI model and configure API access.</p>
              </div>
              <div className="p-6 space-y-5">
                <div className="space-y-2">
                  <label className="text-xs font-semibold" style={{color:'#64748B'}}>AI Model</label>
                  <div className="grid grid-cols-2 gap-2">
                    {MODELS.map(model=>(
                      <button key={model.value} onClick={()=>setConfig({...config,model:model.value})}
                        className="flex items-center gap-2.5 px-3.5 py-3 rounded-xl cursor-pointer text-left"
                        style={config.model===model.value ? {background:`${model.color}10`,border:`1.5px solid ${model.color}30`,color:model.color} : {background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#94A3B8'}}
                        onMouseEnter={(e)=>{if(config.model!==model.value){e.currentTarget.style.background='#F1F5F9';e.currentTarget.style.color='#64748B'}}}
                        onMouseLeave={(e)=>{if(config.model!==model.value){e.currentTarget.style.background='#F8FAFC';e.currentTarget.style.color='#94A3B8'}}}>
                        <Cpu size={15} style={{color:model.color}}/>
                        <div><p className="text-xs font-semibold">{model.label}</p><p className="text-xs opacity-60">{model.provider}</p></div>
                        {config.model===model.value && <CheckCircle2 size={14} className="ml-auto"/>}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold flex items-center gap-1.5" style={{color:'#64748B'}}><Key size={13}/>API Key</label>
                  <div className="relative">
                    <input type={showKey?'text':'password'} value={config.apiKey} onChange={(e)=>setConfig({...config,apiKey:e.target.value})}
                      className="w-full pr-10 pl-3.5 py-2.5 rounded-xl text-sm outline-none font-mono" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
                      onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
                    <button onClick={()=>setShowKey(s=>!s)} className="absolute right-3.5 top-1/2 -translate-y-1/2 cursor-pointer" style={{color:'#CBD5E1'}}
                      onMouseEnter={(e)=>{e.currentTarget.style.color='#94A3B8'}} onMouseLeave={(e)=>{e.currentTarget.style.color='#CBD5E1'}}>
                      {showKey?<EyeOff size={16}/>:<Eye size={16}/>}
                    </button>
                  </div>
                  <div className="flex items-center gap-1.5 text-xs" style={{color:'#CBD5E1'}}><Info size={12}/>API key is encrypted and stored securely.</div>
                </div>
              </div>
            </div>
          )}

          {section==='behavior' && (
            <div className="rounded-xl overflow-hidden" style={card}>
              <div className="px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
                <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>Behavior Settings</h2>
                <p className="text-xs mt-0.5" style={{color:'#94A3B8'}}>Control how the AI responds and reasons.</p>
              </div>
              <div className="p-6 space-y-6">
                <Slider label="Temperature (Creativity)" value={config.temperature} min={0} max={1} step={0.05} onChange={v=>setConfig({...config,temperature:v})} format={v=>v.toFixed(2)}/>
                <Slider label="Max Response Tokens" value={config.maxTokens} min={256} max={4096} step={128} onChange={v=>setConfig({...config,maxTokens:v})} format={v=>v.toLocaleString()}/>
                <Slider label="Conversation Context Window" value={config.contextWindow} min={1} max={20} step={1} onChange={v=>setConfig({...config,contextWindow:v})} format={v=>`${v} turns`}/>
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="text-xs font-semibold" style={{color:'#64748B'}}>System Prompt</label>
                    <button onClick={()=>setConfig({...config,systemPrompt:DEFAULT_PROMPT.replace('{company}',company?.name??'your company')})}
                      className="flex items-center gap-1 text-xs cursor-pointer" style={{color:'#CBD5E1'}}
                      onMouseEnter={(e)=>{e.currentTarget.style.color='#F59E0B'}} onMouseLeave={(e)=>{e.currentTarget.style.color='#CBD5E1'}}>
                      <RotateCcw size={12}/>Reset
                    </button>
                  </div>
                  <textarea rows={6} value={config.systemPrompt} onChange={(e)=>setConfig({...config,systemPrompt:e.target.value})}
                    className="w-full px-3.5 py-2.5 rounded-xl text-xs outline-none resize-none leading-relaxed font-mono"
                    style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#334155'}}
                    onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
                </div>
              </div>
            </div>
          )}

          {section==='knowledge' && (
            <div className="rounded-xl overflow-hidden" style={card}>
              <div className="px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
                <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>Knowledge Base</h2>
                <p className="text-xs mt-0.5" style={{color:'#94A3B8'}}>Choose what context is provided to the AI.</p>
              </div>
              <div className="p-6 space-y-4">
                {[
                  { key:'includeCategories', label:'Include Categories', desc:`Provide all ${categories.length} categories for classification context`, color:'#3B82F6' },
                  { key:'includeRules',      label:'Include Rules',      desc:'Share classification rules so AI can explain matches', color:'#8B5CF6' },
                  { key:'allowFreeform',     label:'Allow Freeform Chat', desc:'Let users ask general expense management questions', color:'#F59E0B' },
                ].map(({key,label,desc,color})=>(
                  <div key={key} className="flex items-center justify-between p-4 rounded-xl cursor-pointer" style={{background:'#F8FAFC',border:'1px solid #F1F5F9'}}
                    onClick={()=>setConfig({...config,[key]:!config[key]})}
                    onMouseEnter={(e)=>{e.currentTarget.style.background='#F1F5F9';e.currentTarget.style.borderColor='#E2E8F0'}}
                    onMouseLeave={(e)=>{e.currentTarget.style.background='#F8FAFC';e.currentTarget.style.borderColor='#F1F5F9'}}>
                    <div className="flex-1">
                      <p className="text-sm font-semibold" style={{color:'#0F172A'}}>{label}</p>
                      <p className="text-xs mt-0.5" style={{color:'#94A3B8'}}>{desc}</p>
                    </div>
                    <button className="ml-4 cursor-pointer shrink-0" style={{color:config[key]?color:'#CBD5E1'}}>
                      {config[key]?<ToggleRight size={28}/>:<ToggleLeft size={28}/>}
                    </button>
                  </div>
                ))}
                {config.includeCategories && (
                  <div className="rounded-xl p-4" style={{background:'#F8FAFC',border:'1px solid #E2E8F0'}}>
                    <p className="text-xs font-semibold mb-3" style={{color:'#64748B'}}>{categories.length} categories injected into context:</p>
                    <div className="flex flex-wrap gap-2">
                      {categories.map(cat=>(
                        <span key={cat.id} className="text-xs px-2.5 py-1 rounded-full font-medium" style={{background:`${cat.color}12`,color:cat.color,border:`1px solid ${cat.color}25`}}>{cat.name}</span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {section==='appearance' && (
            <div className="rounded-xl overflow-hidden" style={card}>
              <div className="px-6 py-4" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
                <h2 className="font-semibold text-sm" style={{color:'#0F172A'}}>Appearance</h2>
              </div>
              <div className="p-6 space-y-4">
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold" style={{color:'#64748B'}}>Bot Name</label>
                  <input type="text" value={config.botName} onChange={(e)=>setConfig({...config,botName:e.target.value})} placeholder="e.g. BizBot"
                    className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
                    onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
                </div>
                <div className="space-y-1.5">
                  <label className="text-xs font-semibold" style={{color:'#64748B'}}>Greeting Message</label>
                  <textarea rows={3} value={config.greeting} onChange={(e)=>setConfig({...config,greeting:e.target.value})}
                    className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none resize-none" style={{background:'#F8FAFC',border:'1px solid #E2E8F0',color:'#0F172A'}}
                    onFocus={(e)=>{e.target.style.borderColor='#F59E0B';e.target.style.background='#FFFFFF'}} onBlur={(e)=>{e.target.style.borderColor='#E2E8F0';e.target.style.background='#F8FAFC'}}/>
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="space-y-4">
          {/* Config summary */}
          <div className="rounded-xl p-5" style={card}>
            <p className="text-xs font-bold mb-3 uppercase tracking-wider" style={{color:'#94A3B8'}}>Current Config</p>
            <div className="space-y-3">
              {[['Model',<span key="m" className="flex items-center gap-1.5 text-xs font-bold px-2.5 py-1 rounded-full" style={{background:`${selModel?.color}12`,color:selModel?.color}}><Cpu size={11}/>{selModel?.label}</span>],
                ['Temperature',<span key="t" className="text-xs font-mono font-bold" style={{color:'#0F172A'}}>{config.temperature.toFixed(2)}</span>],
                ['Max Tokens',<span key="mt" className="text-xs font-mono font-bold" style={{color:'#0F172A'}}>{config.maxTokens.toLocaleString()}</span>],
                ['Status',<span key="s" className="flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded-full" style={enabled?{background:'rgba(16,185,129,0.1)',color:'#059669'}:{background:'#F1F5F9',color:'#94A3B8'}}><span className="w-1.5 h-1.5 rounded-full" style={{background:enabled?'#10B981':'#CBD5E1'}}/>{enabled?'Live':'Disabled'}</span>],
              ].map(([label,value])=>(
                <div key={label} className="flex items-center justify-between">
                  <span className="text-xs" style={{color:'#94A3B8'}}>{label}</span>{value}
                </div>
              ))}
            </div>
          </div>

          {/* Chat preview */}
          <div className="rounded-xl overflow-hidden flex flex-col" style={{...card,minHeight:'380px'}}>
            <div className="flex items-center gap-2.5 px-4 py-3 shrink-0" style={{borderBottom:'1px solid #F1F5F9',background:'#F8FAFC'}}>
              <div className="flex items-center justify-center w-7 h-7 rounded-full" style={{background:'linear-gradient(135deg,#F59E0B,#D97706)'}}>
                <Bot size={14} color="#fff"/>
              </div>
              <div>
                <p className="text-xs font-bold" style={{color:'#0F172A'}}>{config.botName}</p>
                <p className="text-xs" style={{color:'#10B981'}}>● Online</p>
              </div>
              <Sparkles size={14} className="ml-auto" style={{color:'#F59E0B'}}/>
            </div>
            <div className="flex-1 p-4 space-y-3 overflow-y-auto" style={{background:'#FAFAFA'}}>
              {CHAT_PREVIEW.map((msg,idx)=>(
                <div key={idx} className={`flex gap-2 ${msg.role==='user'?'flex-row-reverse':''}`}>
                  <div className="flex items-center justify-center w-6 h-6 rounded-full shrink-0 mt-0.5"
                    style={msg.role==='assistant' ? {background:'linear-gradient(135deg,#F59E0B,#D97706)'} : {background:'#E2E8F0'}}>
                    {msg.role==='assistant'?<Bot size={12} color="#fff"/>:<User size={12} color="#94A3B8"/>}
                  </div>
                  <div className="max-w-[85%] rounded-xl px-3 py-2 text-xs leading-relaxed"
                    style={msg.role==='assistant' ? {background:'#FFFFFF',color:'#334155',border:'1px solid #F1F5F9'} : {background:'rgba(245,158,11,0.1)',color:'#92400E'}}>
                    {msg.text}
                  </div>
                </div>
              ))}
            </div>
            <div className="p-3 shrink-0" style={{borderTop:'1px solid #F1F5F9',background:'#F8FAFC'}}>
              <div className="flex items-center gap-2 px-3 py-2 rounded-xl" style={{background:'#FFFFFF',border:'1px solid #E2E8F0'}}>
                <input type="text" placeholder="Preview message..." className="flex-1 text-xs bg-transparent outline-none" style={{color:'#0F172A'}} disabled={!enabled}/>
                <button className="flex items-center justify-center w-6 h-6 rounded-lg cursor-pointer shrink-0" style={{background:enabled?'#F59E0B':'#F1F5F9',color:enabled?'#fff':'#CBD5E1'}}>
                  <Send size={12}/>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
