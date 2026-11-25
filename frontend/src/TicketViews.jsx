import { statuses } from './ticketApi.js'

const priorities = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }
const dateFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function TicketTime({ value }) {
  return <time dateTime={value}>{dateFormat.format(new Date(value))}</time>
}

export function TicketRows({ tickets, listPath }) {
  return (
    <ul className="ticket-list">
      {tickets.map((ticket) => (
        <li key={ticket.id}>
          <a className="ticket-row" href={`${listPath}/${ticket.id}`}>
            <div>
              <span className="ticket-title">{ticket.title}</span>
              <span className="ticket-meta">#{ticket.id} · {priorities[ticket.priority]} priority</span>
              {'assignee' in ticket && <span className="ticket-meta">{ticket.assignee?.email ?? 'Unassigned'}</span>}
            </div>
            <div className="ticket-row-end">
              <span className="status-pill">{statuses[ticket.status]}</span>
              <TicketTime value={ticket.updatedAt} />
            </div>
          </a>
        </li>
      ))}
    </ul>
  )
}

export function TicketCard({ ticket }) {
  return (
    <article className="ticket-detail">
      <div className="ticket-heading">
        <h3>{ticket.title}</h3>
        <span className="status-pill">{statuses[ticket.status]}</span>
      </div>
      <dl className="ticket-facts">
        <div><dt>Ticket</dt><dd>#{ticket.id}</dd></div>
        <div><dt>Priority</dt><dd>{priorities[ticket.priority]}</dd></div>
        {'assignee' in ticket && <div><dt>Assignee</dt><dd>{ticket.assignee?.email ?? 'Unassigned'}</dd></div>}
        <div><dt>Created</dt><dd><TicketTime value={ticket.createdAt} /></dd></div>
        <div><dt>Updated</dt><dd><TicketTime value={ticket.updatedAt} /></dd></div>
      </dl>
      <h4>Description</h4>
      <p className="ticket-description">{ticket.description}</p>
    </article>
  )
}
