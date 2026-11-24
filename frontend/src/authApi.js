export class ApiError extends Error {
  constructor(status) {
    super(`Request failed with HTTP ${status}`)
    this.status = status
  }
}

async function request(path, options) {
  const response = await fetch(`/api/auth${path}`, options)
  if (!response.ok) {
    throw new ApiError(response.status)
  }
  return response
}

async function postWithCsrf(path, body) {
  const { headerName, token } = await (await request('/csrf')).json()
  await request(path, {
    method: 'POST',
    headers: { [headerName]: token },
    body,
  })
}

export async function getCurrentUser(signal) {
  return (await request('/me', { signal })).json()
}

export function signIn(email, password) {
  return postWithCsrf('/login', new URLSearchParams({ email, password }))
}

export function signOut() {
  return postWithCsrf('/logout')
}
