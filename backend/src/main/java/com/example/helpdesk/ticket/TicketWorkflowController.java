package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.example.helpdesk.model.TicketStatus;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
class TicketWorkflowController {

    private final TicketWorkflowService workflow;

    TicketWorkflowController(TicketWorkflowService workflow) {
        this.workflow = workflow;
    }

    @GetMapping("/admin/tickets")
    List<WorkTicket> adminList(@AuthenticationPrincipal HelpdeskPrincipal actor,
                               @RequestParam(required = false) TicketStatus status,
                               @RequestParam(required = false) Long assigneeId) {
        return workflow.adminList(actor, status, assigneeId);
    }

    @GetMapping("/admin/tickets/{id}")
    WorkTicket adminDetail(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id) {
        return workflow.adminDetail(actor, id);
    }

    @GetMapping("/admin/agents")
    List<WorkTicket.Assignee> agents(@AuthenticationPrincipal HelpdeskPrincipal actor) {
        return workflow.agents(actor);
    }

    @PatchMapping("/admin/tickets/{id}/assignee")
    WorkTicket assign(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id,
                      @Valid @RequestBody Assignment request) {
        return workflow.assign(actor, id, request.assigneeId(), request.version());
    }

    @GetMapping("/agent/tickets")
    List<WorkTicket> assignedList(@AuthenticationPrincipal HelpdeskPrincipal actor) {
        return workflow.assignedList(actor);
    }

    @GetMapping("/agent/tickets/{id}")
    WorkTicket assignedDetail(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id) {
        return workflow.assignedDetail(actor, id);
    }

    @PatchMapping("/agent/tickets/{id}/status")
    WorkTicket changeStatus(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id,
                            @Valid @RequestBody StatusChange request) {
        return workflow.changeStatus(actor, id, request.status(), request.version());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<WorkflowError> workflowFailure(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new WorkflowError(exception.getReason()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    WorkflowError optimisticConflict() {
        return new WorkflowError(TicketWorkflowService.CONFLICT);
    }

    record Assignment(@NotNull @Positive Long assigneeId, @NotNull @PositiveOrZero Long version) {
    }

    record StatusChange(@NotNull TicketStatus status, @NotNull @PositiveOrZero Long version) {
    }

    record WorkflowError(String message) {
    }
}
