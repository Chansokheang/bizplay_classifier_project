'use client'

import { useRef, useState } from 'react'
import { useParams } from 'next/navigation'
import { useSession } from 'next-auth/react'
import {
  Upload,
  FileSpreadsheet,
  Brain,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Info,
  Database,
} from 'lucide-react'
import { uploadTrainingFile, trainFromFile } from '../../../../../service/trainingService'

const card = { background: '#FFFFFF', border: '1px solid #E2E8F0', boxShadow: '0 1px 3px rgba(0,0,0,0.06)' }

export default function TrainingPage() {
  const { id: companyId } = useParams()
  const { data: session } = useSession()
  const token = session?.accessToken

  const fileRef = useRef(null)
  const [file, setFile]             = useState(null)
  const [sheetName, setSheetName]   = useState('')
  const [storeFile, setStoreFile]   = useState(false)
  const [status, setStatus]         = useState(null)   // null | 'uploading' | 'training' | 'success' | 'error'
  const [errorMsg, setErrorMsg]     = useState('')

  const isRunning = status === 'uploading' || status === 'training'

  const handleFileChange = (e) => {
    const f = e.target.files[0]
    if (f) { setFile(f); setStatus(null); setErrorMsg('') }
    e.target.value = ''
  }

  const handleRun = async () => {
    if (!file) return
    setStatus(null)
    setErrorMsg('')

    if (storeFile) {
      setStatus('uploading')
      try {
        await uploadTrainingFile(file, companyId, token)
      } catch (err) {
        setStatus('error')
        setErrorMsg(`File upload failed: ${err.message}`)
        return
      }
    }

    setStatus('training')
    try {
      await trainFromFile(file, companyId, sheetName || null, token)
      setStatus('success')
    } catch (err) {
      setStatus('error')
      setErrorMsg(`Training failed: ${err.message}`)
    }
  }

  return (
    <div className="py-8 animate-fade-in">
      <div className="mb-8">
        <h1 className="text-2xl font-bold" style={{ color: '#0F172A' }}>Training</h1>
        <p className="mt-1 text-sm" style={{ color: '#94A3B8' }}>
          Upload a labelled dataset to train classification rules.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* ── Main card ── */}
        <div className="lg:col-span-2 space-y-4">
          <div className="rounded-xl overflow-hidden" style={card}>
            <div className="px-6 py-4" style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
              <div className="flex items-center gap-2">
                <Brain size={15} color="#1A32D8" />
                <h2 className="font-semibold text-sm" style={{ color: '#0F172A' }}>Train Rules</h2>
              </div>
              <p className="text-xs mt-0.5" style={{ color: '#94A3B8' }}>
                Select a labelled Excel or CSV file to update your classification rules.
              </p>
            </div>

            <div className="p-6 space-y-5">
              {/* File picker */}
              <div>
                <label className="text-xs font-semibold block mb-2" style={{ color: '#64748B' }}>Training File</label>
                <input ref={fileRef} type="file" accept=".xlsx,.xls,.csv" className="hidden" onChange={handleFileChange} />
                {file ? (
                  <div
                    className="flex items-center justify-between px-4 py-3 rounded-xl"
                    style={{ background: 'rgba(26,50,216,0.04)', border: '1.5px solid rgba(26,50,216,0.2)' }}
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex items-center justify-center w-9 h-9 rounded-lg" style={{ background: 'rgba(59,130,246,0.08)' }}>
                        <FileSpreadsheet size={16} color="#3B82F6" />
                      </div>
                      <div>
                        <p className="text-sm font-semibold" style={{ color: '#0F172A' }}>{file.name}</p>
                        <p className="text-xs" style={{ color: '#94A3B8' }}>{(file.size / 1024).toFixed(1)} KB</p>
                      </div>
                    </div>
                    <button
                      onClick={() => { setFile(null); setStatus(null); setErrorMsg('') }}
                      className="text-xs font-medium cursor-pointer"
                      style={{ color: '#EF4444' }}
                    >
                      Remove
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={() => fileRef.current?.click()}
                    className="w-full flex flex-col items-center justify-center gap-2 py-8 rounded-xl border-2 border-dashed cursor-pointer"
                    style={{ borderColor: '#E2E8F0', background: '#F8FAFC', color: '#94A3B8' }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = '#1A32D8'; e.currentTarget.style.background = 'rgba(26,50,216,0.02)' }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = '#E2E8F0'; e.currentTarget.style.background = '#F8FAFC' }}
                  >
                    <Upload size={22} />
                    <div className="text-center">
                      <p className="text-sm font-semibold" style={{ color: '#64748B' }}>Click to select file</p>
                      <p className="text-xs mt-0.5">Accepts .xlsx, .xls, .csv</p>
                    </div>
                  </button>
                )}
              </div>

              {/* Sheet name */}
              <div>
                <label className="text-xs font-semibold block mb-1.5" style={{ color: '#64748B' }}>
                  Sheet Name <span style={{ color: '#CBD5E1', fontWeight: 400 }}>(optional)</span>
                </label>
                <input
                  type="text"
                  value={sheetName}
                  onChange={(e) => setSheetName(e.target.value)}
                  placeholder="e.g. Sheet1"
                  className="w-full px-3.5 py-2.5 rounded-xl text-sm outline-none"
                  style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', color: '#0F172A' }}
                  onFocus={(e) => { e.target.style.borderColor = '#1A32D8'; e.target.style.background = '#FFFFFF' }}
                  onBlur={(e) => { e.target.style.borderColor = '#E2E8F0'; e.target.style.background = '#F8FAFC' }}
                />
              </div>

              {/* Store file toggle */}
              <div
                className="flex items-center justify-between px-4 py-3 rounded-xl cursor-pointer"
                style={{ background: '#F8FAFC', border: '1px solid #F1F5F9' }}
                onClick={() => setStoreFile((v) => !v)}
                onMouseEnter={(e) => { e.currentTarget.style.background = '#F1F5F9' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#F8FAFC' }}
              >
                <div>
                  <p className="text-sm font-semibold" style={{ color: '#0F172A' }}>Also store file</p>
                  <p className="text-xs mt-0.5" style={{ color: '#94A3B8' }}>Save the dataset to file storage before training</p>
                </div>
                <div
                  className="w-9 h-5 rounded-full relative transition-colors shrink-0 ml-4"
                  style={{ background: storeFile ? '#1A32D8' : '#E2E8F0' }}
                >
                  <div
                    className="absolute top-0.5 w-4 h-4 rounded-full bg-white shadow transition-all"
                    style={{ left: storeFile ? '18px' : '2px' }}
                  />
                </div>
              </div>

              {/* Run button */}
              <button
                onClick={handleRun}
                disabled={!file || isRunning}
                className="w-full flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-bold cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ background: '#1A32D8', color: '#FFFFFF' }}
                onMouseEnter={(e) => { if (file && !isRunning) e.currentTarget.style.background = '#1529b8' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = '#1A32D8' }}
              >
                {isRunning
                  ? <><Loader2 size={15} className="animate-spin" />{status === 'uploading' ? 'Storing file…' : 'Training…'}</>
                  : <><Brain size={15} />Run Training</>
                }
              </button>

              {/* Feedback */}
              {status === 'success' && (
                <div className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-xs font-medium" style={{ background: 'rgba(16,185,129,0.08)', color: '#059669' }}>
                  <CheckCircle2 size={13} />
                  Training complete. Classification rules have been updated.
                </div>
              )}
              {status === 'error' && (
                <div className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-xs font-medium" style={{ background: 'rgba(239,68,68,0.08)', color: '#DC2626' }}>
                  <AlertCircle size={13} />
                  {errorMsg}
                </div>
              )}
            </div>
          </div>

          {/* Info note */}
          <div className="flex gap-3 px-4 py-3.5 rounded-xl" style={{ background: 'rgba(26,50,216,0.04)', border: '1px solid rgba(26,50,216,0.12)' }}>
            <Info size={15} color="#1A32D8" className="shrink-0 mt-0.5" />
            <p className="text-xs leading-relaxed" style={{ color: '#334155' }}>
              Training updates your classification rules from the labelled dataset.
              To improve the AI assistant's detection prompt, go to the <span className="font-semibold">Chatbot</span> page and use <span className="font-semibold">Enhance Prompt</span>.
            </p>
          </div>
        </div>

        {/* ── Right: what happens ── */}
        <div className="rounded-xl p-5 space-y-4 h-fit" style={card}>
          <p className="text-xs font-bold uppercase tracking-wider" style={{ color: '#94A3B8' }}>What happens</p>
          {[
            { Icon: Database, label: storeFile ? 'Store file' : 'Store file (skipped)', desc: 'POST /storage/training-files/upload', active: status === 'uploading', done: status === 'training' || status === 'success', dimmed: !storeFile },
            { Icon: Brain,    label: 'Train rules', desc: 'POST /data/train', active: status === 'training', done: status === 'success' },
          ].map(({ Icon, label, desc, active, done, dimmed }) => (
            <div key={label} className="flex items-start gap-3">
              <div
                className="flex items-center justify-center w-6 h-6 rounded-full shrink-0 mt-0.5"
                style={{
                  background: done ? 'rgba(16,185,129,0.1)' : active ? 'rgba(26,50,216,0.1)' : 'rgba(203,213,225,0.2)',
                  color: done ? '#10B981' : active ? '#1A32D8' : '#CBD5E1',
                }}
              >
                {done
                  ? <CheckCircle2 size={13} />
                  : active
                    ? <Loader2 size={13} className="animate-spin" />
                    : <Icon size={13} />
                }
              </div>
              <div>
                <p className="text-xs font-semibold" style={{ color: dimmed ? '#CBD5E1' : active || done ? '#0F172A' : '#94A3B8' }}>{label}</p>
                <p className="text-xs mt-0.5 font-mono" style={{ color: '#CBD5E1' }}>{desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
