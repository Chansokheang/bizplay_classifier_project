import { redirect } from 'next/navigation'

export default function CompanyPage({ params }) {
  redirect(`/companies/${params.id}/transactions`)
}
