import NextAuth from 'next-auth'
import Credentials from 'next-auth/providers/credentials'
import { loginRaw } from './service/authService'

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Credentials({
      credentials: {
        email:    { label: 'Email',    type: 'email' },
        password: { label: 'Password', type: 'password' },
      },
      async authorize(credentials) {
        if (!credentials?.email || !credentials?.password) return null

        let res, payload
        try {
          ;({ res, payload } = await loginRaw({
            email:    credentials.email,
            password: credentials.password,
          }))
        } catch {
          throw new Error('Unable to reach the server. Please try again.')
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

        // Response shape: { message, status, code, payload: { username, firstname, lastname, email, ... }, token }
        const user = payload?.payload ?? payload?.user ?? payload?.data ?? payload ?? {}
        const fullName = [user.firstname, user.lastname].filter(Boolean).join(' ').trim()
        return {
          id:          String(user.id    ?? user.userId   ?? user.username ?? user.email ?? '1'),
          name:        user.name         ?? user.fullName ?? (fullName || null) ?? user.username ?? String(credentials.email).split('@')[0],
          email:       user.email        ?? user.username ?? String(credentials.email),
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
