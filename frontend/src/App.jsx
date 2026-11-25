import { useCallback, useEffect, useState } from 'react'
import { getCurrentUser, signIn, signOut } from './authApi.js'
import RequesterTickets from './RequesterTickets.jsx'
import WorkflowTickets from './WorkflowTickets.jsx'

const forbidden = 'You do not have permission to complete this request.'
const expired = 'Your session has expired. Sign in again.'
const unavailable = 'Could not reach the service. Try again.'

function App() {
  const [user, setUser] = useState(undefined)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const handleExpired = useCallback(() => {
    setUser(null)
    setMessage(expired)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    getCurrentUser(controller.signal).then(setUser).catch((error) => {
      if (controller.signal.aborted) return
      setUser(null)
      if (error.status !== 401) {
        setMessage(error.status === 403 ? forbidden : unavailable)
      }
    })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!user || busy) return
    const controller = new AbortController()

    function checkSession() {
      getCurrentUser(controller.signal).catch((error) => {
        if (controller.signal.aborted) return
        if (error.status === 401) {
          setUser(null)
          setMessage(expired)
        } else if (error.status === 403) {
          setMessage(forbidden)
        }
      })
    }

    window.addEventListener('focus', checkSession)
    return () => {
      controller.abort()
      window.removeEventListener('focus', checkSession)
    }
  }, [user, busy])

  async function handleLogin(event) {
    event.preventDefault()
    const form = event.currentTarget
    const formData = new FormData(form)
    setBusy(true)
    setMessage('')
    try {
      await signIn(formData.get('email'), formData.get('password'))
      setUser(await getCurrentUser())
      form.reset()
    } catch (error) {
      if (error.status === 401) {
        setMessage('Email or password is incorrect.')
      } else {
        setMessage(error.status === 403 ? forbidden : unavailable)
      }
    } finally {
      setBusy(false)
    }
  }

  async function handleLogout() {
    setBusy(true)
    setMessage('')
    try {
      await signOut()
      setUser(null)
    } catch (error) {
      if (error.status === 403) {
        try {
          await getCurrentUser()
          setMessage(forbidden)
        } catch (sessionError) {
          if (sessionError.status === 401) {
            setUser(null)
            setMessage(expired)
          } else {
            setMessage(sessionError.status === 403 ? forbidden : unavailable)
          }
        }
      } else if (error.status === 401) {
        setUser(null)
        setMessage(expired)
      } else {
        setMessage(unavailable)
      }
    } finally {
      setBusy(false)
    }
  }

  if (user) {
    return (
      <main className="workspace">
        <header className="workspace-header">
          <div>
            <h1>Helpdesk Ticket System</h1>
            <p>{user.email}</p>
          </div>
          <button type="button" onClick={handleLogout} disabled={busy}>{busy ? 'Signing out…' : 'Sign out'}</button>
        </header>
        {message && <p className="error" role="alert">{message}</p>}
        {user.role === 'REQUESTER'
          ? <RequesterTickets key={user.id} onExpired={handleExpired} />
          : <WorkflowTickets key={user.id} role={user.role} onExpired={handleExpired} />}
      </main>
    )
  }

  return (
    <main>
      <h1>Helpdesk Ticket System</h1>
      {user === undefined && <p role="status">Checking your session…</p>}
      {user === null && (
        <form onSubmit={handleLogin}>
          <h2>Sign in</h2>
          <label htmlFor="email">Email</label>
          <input id="email" name="email" type="email" autoComplete="username" required />
          <label htmlFor="password">Password</label>
          <input id="password" name="password" type="password" autoComplete="current-password" required />
          <button type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        </form>
      )}
      {message && <p className="error" role="alert">{message}</p>}
    </main>
  )
}

export default App
