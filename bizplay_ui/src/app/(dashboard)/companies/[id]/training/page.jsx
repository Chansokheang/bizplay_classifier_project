'use client'

import { useEffect } from 'react'
import { useParams, useRouter } from 'next/navigation'

/** Old Training route lives on Classification Rules. */
export default function TrainingRedirectPage() {
  const { id } = useParams()
  const router = useRouter()

  useEffect(() => {
    if (id) router.replace(`/companies/${id}/rules`)
  }, [id, router])

  return (
    <div className="py-16 text-center text-sm" style={{ color: '#94A3B8' }}>
      Redirecting…
    </div>
  )
}
