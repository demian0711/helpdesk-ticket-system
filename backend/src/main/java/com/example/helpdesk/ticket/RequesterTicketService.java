package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class RequesterTicketService {

    private final TicketRepository tickets;
    private final EntityManager entityManager;
    private final TicketAccess access;

    RequesterTicketService(TicketRepository tickets, EntityManager entityManager, TicketAccess access) {
        this.tickets = tickets;
        this.entityManager = entityManager;
        this.access = access;
    }

    @Transactional
    Ticket create(Long requesterId, String title, String description, TicketPriority priority) {
        User requester = entityManager.getReference(User.class, requesterId);
        return tickets.save(new Ticket(title, description, priority, requester));
    }

    @Transactional(readOnly = true)
    List<Ticket> list(Long requesterId) {
        return tickets.findAllByRequester_IdOrderByUpdatedAtDescIdDesc(requesterId);
    }

    @Transactional(readOnly = true)
    Ticket get(HelpdeskPrincipal actor, Long ticketId) {
        return access.require(actor, ticketId);
    }
}
