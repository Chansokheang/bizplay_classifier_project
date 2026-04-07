import Link from 'next/link'

export default function NotFound() {
  return (
    <div className="min-h-screen flex items-center justify-center p-6"
      style={{ background: 'linear-gradient(135deg, #F8FAFC 0%, #EEF2FF 50%, #F8FAFC 100%)' }}>
      <div className="text-center max-w-md w-full">

        {/* Animated 404 number */}
        <div className="relative mb-8 select-none">
          <p className="text-[120px] font-black leading-none tracking-tighter"
            style={{
              background: 'linear-gradient(135deg, #1A32D8 0%, #818CF8 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              backgroundClip: 'text',
              opacity: 0.15,
            }}>
            404
          </p>
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="flex items-center justify-center w-20 h-20 rounded-[24px]"
              style={{
                background: 'linear-gradient(135deg, rgba(26,50,216,0.1) 0%, rgba(129,140,248,0.1) 100%)',
                border: '1px solid rgba(26,50,216,0.15)',
                boxShadow: '0 8px 32px rgba(26,50,216,0.1)',
              }}>
              <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="url(#grad)" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <defs>
                  <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#1A32D8" />
                    <stop offset="100%" stopColor="#818CF8" />
                  </linearGradient>
                </defs>
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
          </div>
        </div>

        {/* Message */}
        <h1 className="text-2xl font-bold text-slate-800 mb-2">Page not found</h1>
        <p className="text-[14px] font-medium text-slate-400 mb-8 leading-relaxed">
          The page you're looking for doesn't exist or may have been moved.
        </p>

        {/* Actions */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link href="/companies"
            className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-[14px] font-bold text-white transition-all"
            style={{
              background: 'linear-gradient(180deg, #1A32D8 0%, #1529AB 100%)',
              boxShadow: '0 4px 12px rgba(26,50,216,0.25)',
            }}>
            Go to Dashboard
          </Link>
          <button onClick={() => window.history.back()}
            className="w-full sm:w-auto flex items-center justify-center gap-2 px-6 py-3 rounded-xl text-[14px] font-bold text-slate-600 transition-colors hover:bg-slate-100"
            style={{ background: '#F1F5F9', border: '1px solid #E2E8F0' }}>
            Go Back
          </button>
        </div>
      </div>
    </div>
  )
}
