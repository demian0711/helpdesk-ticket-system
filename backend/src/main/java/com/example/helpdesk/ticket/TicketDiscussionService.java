package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketComment;
import com.example.helpdesk.model.TicketEvent;
import com.example.helpdesk.model.TicketEventType;
import com.example.helpdesk.model.TicketStatus;
import com.example.helpdesk.model.User;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
class TicketDiscussionService {

    private final EntityManager entityManager;
    private final TicketAccess access;

    TicketDiscussionService(EntityManager entityManager, TicketAccess access) {
        this.entityManager = entityManager;
        this.access = access;
    }

    @Transactional(readOnly = true)
    Discussion get(HelpdeskPrincipal actor, Long id) {
        access.require(actor, id);
        var comments = entityManager.createQuery("""
                        SELECT c FROM TicketComment c JOIN FETCH c.author
                        WHERE c.ticket.id = :id ORDER BY c.createdAt, c.id
                        """, TicketComment.class)
                .setParameter("id", id).getResultList().stream().map(Comment::from).toList();
        var activity = entityManager.createQuery("""
                        SELECT e FROM TicketEvent e JOIN FETCH e.actor
                        LEFT JOIN FETCH e.oldAssignee LEFT JOIN FETCH e.newAssignee
                        WHERE e.ticket.id = :id ORDER BY e.createdAt, e.id
                        """, TicketEvent.class)
                .setParameter("id", id).getResultList().stream().map(Activity::from).toList();
        return new Discussion(comments, activity);
    }

    @Transactional
    Comment addComment(HelpdeskPrincipal actor, Long id, String body) {
        Ticket ticket = access.requireForComment(actor, id);
        User author = entityManager.getReference(User.class, actor.id());
        TicketComment comment = new TicketComment(ticket, author, body.strip());
        entityManager.persist(comment);
        return Comment.from(comment);
    }

    record Discussion(List<Comment> comments, List<Activity> activity) {
    }

    record Comment(Long id, String authorEmail, String body, Instant createdAt) {
        static Comment from(TicketComment comment) {
            return new Comment(comment.getId(), comment.getAuthor().getEmail(), comment.getBody(), comment.getCreatedAt());
        }
    }

    record Activity(Long id, TicketEventType type, String actorEmail, Instant createdAt, String oldAssigneeEmail,
                    String newAssigneeEmail, TicketStatus oldStatus, TicketStatus newStatus) {
        static Activity from(TicketEvent event) {
            return new Activity(event.getId(), event.getEventType(), event.getActor().getEmail(), event.getCreatedAt(),
                    email(event.getOldAssignee()), email(event.getNewAssignee()), event.getOldStatus(), event.getNewStatus());
        }

        private static String email(User user) {
            return user == null ? null : user.getEmail();
        }
    }
}
