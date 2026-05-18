'use client'

import { useEffect } from 'react'

export default function GlobalError({ error, reset }) {
  useEffect(() => {
    console.error('Application error:', error)
  }, [error])

  const isNetworkError =
    error?.message?.toLowerCase().includes('fetch') ||
    error?.message?.toLowerCase().includes('network') ||
    error?.message?.toLowerCase().includes('failed') ||
    error?.message?.toLowerCase().includes('connection')

  return (
    <div className="min-h-screen flex items-center justify-center p-6"
      style={{ background: 'linear-gradient(135deg, #F8FAFC 0%, #EEF2FF 50%, #F8FAFC 100%)' }}>
      <div className="text-center max-w-md w-full">

        {/* Icon with pulsing ring */}
        <div className="relative flex items-center justify-center mb-8">
          <div className="absolute w-28 h-28 rounded-full animate-ping opacity-10"
            style={{ background: isNetworkError ? '#DC2626' : '#1A32D8' }} />
          <div className="relative flex items-center justify-center w-20 h-20 rounded-[24px]"
            style={{
              background: isNetworkError
                ? 'rgba(220,38,38,0.08)'
                : 'linear-gradient(135deg, rgba(26,50,216,0.08) 0%, rgba(129,140,248,0.08) 100%)',
              border: `1px solid ${isNetworkError ? 'rgba(220,38,38,0.2)' : 'rgba(26,50,216,0.15)'}`,
              boxShadow: `0 8px 32px ${isNetworkError ? 'rgba(220,38,38,0.1)' : 'rgba(26,50,216,0.1)'}`,
            }}>
            {isNetworkError ? (
              // Wifi-off style icon
              <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#DC2626" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="1" y1="1" x2="23" y2="23" />
                <path d="M16.72 11.06A10.94 10.94 0 0 1 19 12.55" />
                <path d="M5 12.55a10.94 10.94 0 0 1 5.17-2.39" />
                <path d="M10.71 5.05A16 16 0 0 1 22.56 9" />
                <path d="M1.42 9a15.91 15.91 0 0 1 4.7-2.88" />
                <path d="M8.53 16.11a6 6 0 0 1 6.95 0" />
                <line x1="12" y1="20" x2="12.01" y2="20" />
              </svg>
            ) : (
              <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="url(#errGrad)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <defs>
                  <linearGradient id="errGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#1A32D8" />
                    <stop offset="100%" stopColor="#818CF8" />
                  </linearGradient>
                </defs>
                <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
            )}
          </div>
        </div>

        {/* Message */}
        <h1 className="text-2xl font-bold text-slate-800 mb-2">
          {isNetworkError ? 'Connection lost' : 'Something went wrong'}
        </h1>
        <p className="text-[14px] font-medium text-slate-400 mb-2 leading-relaxed">
          {isNetworkError
            ? 'Unable to reach the server. Please check your internet connection or try again later.'
            : 'An unexpected error occurred. Our team has been notified.'}
        </p>

        {/* Error detail (dev-friendly) */}
        {error?.message && (
          <p className="text-[12px] font-mono px-4 py-2 rounded-xl mb-8 text-left break-all"
            style={{ background: 'rgba(15,23,42,0.04)', color: '#64748B', border: '1px solid #E2E8F0' }}>
            {error.message}
          </p>
        )}

        {/* Actions */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <button onClick={reset}
            className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-[14px] font-bold text-white transition-all"
            style={{
              background: 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)',
              boxShadow: '0 4px 12px rgba(26,50,216,0.25)',
            }}>
            Try Again
          </button>
          <a href="/companies"
            className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-[14px] font-bold text-slate-600 transition-colors hover:bg-slate-100"
            style={{ background: '#F1F5F9', border: '1px solid #E2E8F0' }}>
            Back to Dashboard
          </a>
        </div>
      </div>
    </div>
  )
}
