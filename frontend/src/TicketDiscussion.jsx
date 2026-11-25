import { useEffect, useRef, useState } from 'react'
import { getCurrentUser, request, requestWithCsrf } from './authApi.js'
import { statuses } from './ticketApi.js'
import { TicketTime } from './TicketViews.jsx'

const commentLimit = 2000

function activityText(event) {
  if (event.type === 'ASSIGNMENT_CHANGED') {
    return event.oldAssigneeEmail
      ? `Reassigned from ${event.oldAssigneeEmail} to ${event.newAssigneeEmail}.`
      : `Assigned to ${event.newAssigneeEmail}.`
  }
  return `Changed status from ${statuses[event.oldStatus]} to ${statuses[event.newStatus]}.`
}

function errorText(error, fallback) {
  if (error.status === 400) return error.message
  if (error.status === 403) return 'You do not have access to this ticket.'
  if (error.status === 404) return 'This ticket or its discussion is unavailable. Reload the ticket and try again.'
  return fallback
}

export default function TicketDiscussion({ id, version, onExpired }) {
  const [discussion, setDiscussion] = useState(null)
  const [readError, setReadError] = useState('')
  const [saveError, setSaveError] = useState('')
  const [saved, setSaved] = useState(false)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const pending = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    request(`/tickets/${id}/discussion`, { signal: controller.signal })
      .then(response => response.json())
      .then(data => {
        if (controller.signal.aborted) return
        setDiscussion(data)
        setReadError('')
      })
      .catch(error => {
        if (controller.signal.aborted) return
        if (error.status === 401) onExpired()
        else {
          if (error.status === 403 || error.status === 404) setDiscussion(null)
          setReadError(errorText(error, 'Could not load comments and activity.'))
        }
      })
    return () => controller.abort()
  }, [id, version, onExpired, refresh])

  useEffect(() => () => pending.current?.abort(), [])

  async function save(event) {
    event.preventDefault()
    if (pending.current) return
    const controller = new AbortController()
    pending.current = controller
    setBusy(true)
    setSaveError('')
    setSaved(false)
    try {
      await requestWithCsrf(`/tickets/${id}/comments`, {
        body: JSON.stringify({ body: draft }),
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
      })
      if (controller.signal.aborted) return
      setDraft('')
      setSaved(true)
      setRefresh(value => value + 1)
    } catch (error) {
      let status = error.status
      if (status === 403 && !controller.signal.aborted) {
        status = await getCurrentUser(controller.signal).then(() => 403, sessionError => sessionError.status)
      }
      if (controller.signal.aborted) return
      if (status === 401) onExpired()
      else setSaveError(errorText(error, 'Could not save the comment. Your draft is still here. Try again.'))
    } finally {
      pending.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  return (
    <div className="ticket-discussion">
      {readError && (
        <div>
          <p role="alert" className="error">{readError}</p>
          <button type="button" onClick={() => setRefresh(value => value + 1)}>Reload discussion</button>
        </div>
      )}
      {!discussion && !readError && <p role="status">Loading comments and activity…</p>}
      {discussion && (
        <>
          <section aria-labelledby="comments-heading">
            <h3 id="comments-heading">Comments</h3>
            {discussion.comments.length === 0 && <p>No comments yet.</p>}
            {discussion.comments.length > 0 && (
              <ol className="discussion-list">
                {discussion.comments.map(comment => (
                  <li key={comment.id}>
                    <div className="discussion-meta">
                      <strong>{comment.authorEmail}</strong>
                      <TicketTime value={comment.createdAt} />
                    </div>
                    <p className="discussion-body">{comment.body}</p>
                  </li>
                ))}
              </ol>
            )}
            <form className="ticket-form discussion-form" onSubmit={save} noValidate>
              <label htmlFor="ticket-comment">Add a comment</label>
              <textarea id="ticket-comment" rows={4} maxLength={commentLimit} required value={draft}
                disabled={busy} aria-describedby={saveError ? 'comment-limit comment-error' : 'comment-limit'}
                onChange={event => { setDraft(event.target.value); setSaveError(''); setSaved(false) }} />
              <p id="comment-limit" className="ticket-meta">{draft.length.toLocaleString()} / {commentLimit.toLocaleString()}</p>
              {saveError && <p id="comment-error" className="error" role="alert">{saveError}</p>}
              {saved && <p role="status">Comment posted.</p>}
              <button type="submit" disabled={busy}>{busy ? 'Posting…' : 'Post comment'}</button>
            </form>
          </section>
          <section aria-labelledby="activity-heading">
            <h3 id="activity-heading">Activity History</h3>
            {discussion.activity.length === 0 && <p>No assignment or status changes yet.</p>}
            {discussion.activity.length > 0 && (
              <ol className="discussion-list">
                {discussion.activity.map(event => (
                  <li key={event.id}>
                    <div className="discussion-meta">
                      <strong>{event.actorEmail}</strong>
                      <TicketTime value={event.createdAt} />
                    </div>
                    <p className="discussion-body">{activityText(event)}</p>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}
    </div>
  )
}
