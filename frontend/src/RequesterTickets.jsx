import { useEffect, useRef, useState } from 'react'
import { getCurrentUser } from './authApi.js'
import { createTicket, getTicket, listTickets } from './ticketApi.js'
import { TicketCard, TicketRows } from './TicketViews.jsx'

const listPath = '#/tickets'
const newPath = '#/tickets/new'
const priorities = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }

function ticketError(error, ticketId) {
  if (error.status === 403) return 'You do not have permission to complete this request.'
  if (error.status === 404) {
    return ticketId
      ? 'This ticket could not be found or you do not have access to it.'
      : 'The ticket API is unavailable. Restart the backend and try again.'
  }
  return 'Could not reach the ticket service. Try again.'
}

function useTicketResource(id, onExpired) {
  const [state, setState] = useState({ loading: true })

  useEffect(() => {
    const controller = new AbortController()
    const load = id ? getTicket(id, controller.signal) : listTickets(controller.signal)
    load.then((data) => {
      if (!controller.signal.aborted) setState({ data })
    }).catch((error) => {
      if (controller.signal.aborted) return
      if (error.status === 401) onExpired()
      else setState({ error: ticketError(error, id) })
    })
    return () => controller.abort()
  }, [id, onExpired])

  return state
}

function TicketList({ onExpired }) {
  const { loading, error, data } = useTicketResource(null, onExpired)
  return (
    <section aria-labelledby="tickets-heading">
      <div className="ticket-heading">
        <h2 id="tickets-heading">My Tickets</h2>
        <a className="primary-link" href={newPath}>New Ticket</a>
      </div>
      {loading && <p role="status">Loading tickets…</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {data?.length === 0 && <p className="ticket-empty">You haven’t created a ticket yet.</p>}
      {data?.length > 0 && <TicketRows tickets={data} listPath={listPath} />}
    </section>
  )
}

function TicketDetails({ id, onExpired }) {
  const { loading, error, data: ticket } = useTicketResource(id, onExpired)
  return (
    <section aria-labelledby="details-heading">
      <a className="back-link" href={listPath}>← My Tickets</a>
      <h2 id="details-heading">Ticket Details</h2>
      {loading && <p role="status">Loading ticket…</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {ticket && <TicketCard ticket={ticket} />}
    </section>
  )
}

function NewTicket({ onExpired }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [fields, setFields] = useState({})
  const pending = useRef(null)

  useEffect(() => () => pending.current?.abort(), [])

  async function submit(event) {
    event.preventDefault()
    if (pending.current) return
    const data = Object.fromEntries(new FormData(event.currentTarget))
    const controller = new AbortController()
    pending.current = controller
    setBusy(true)
    setFields({})
    setMessage('')
    try {
      const ticket = await createTicket(data, controller.signal)
      if (!controller.signal.aborted) window.location.hash = `${listPath}/${ticket.id}`
    } catch (error) {
      if (controller.signal.aborted) return
      let status = error.status
      if (status === 403) {
        status = await getCurrentUser(controller.signal).then(() => 403, (sessionError) => sessionError.status)
      }
      if (controller.signal.aborted) return
      if (status === 401) {
        onExpired()
      } else if (status === 400) {
        setFields(error.fields ?? {})
        setMessage(error.message)
      } else {
        setMessage(ticketError(error))
      }
    } finally {
      pending.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  return (
    <section aria-labelledby="new-heading">
      <a className="back-link" href={listPath}>← My Tickets</a>
      <h2 id="new-heading">New Ticket</h2>
      <form className="ticket-form" onSubmit={submit} noValidate>
        {message && <p className="error" role="alert">{message}</p>}
        <label htmlFor="ticket-title">Title</label>
        <input id="ticket-title" name="title" maxLength={200} required
          aria-invalid={Boolean(fields.title)} aria-describedby={fields.title ? 'title-error' : undefined} />
        {fields.title && <span className="field-error" id="title-error">{fields.title}</span>}
        <label htmlFor="ticket-description">Description</label>
        <textarea id="ticket-description" name="description" rows={6} maxLength={10000} required
          aria-invalid={Boolean(fields.description)} aria-describedby={fields.description ? 'description-error' : undefined} />
        {fields.description && <span className="field-error" id="description-error">{fields.description}</span>}
        <label htmlFor="ticket-priority">Priority</label>
        <select id="ticket-priority" name="priority" defaultValue="" required
          aria-invalid={Boolean(fields.priority)} aria-describedby={fields.priority ? 'priority-error' : undefined}>
          <option value="">Select a priority</option>
          {Object.entries(priorities).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
        {fields.priority && <span className="field-error" id="priority-error">{fields.priority}</span>}
        <button type="submit" disabled={busy}>{busy ? 'Creating ticket…' : 'Create Ticket'}</button>
      </form>
    </section>
  )
}

export default function RequesterTickets({ onExpired }) {
  const [path, setPath] = useState(() => window.location.hash)

  useEffect(() => {
    function onHashChange() {
      setPath(window.location.hash)
    }
    window.addEventListener('hashchange', onHashChange)
    onHashChange()
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  if (path === newPath) return <NewTicket onExpired={onExpired} />
  if (!path || path === listPath) return <TicketList onExpired={onExpired} />
  const ticket = /^#\/tickets\/([1-9][0-9]*)$/.exec(path)
  if (ticket) return <TicketDetails key={ticket[1]} id={ticket[1]} onExpired={onExpired} />
  return (
    <section>
      <a className="back-link" href={listPath}>← My Tickets</a>
      <h2>Page not found</h2>
    </section>
  )
}
