# Helpdesk Ticket System

A personal full-stack project for creating, assigning, and tracking support tickets. The project is in development. This README describes the planned MVP. The backend and frontend have yet to be implemented.

## Stack and repository layout

- `backend/` will use Java 21, Spring Boot, Maven Wrapper, Spring Security, Spring Data JPA, and Flyway.
- `frontend/` will use React with JavaScript and Vite.
- PostgreSQL will run locally with Docker Compose.

Spring Security will use a session cookie and CSRF protection. During development, Vite will proxy `/api` requests to the backend so the browser can make same-origin requests.

## MVP scope

| Role | Ticket access and actions |
| --- | --- |
| REQUESTER | Create tickets, view their own tickets, and comment on them. |
| ADMIN | View and comment on any ticket. Assign or reassign an agent while the ticket is `OPEN`. |
| AGENT | View and comment on tickets assigned to them. Only the currently assigned agent can move a ticket from `OPEN` to `IN_PROGRESS`, then to `RESOLVED`. |

REQUESTER and ADMIN cannot change ticket status in the MVP.

Each ticket has a title, description, priority, status, creator, assignee, creation time, and last update time. The system will record assignment and status changes with the actor and timestamp for each event.

The MVP will use demo accounts and sample data. Public registration, email, attachments, and dashboards are out of scope.

## Planned demo flow

1. Sign in as a REQUESTER and create a ticket. It starts as `OPEN` and appears in your ticket list. Add a comment.
2. Sign in as an ADMIN, find the ticket, and assign it to a demo AGENT.
3. Sign in as the assigned AGENT, open the ticket, and move it to `IN_PROGRESS`. Add a comment with the resolution, then move it to `RESOLVED`.
4. Sign back in as the REQUESTER and verify that your ticket shows `RESOLVED`. As an ADMIN, check that the history records the assignment and both status changes, including who made each change and when.
