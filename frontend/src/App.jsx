import { useEffect, useState } from 'react'

function App() {
  const [message, setMessage] = useState('Checking connection…')

  useEffect(() => {
    const controller = new AbortController()

    async function checkConnection() {
      try {
        const response = await fetch('/api/health', { signal: controller.signal })
        if (!response.ok && response.status !== 503) {
          throw new Error(`Unexpected health response (${response.status})`)
        }

        const health = await response.json()
        if (response.ok && health.status === 'UP' && health.database === 'UP') {
          setMessage('Backend and PostgreSQL are connected.')
        } else if (response.status === 503 && health.database === 'DOWN') {
          setMessage('Backend is running. PostgreSQL is unavailable.')
        } else {
          setMessage('The health endpoint returned an unexpected status.')
        }
      } catch (error) {
        if (error.name !== 'AbortError') {
          setMessage('Could not check the backend connection.')
        }
      }
    }

    void checkConnection()
    return () => controller.abort()
  }, [])

  return (
    <main>
      <h1>Helpdesk Ticket System</h1>
      <p role="status">{message}</p>
    </main>
  )
}

export default App
