import { redirect } from 'next/navigation'

export default async function CompanyPage({ params }) {
  const { id } = await params
  redirect(`/companies/${id}/transactions`)
}
