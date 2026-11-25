package com.example.helpdesk.ticket;

import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByRequester_IdOrderByUpdatedAtDescIdDesc(Long requesterId);

    Optional<Ticket> findByIdAndRequester_Id(Long id, Long requesterId);

    List<Ticket> findAllByAssignee_IdOrderByUpdatedAtDescIdDesc(Long assigneeId);

    Optional<Ticket> findByIdAndAssignee_Id(Long id, Long assigneeId);

    @Query("""
            SELECT t FROM Ticket t LEFT JOIN FETCH t.assignee a
            WHERE (:status IS NULL OR t.status = :status)
              AND (:assigneeId IS NULL OR a.id = :assigneeId)
            ORDER BY t.updatedAt DESC, t.id DESC
            """)
    List<Ticket> findForAdmin(@Param("status") TicketStatus status, @Param("assigneeId") Long assigneeId);
}
