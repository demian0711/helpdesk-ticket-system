package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.Ticket;
import com.example.helpdesk.model.TicketPriority;
import com.example.helpdesk.model.TicketStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
class RequesterTicketController {

    private final RequesterTicketService tickets;

    RequesterTicketController(RequesterTicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    ResponseEntity<TicketDetails> create(@AuthenticationPrincipal HelpdeskPrincipal user,
                                         @Valid @RequestBody NewTicket request) {
        Ticket ticket = tickets.create(user.id(), request.title(), request.description(),
                TicketPriority.valueOf(request.priority()));
        return ResponseEntity.created(URI.create("/api/tickets/" + ticket.getId())).body(TicketDetails.from(ticket));
    }

    @GetMapping
    List<TicketSummary> list(@AuthenticationPrincipal HelpdeskPrincipal user) {
        return tickets.list(user.id()).stream().map(TicketSummary::from).toList();
    }

    @GetMapping("/{id}")
    TicketDetails get(@AuthenticationPrincipal HelpdeskPrincipal user, @PathVariable Long id) {
        return TicketDetails.from(tickets.get(user.id(), id));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ValidationError invalidTicket(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return new ValidationError("Check the highlighted fields.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ValidationError invalidBody() {
        return new ValidationError("Send a valid ticket with a title, description, and priority.", Map.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewTicket(
            @NotBlank(message = "Enter a title.")
            @Size(max = 200, message = "Use 200 characters or fewer for the title.") String title,
            @NotBlank(message = "Enter a description.")
            @Size(max = 10000, message = "Use 10,000 characters or fewer for the description.") String description,
            @NotNull(message = "Select a priority.")
            @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Select Low, Medium, or High.") String priority) {

        NewTicket {
            if (title != null) {
                title = title.strip();
            }
            if (description != null) {
                description = description.strip();
            }
        }
    }

    record TicketSummary(Long id, String title, TicketPriority priority, TicketStatus status,
                         Instant createdAt, Instant updatedAt) {

        static TicketSummary from(Ticket ticket) {
            return new TicketSummary(ticket.getId(), ticket.getTitle(), ticket.getPriority(), ticket.getStatus(),
                    ticket.getCreatedAt(), ticket.getUpdatedAt());
        }
    }

    record TicketDetails(Long id, String title, String description, TicketPriority priority, TicketStatus status,
                         Instant createdAt, Instant updatedAt) {

        static TicketDetails from(Ticket ticket) {
            return new TicketDetails(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getPriority(),
                    ticket.getStatus(), ticket.getCreatedAt(), ticket.getUpdatedAt());
        }
    }

    record ValidationError(String message, Map<String, String> fields) {
    }
}
