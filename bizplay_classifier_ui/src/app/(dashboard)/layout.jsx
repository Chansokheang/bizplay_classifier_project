import { auth } from '../../auth'
import SessionProvider from '../../components/SessionProvider'
import Sidebar from '../../components/Sidebar'
import Breadcrumbs from '../../components/Breadcrumbs'

export const metadata = {
  title: 'BizPlay Classifier',
  description: 'Manage expense classification workflows for your company.',
}

export default async function DashboardLayout({ children }) {
  const session = await auth()

  return (
    <SessionProvider session={session}>
      <div
        className="flex h-screen overflow-hidden"
        style={{ background: 'linear-gradient(180deg, #F8FAFC 0%, #EEF2FF 45%, #F8FAFC 100%)' }}
      >
        <Sidebar />
        <main className="flex-1 overflow-y-scroll" style={{ paddingLeft: '30px', paddingRight: '30px' }}>
          <div className="w-full max-w-7xl mx-auto">
            <Breadcrumbs />
            {children}
          </div>
        </main>
      </div>
    </SessionProvider>
  )
}
