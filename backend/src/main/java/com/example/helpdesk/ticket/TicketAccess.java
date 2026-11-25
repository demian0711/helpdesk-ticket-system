package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.Ticket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Component
class TicketAccess {

    private final EntityManager entityManager;

    TicketAccess(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    Ticket require(HelpdeskPrincipal actor, Long id) {
        return check(actor, entityManager.find(Ticket.class, id));
    }

    Ticket requireForComment(HelpdeskPrincipal actor, Long id) {
        // Giữ khóa đọc đến khi lưu xong để việc đổi assignee không làm quyền ghi bị lỗi thời.
        return check(actor, entityManager.find(Ticket.class, id, LockModeType.PESSIMISTIC_READ));
    }

    private Ticket check(HelpdeskPrincipal actor, Ticket ticket) {
        if (ticket == null || !canAccess(actor, ticket)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found.");
        }
        return ticket;
    }

    private boolean canAccess(HelpdeskPrincipal actor, Ticket ticket) {
        return switch (actor.role()) {
            case ADMIN -> true;
            case REQUESTER -> Objects.equals(actor.id(), ticket.getRequester().getId());
            case AGENT -> ticket.getAssignee() != null && Objects.equals(actor.id(), ticket.getAssignee().getId());
        };
    }
}
