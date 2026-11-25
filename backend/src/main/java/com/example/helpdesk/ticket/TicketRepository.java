package com.example.helpdesk.ticket;

import com.example.helpdesk.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByRequester_IdOrderByUpdatedAtDescIdDesc(Long requesterId);

    Optional<Ticket> findByIdAndRequester_Id(Long id, Long requesterId);
}
