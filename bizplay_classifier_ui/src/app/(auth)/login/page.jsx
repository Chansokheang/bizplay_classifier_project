'use client'

import { useState, Suspense } from 'react'
import { signIn } from 'next-auth/react'
import { useSearchParams } from 'next/navigation'
import { Eye, EyeOff, Mail, Lock } from 'lucide-react'

const ERROR_MESSAGES = {
  CredentialsSignin: 'Invalid email or password.',
  Default:           'Something went wrong. Please try again.',
}

function LoginForm() {
  const searchParams  = useSearchParams()
  const callbackUrl   = searchParams.get('callbackUrl') ?? '/dashboard'
  const authError     = searchParams.get('error')

  const [email,        setEmail]        = useState('')
  const [password,     setPassword]     = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error,        setError]        = useState(
    authError ? (ERROR_MESSAGES[authError] ?? ERROR_MESSAGES.Default) : ''
  )

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (!email.trim() || !password.trim()) {
      setError('Email and password are required.')
      return
    }

    setIsSubmitting(true)
    try {
      const result = await signIn('credentials', {
        email,
        password,
        redirect: false,
      })

      if (result?.error) {
        setError(ERROR_MESSAGES[result.error] ?? result.error)
        return
      }

      window.location.href = callbackUrl
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen w-full flex items-center justify-center relative overflow-hidden" style={{ background: 'radial-gradient(circle at 20% 20%, rgba(26,50,216,0.12), transparent 55%), radial-gradient(circle at 80% 10%, rgba(15,23,42,0.10), transparent 55%), linear-gradient(180deg, #EEF2FF 0%, #E5E7EB 60%, #F8FAFC 100%)' }}>
      <div className="w-full max-w-[960px] h-[600px] bg-white rounded-[24px] shadow-sm flex p-2 relative z-10">
        <div className="w-[45%] h-full bg-[#EEF2FF] rounded-[20px] relative flex flex-col items-center justify-center overflow-hidden">
          <div className="absolute top-6 left-6 text-[16px] font-bold" style={{ color: '#1A32D8' }}>Bizplay</div>
          <div className="w-[320px] h-[320px] relative mt-4">
            <svg width="100%" height="100%" viewBox="0 0 400 400" xmlns="http://www.w3.org/2000/svg">
              <g transform="translate(0, 10)">
                <polygon points="40,110 90,85 130,105 80,130" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round"/>
                <polygon points="40,110 80,130 80,135 40,115" fill="#cbd5e1" stroke="#1e293b" strokeWidth="2.5"/>
                <polygon points="80,130 130,105 130,110 80,135" fill="#94a3b8" stroke="#1e293b" strokeWidth="2.5"/>
                <polygon points="40,110 55,102.5 95,122.5 80,130" fill="#8b5cf6" stroke="#1e293b" strokeWidth="2.5"/>
                <line x1="65" y1="110" x2="105" y2="90" stroke="#1e293b" strokeWidth="2.5"/>
                <circle cx="60" cy="112" r="3" fill="#22c55e" stroke="#1e293b" strokeWidth="2.5"/>
                <line x1="75" y1="115" x2="115" y2="95" stroke="#1e293b" strokeWidth="2.5"/>
                <circle cx="70" cy="117" r="3" fill="#22c55e" stroke="#1e293b" strokeWidth="2.5"/>
              </g>
              <path d="M 165 247 L 110 290" stroke="#94a3b8" strokeWidth="2" strokeDasharray="4,4"/>
              <path d="M 200 265 L 200 340" stroke="#94a3b8" strokeWidth="2" strokeDasharray="4,4"/>
              <path d="M 235 247 L 290 290" stroke="#94a3b8" strokeWidth="2" strokeDasharray="4,4"/>
              <polygon points="200,160 270,195 200,230 130,195" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="130,195 200,230 200,300 130,265" fill="#3b82f6" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="200,230 270,195 270,265 200,300" fill="#60a5fa" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="165" cy="247" r="16" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="165" cy="247" r="5" fill="#3b82f6" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="165" cy="225" r="3.5" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="165" cy="269" r="3.5" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="143" cy="247" r="3.5" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="187" cy="247" r="3.5" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="220,240 230,235 230,255 220,260" fill="#22c55e" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="235,230 245,225 245,265 235,270" fill="#facc15" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="250,220 260,215 260,275 250,280" fill="#ef4444" stroke="#1e293b" strokeWidth="2.5"/>
              <ellipse cx="200" cy="195" rx="25" ry="12.5" fill="#94a3b8" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="150,110 250,110 220,195 180,195" fill="#f8fafc" stroke="#1e293b" strokeWidth="2.5"/>
              <ellipse cx="200" cy="110" rx="50" ry="25" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <ellipse cx="200" cy="112" rx="35" ry="17" fill="#0f172a" stroke="#1e293b" strokeWidth="2.5"/>
              <line x1="200" y1="20" x2="200" y2="30" stroke="#facc15" strokeWidth="2.5" strokeLinecap="round"/>
              <line x1="170" y1="35" x2="175" y2="42" stroke="#facc15" strokeWidth="2.5" strokeLinecap="round"/>
              <line x1="230" y1="35" x2="225" y2="42" stroke="#facc15" strokeWidth="2.5" strokeLinecap="round"/>
              <polygon points="170,55 200,40 230,55 200,70" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="170,55 185,47.5 215,62.5 200,70" fill="#10b981" stroke="#1e293b" strokeWidth="2.5"/>
              <line x1="188" y1="55" x2="195" y2="62" stroke="#ffffff" strokeWidth="2" strokeLinecap="round"/>
              <line x1="195" y1="55" x2="188" y2="62" stroke="#ffffff" strokeWidth="2" strokeLinecap="round"/>
              <line x1="180" y1="60" x2="210" y2="45" stroke="#1e293b" strokeWidth="2"/>
              <line x1="190" y1="65" x2="220" y2="50" stroke="#1e293b" strokeWidth="2"/>
              <circle cx="110" cy="275" r="9" fill="#3b82f6" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="110,290 140,305 110,320 80,305" fill="#bfdbfe" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="80,305 110,320 110,350 80,335" fill="#3b82f6" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="110,320 140,305 140,335 110,350" fill="#2563eb" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="85,302 105,292 105,285 85,295" fill="#2563eb" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="200" cy="325" r="9" fill="#f97316" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="200,340 230,355 200,370 170,355" fill="#fed7aa" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="170,355 200,370 200,400 170,385" fill="#f97316" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="200,370 230,355 230,385 200,400" fill="#ea580c" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="175,352 195,342 195,335 175,345" fill="#ea580c" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="290" cy="275" r="9" fill="#a855f7" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="290,290 320,305 290,320 260,305" fill="#e9d5ff" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="260,305 290,320 290,350 260,335" fill="#a855f7" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="290,320 320,305 320,335 290,350" fill="#9333ea" stroke="#1e293b" strokeWidth="2.5"/>
              <polygon points="265,302 285,292 285,285 265,295" fill="#9333ea" stroke="#1e293b" strokeWidth="2.5"/>
              <ellipse cx="315" cy="145" rx="18" ry="6" fill="#cbd5e1" opacity="0.6"/>
              <path d="M 290 100 Q 280 100 280 90" stroke="#1e293b" strokeWidth="2.5" fill="none"/>
              <path d="M 340 100 Q 350 100 350 90" stroke="#1e293b" strokeWidth="2.5" fill="none"/>
              <rect x="290" y="70" width="50" height="45" rx="12" fill="#ffffff" stroke="#1e293b" strokeWidth="2.5"/>
              <line x1="315" y1="70" x2="315" y2="45" stroke="#1e293b" strokeWidth="2.5"/>
              <circle cx="315" cy="40" r="6" fill="#facc15" stroke="#1e293b" strokeWidth="2.5"/>
              <rect x="298" y="80" width="34" height="22" rx="4" fill="#1e293b" stroke="#1e293b" strokeWidth="2.5"/>
              <path d="M 303 90 Q 308 85 313 90" stroke="#22c55e" fill="none" strokeWidth="2.5" strokeLinecap="round"/>
              <path d="M 317 90 Q 322 85 327 90" stroke="#22c55e" fill="none" strokeWidth="2.5" strokeLinecap="round"/>
              <path d="M 312 95 Q 315 98 318 95" stroke="#22c55e" fill="none" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </div>
        </div>

        <div className="flex-1 relative flex flex-col justify-center px-12">
          <div className="absolute top-8 right-10 text-[11px] text-gray-400 font-medium">
            Don&apos;t have an account?
            <button type="button" className="text-gray-900 font-bold ml-1 hover:underline">Sign up</button>
          </div>

          <div className="w-full max-w-[340px] mx-auto mt-4">
            <h2 className="text-[34px] font-bold text-gray-900 mb-8 tracking-tight">Sign in</h2>
            <p className="text-[11px] font-bold text-gray-900 mb-3">Continue with email address</p>

            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="relative w-full">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <Mail className="w-[14px] h-[14px] text-gray-500" />
                </div>
                <input
                  id="email"
                  type="email"
                  placeholder="you@bizplay.ai"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-[#F4F5F7] rounded-xl py-3 pl-10 pr-4 text-[13px] font-semibold text-gray-900 outline-none focus:ring-1 transition-shadow"
                  style={{ boxShadow: 'none' }}
                  onFocus={(e) => { e.target.style.boxShadow = '0 0 0 1px #1A32D8' }}
                  onBlur={(e) => { e.target.style.boxShadow = 'none' }}
                />
              </div>

              <div className="relative w-full">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                  <Lock className="w-[14px] h-[14px] text-gray-500" />
                </div>
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-[#F4F5F7] rounded-xl py-3 pl-10 pr-10 text-[13px] font-semibold text-gray-900 outline-none focus:ring-1 transition-shadow"
                  style={{ boxShadow: 'none' }}
                  onFocus={(e) => { e.target.style.boxShadow = '0 0 0 1px #1A32D8' }}
                  onBlur={(e) => { e.target.style.boxShadow = 'none' }}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((s) => !s)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 cursor-pointer text-gray-400"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>

              {error && (
                <div
                  className="rounded-xl px-4 py-3 text-xs"
                  style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#DC2626' }}
                >
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full rounded-full py-3.5 mt-2 text-[13px] font-bold cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed"
                style={{ background: '#1A32D8', color: '#FFFFFF' }}
                onMouseEnter={(e) => { if (!isSubmitting) e.currentTarget.style.background = '#1529B5' }}
                onMouseLeave={(e) => { if (!isSubmitting) e.currentTarget.style.background = '#1A32D8' }}
              >
                {isSubmitting ? 'Signing in…' : 'Login'}
              </button>

              <button type="button" className="text-[11px] font-semibold mt-2" style={{ color: '#1A32D8' }}>
                Reset password
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  )
}
