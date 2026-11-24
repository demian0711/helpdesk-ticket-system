package com.example.helpdesk.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;

import java.time.Instant;

@Entity
@Table(name = "ticket_events")
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private TicketEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_assignee_id")
    private User oldAssignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_assignee_id")
    private User newAssignee;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 16)
    private TicketStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 16)
    private TicketStatus newStatus;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketEvent() {
    }

    private TicketEvent(Ticket ticket, User actor, TicketEventType eventType) {
        this.ticket = ticket;
        this.actor = actor;
        this.eventType = eventType;
    }

    public static TicketEvent assignmentChanged(Ticket ticket, User actor, User oldAssignee, User newAssignee) {
        TicketEvent event = new TicketEvent(ticket, actor, TicketEventType.ASSIGNMENT_CHANGED);
        event.oldAssignee = oldAssignee;
        event.newAssignee = newAssignee;
        return event;
    }

    public static TicketEvent statusChanged(Ticket ticket, User actor, TicketStatus oldStatus, TicketStatus newStatus) {
        TicketEvent event = new TicketEvent(ticket, actor, TicketEventType.STATUS_CHANGED);
        event.oldStatus = oldStatus;
        event.newStatus = newStatus;
        return event;
    }

    public Long getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getActor() {
        return actor;
    }

    public TicketEventType getEventType() {
        return eventType;
    }

    public User getOldAssignee() {
        return oldAssignee;
    }

    public User getNewAssignee() {
        return newAssignee;
    }

    public TicketStatus getOldStatus() {
        return oldStatus;
    }

    public TicketStatus getNewStatus() {
        return newStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
