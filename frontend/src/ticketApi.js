import { requestWithCsrf, request } from './authApi.js'

export const statuses = { OPEN: 'Open', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved' }

export async function listTickets(signal) {
  return (await request('/tickets', { signal })).json()
}

export async function getTicket(id, signal) {
  return (await request(`/tickets/${id}`, { signal })).json()
}

export async function createTicket(ticket, signal) {
  const response = await requestWithCsrf('/tickets', {
    body: JSON.stringify(ticket),
    headers: { 'Content-Type': 'application/json' },
    signal,
  })
  return response.json()
}
