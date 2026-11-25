package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketEvent;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import com.example.helpdesk.model.UserRole;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
class TicketWorkflowService {

    static final String CONFLICT = "This ticket changed. Reload it and try again.";

    private final TicketRepository tickets;
    private final EntityManager entityManager;
    private final TicketAccess access;

    TicketWorkflowService(TicketRepository tickets, EntityManager entityManager, TicketAccess access) {
        this.tickets = tickets;
        this.entityManager = entityManager;
        this.access = access;
    }

    @Transactional(readOnly = true)
    List<WorkTicket> adminList(HelpdeskPrincipal actor, TicketStatus status, Long assigneeId) {
        requireRole(actor, UserRole.ADMIN);
        return tickets.findForAdmin(status, assigneeId).stream().map(WorkTicket::from).toList();
    }

    @Transactional(readOnly = true)
    WorkTicket adminDetail(HelpdeskPrincipal actor, Long ticketId) {
        requireRole(actor, UserRole.ADMIN);
        return WorkTicket.from(access.require(actor, ticketId));
    }

    @Transactional(readOnly = true)
    List<WorkTicket.Assignee> agents(HelpdeskPrincipal actor) {
        requireRole(actor, UserRole.ADMIN);
        return entityManager.createQuery("""
                        SELECT u FROM User u WHERE u.role = :role ORDER BY u.email
                        """, User.class)
                .setParameter("role", UserRole.AGENT)
                .getResultList().stream().map(WorkTicket.Assignee::from).toList();
    }

    @Transactional(readOnly = true)
    List<WorkTicket> assignedList(HelpdeskPrincipal actor) {
        requireRole(actor, UserRole.AGENT);
        return tickets.findAllByAssignee_IdOrderByUpdatedAtDescIdDesc(actor.id())
                .stream().map(WorkTicket::from).toList();
    }

    @Transactional(readOnly = true)
    WorkTicket assignedDetail(HelpdeskPrincipal actor, Long ticketId) {
        requireRole(actor, UserRole.AGENT);
        return WorkTicket.from(access.require(actor, ticketId));
    }

    @Transactional
    WorkTicket assign(HelpdeskPrincipal actor, Long ticketId, Long assigneeId, Long expectedVersion) {
        requireRole(actor, UserRole.ADMIN);
        Ticket ticket = access.require(actor, ticketId);
        checkVersion(ticket, expectedVersion);
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only open tickets can be assigned.");
        }

        User assignee = entityManager.find(User.class, assigneeId);
        if (assignee == null || assignee.getRole() != UserRole.AGENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid agent.");
        }
        User previous = ticket.getAssignee();
        if (previous != null && previous.getId().equals(assigneeId)) {
            return WorkTicket.from(ticket);
        }

        ticket.assignTo(assignee);
        tickets.flush();
        entityManager.persist(TicketEvent.assignmentChanged(ticket, actorReference(actor), previous, assignee));
        return WorkTicket.from(ticket);
    }

    @Transactional
    WorkTicket changeStatus(HelpdeskPrincipal actor, Long ticketId, TicketStatus nextStatus, Long expectedVersion) {
        requireRole(actor, UserRole.AGENT);
        Ticket ticket = access.require(actor, ticketId);
        checkVersion(ticket, expectedVersion);
        TicketStatus previous = ticket.getStatus();
        boolean valid = switch (previous) {
            case OPEN -> nextStatus == TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> nextStatus == TicketStatus.RESOLVED;
            case RESOLVED -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That status transition is not allowed.");
        }

        ticket.changeStatus(nextStatus);
        tickets.flush();
        entityManager.persist(TicketEvent.statusChanged(ticket, actorReference(actor), previous, nextStatus));
        return WorkTicket.from(ticket);
    }

    private User actorReference(HelpdeskPrincipal actor) {
        return entityManager.getReference(User.class, actor.id());
    }

    private static void requireRole(HelpdeskPrincipal actor, UserRole role) {
        if (actor.role() != role) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission.");
        }
    }

    private static void checkVersion(Ticket ticket, Long expectedVersion) {
        if (!Objects.equals(ticket.getVersion(), expectedVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT);
        }
    }
}
