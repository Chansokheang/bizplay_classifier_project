import NextAuth from 'next-auth'
import Credentials from 'next-auth/providers/credentials'

const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080/api/v1'

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Credentials({
      credentials: {
        email:    { label: 'Email',    type: 'email' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) return null

        let res
        try {
          res = await fetch(`${BACKEND_URL}/auths/login`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              accept: 'application/json',
            },
            body: JSON.stringify({
              email:    credentials.email,
              password: credentials.password,
            }),
          })
        } catch {
          throw new Error('Unable to reach the server. Please try again.')
        }

        let payload = null
        const ct = res.headers.get('content-type') ?? ''
        if (ct.includes('application/json')) {
          payload = await res.json().catch(() => null)
        } else {
          const text = await res.text().catch(() => '')
          if (text.trim()) {
            try { payload = JSON.parse(text) } catch { payload = { message: text } }
          }
        }

        if (!res.ok) {
          const msg =
            payload?.detail ??
            payload?.message ??
            payload?.error ??
            (Array.isArray(payload?.errors) ? payload.errors[0]?.message : null) ??
            'Invalid credentials.'
          throw new Error(msg)
        }

        // Normalise the user object — adjust field names to match your API response.
        const user = payload?.user ?? payload?.data ?? payload ?? {}
        return {
          id:          String(user.id    ?? user.userId    ?? '1'),
          name:        user.name         ?? user.fullName  ?? String(credentials.email).split('@')[0],
          email:       user.email        ?? String(credentials.email),
          accessToken: payload?.token    ?? payload?.accessToken ?? payload?.access_token ?? null,
        }
      },
    }),
  ],

  callbacks: {
    jwt({ token, user }) {
      // Persist accessToken and id into the JWT on first sign-in.
      if (user) {
        token.id          = user.id
        token.accessToken = user.accessToken
      }
      return token
    },
    session({ session, token }) {
      // Expose id and accessToken on the client-side session.
      if (session.user) {
        session.user.id = token.id
      }
      session.accessToken = token.accessToken
      return session
    },
  },

  pages: {
    signIn: '/login',
    error:  '/login',  // Auth errors redirect back to login with ?error=...
  },

  session: { strategy: 'jwt' },
})
