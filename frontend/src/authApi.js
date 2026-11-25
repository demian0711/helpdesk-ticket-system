export class ApiError extends Error {
  constructor(status, details) {
    super(details?.message ?? `Request failed with HTTP ${status}`)
    this.status = status
    this.fields = details?.fields
  }
}

export async function request(path, options) {
  const response = await fetch(`/api${path}`, options)
  if (!response.ok) {
    const details = [400, 404, 409].includes(response.status) ? await response.json().catch(() => null) : null
    throw new ApiError(response.status, details)
  }
  return response
}

export async function requestWithCsrf(path, { method = 'POST', body, headers, signal } = {}) {
  const { headerName, token } = await (await request('/auth/csrf', { signal })).json()
  return request(path, {
    method,
    headers: { ...headers, [headerName]: token },
    body,
    signal,
  })
}

export async function getCurrentUser(signal) {
  return (await request('/auth/me', { signal })).json()
}

export function signIn(email, password) {
  return requestWithCsrf('/auth/login', { body: new URLSearchParams({ email, password }) })
}

export function signOut() {
  return requestWithCsrf('/auth/logout')
}
