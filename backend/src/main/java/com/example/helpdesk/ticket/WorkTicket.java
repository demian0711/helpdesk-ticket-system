package com.example.helpdesk.ticket;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;

import java.time.Instant;

record WorkTicket(Long id, String title, String description, TicketPriority priority, TicketStatus status,
                  Long version, Assignee assignee, Instant createdAt, Instant updatedAt) {

    static WorkTicket from(Ticket ticket) {
        User assignee = ticket.getAssignee();
        return new WorkTicket(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getPriority(),
                ticket.getStatus(), ticket.getVersion(), assignee == null ? null : Assignee.from(assignee),
                ticket.getCreatedAt(), ticket.getUpdatedAt());
    }

    record Assignee(Long id, String email) {

        static Assignee from(User user) {
            return new Assignee(user.getId(), user.getEmail());
        }
    }
}
