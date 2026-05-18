import { NextResponse } from 'next/server'
import { getToken } from 'next-auth/jwt'

const PROTECTED_PREFIXES = ['/dashboard', '/companies']
const PUBLIC_PATHS       = ['/login']

export default async function proxy(request) {
  const { pathname } = request.nextUrl

  // Read the JWT from the session cookie — Edge-compatible, no Node.js APIs needed.
  const token = await getToken({
    req:    request,
    secret: process.env.AUTH_SECRET,
  })

  const isLoggedIn  = !!token
  const isProtected = PROTECTED_PREFIXES.some((p) => pathname.startsWith(p))
  const isPublic    = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'))

  // Unauthenticated user hits a protected route → redirect to login
  if (isProtected && !isLoggedIn) {
    const url = new URL('/login', request.nextUrl.origin)
    url.searchParams.set('callbackUrl', pathname)
    return NextResponse.redirect(url)
  }

  // Authenticated user hits the login page → redirect to dashboard
  if (isPublic && isLoggedIn) {
    return NextResponse.redirect(new URL('/dashboard', request.nextUrl.origin))
  }

  return NextResponse.next()
}

export const config = {
  // Skip Next.js internals, auth API routes, and static files.
  matcher: ['/((?!api/auth|_next/static|_next/image|favicon\\.ico).*)'],
}
