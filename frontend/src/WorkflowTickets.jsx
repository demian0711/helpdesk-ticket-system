import { useEffect, useRef, useState } from 'react'
import { getCurrentUser, request, requestWithCsrf } from './authApi.js'
import { statuses } from './ticketApi.js'
import { TicketCard, TicketRows } from './TicketViews.jsx'
import TicketDiscussion from './TicketDiscussion.jsx'

const listPath = '#/tickets'
const nextStatuses = { OPEN: 'IN_PROGRESS', IN_PROGRESS: 'RESOLVED' }

function workflowError(error) {
  if (error.status === 403) return 'You do not have permission to complete this request.'
  if (error.status === 404) return 'The ticket or endpoint could not be found, or you do not have access.'
  return error.status ? error.message : 'Could not reach the ticket service. Try again.'
}

function useResource(path, onExpired) {
  const [state, setState] = useState({ loading: true })
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    request(path, { signal: controller.signal })
      .then(response => response.json())
      .then(data => {
        if (!controller.signal.aborted) setState({ data })
      })
      .catch(error => {
        if (controller.signal.aborted) return
        if (error.status === 401) onExpired()
        else setState({ error: workflowError(error) })
      })
    return () => controller.abort()
  }, [path, onExpired, revision])

  return {
    ...state,
    update: data => setState({ data }),
    reload: () => {
      setState({ loading: true })
      setRevision(value => value + 1)
    },
  }
}

function AdminFilters({ agents, status, assigneeId, setStatus, setAssigneeId }) {
  return (
    <div className="ticket-filters">
      <div>
        <label htmlFor="workflow-status-filter">Status</label>
        <select id="workflow-status-filter" value={status} onChange={event => setStatus(event.target.value)}>
          <option value="">All statuses</option>
          {Object.entries(statuses).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </div>
      <div>
        <label htmlFor="workflow-assignee-filter">Assignee</label>
        <select id="workflow-assignee-filter" value={assigneeId} onChange={event => setAssigneeId(event.target.value)}>
          <option value="">All agents</option>
          {agents?.map(agent => <option key={agent.id} value={agent.id}>{agent.email}</option>)}
        </select>
      </div>
    </div>
  )
}

function TicketResults({ path, onExpired }) {
  const { loading, error, data } = useResource(path, onExpired)
  if (loading) return <p role="status">Loading tickets…</p>
  if (error) return <p role="alert" className="error">{error}</p>
  if (data.length === 0) return <p className="ticket-empty">No tickets match this view.</p>
  return <TicketRows tickets={data} listPath={listPath} />
}

function WorkflowList({ path, heading, admin, agents, onExpired }) {
  const [status, setStatus] = useState('')
  const [assigneeId, setAssigneeId] = useState('')
  const filters = new URLSearchParams()
  if (status) filters.set('status', status)
  if (assigneeId) filters.set('assigneeId', assigneeId)
  const listRequest = filters.size ? `${path}?${filters}` : path

  return (
    <section>
      <h2>{heading}</h2>
      {admin && (
        <AdminFilters agents={agents} status={status} assigneeId={assigneeId}
          setStatus={setStatus} setAssigneeId={setAssigneeId} />
      )}
      <TicketResults key={listRequest} path={listRequest} onExpired={onExpired} />
    </section>
  )
}

function WorkflowDetail({ path, heading, admin, agents, onExpired }) {
  const { loading, error, data: ticket, update, reload } = useResource(path, onExpired)
  const [busy, setBusy] = useState(false)
  const [problem, setProblem] = useState(null)
  const pending = useRef(null)
  const next = nextStatuses[ticket?.status]

  useEffect(() => () => pending.current?.abort(), [])

  async function mutate(action, change) {
    if (pending.current) return
    const controller = new AbortController()
    pending.current = controller
    setBusy(true)
    setProblem(null)
    try {
      const response = await requestWithCsrf(`${path}/${action}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...change, version: ticket.version }),
        signal: controller.signal,
      })
      const result = await response.json()
      if (!controller.signal.aborted) update(result)
    } catch (error) {
      let status = error.status
      if (status === 403 && !controller.signal.aborted) {
        status = await getCurrentUser(controller.signal).then(() => 403, sessionError => sessionError.status)
      }
      if (controller.signal.aborted) return
      if (status === 401) onExpired()
      else setProblem({ message: workflowError(error), reload: status === 409 || status === 404 })
    } finally {
      pending.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  return (
    <section>
      <a className="back-link" href={listPath}>← {heading}</a>
      <h2>Ticket Details</h2>
      {loading && <p role="status">Loading ticket…</p>}
      {error && <p role="alert" className="error">{error}</p>}
      {ticket && <TicketCard ticket={ticket} />}
      {problem && <p role="alert" className="error">{problem.message}</p>}
      {problem?.reload && <button type="button" onClick={() => { setProblem(null); reload() }}>Reload ticket</button>}
      {ticket && admin && ticket.status === 'OPEN' && agents?.length === 0 && (
        <p className="ticket-empty">No agents are available for assignment.</p>
      )}
      {ticket && admin && ticket.status === 'OPEN' && agents?.length > 0 && !problem?.reload && (
        <form className="ticket-form workflow-action" key={ticket.version} onSubmit={event => {
          event.preventDefault()
          const assigneeId = new FormData(event.currentTarget).get('assignee')
          if (assigneeId) void mutate('assignee', { assigneeId: Number(assigneeId) })
        }}>
          <label htmlFor="workflow-assignee">Assign to agent</label>
          <select id="workflow-assignee" name="assignee" defaultValue={ticket.assignee?.id ?? ''} required>
            <option value="">Select an agent</option>
            {agents.map(agent => <option key={agent.id} value={agent.id}>{agent.email}</option>)}
          </select>
          <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Save assignment'}</button>
        </form>
      )}
      {ticket && !admin && next && !problem?.reload && (
        <button type="button" className="workflow-action" disabled={busy}
          onClick={() => mutate('status', { status: next })}>
          {busy ? 'Saving…' : next === 'IN_PROGRESS' ? 'Start work' : 'Resolve ticket'}
        </button>
      )}
      {ticket && <TicketDiscussion id={ticket.id} version={ticket.version} onExpired={onExpired} />}
    </section>
  )
}

function WorkArea({ path, heading, admin, agents, onExpired }) {
  const [hash, setHash] = useState(() => window.location.hash)
  useEffect(() => {
    const update = () => setHash(window.location.hash)
    window.addEventListener('hashchange', update)
    update()
    return () => window.removeEventListener('hashchange', update)
  }, [])
  const match = /^#\/tickets\/([1-9][0-9]*)$/.exec(hash)
  if (match) {
    return <WorkflowDetail key={match[1]} path={`${path}/${match[1]}`}
      heading={heading} admin={admin} agents={agents} onExpired={onExpired} />
  }
  return <WorkflowList path={path} heading={heading} admin={admin} agents={agents} onExpired={onExpired} />
}

function AdminWorkArea({ onExpired }) {
  const { data: agents, error, loading } = useResource('/admin/agents', onExpired)
  return (
    <>
      {loading && <p role="status">Loading agents…</p>}
      {error && <p role="alert" className="error">{error}</p>}
      <WorkArea path="/admin/tickets" heading="All Tickets" admin agents={agents} onExpired={onExpired} />
    </>
  )
}

export default function WorkflowTickets({ role, onExpired }) {
  if (role === 'ADMIN') return <AdminWorkArea onExpired={onExpired} />
  return <WorkArea path="/agent/tickets" heading="Assigned Tickets" onExpired={onExpired} />
}
