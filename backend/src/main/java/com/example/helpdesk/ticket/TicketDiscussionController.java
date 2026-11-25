package com.example.helpdesk.ticket;

import com.example.helpdesk.auth.HelpdeskPrincipal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/tickets/{id}")
class TicketDiscussionController {

    private final TicketDiscussionService discussion;

    TicketDiscussionController(TicketDiscussionService discussion) {
        this.discussion = discussion;
    }

    @GetMapping("/discussion")
    TicketDiscussionService.Discussion get(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id) {
        return discussion.get(actor, id);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    TicketDiscussionService.Comment add(@AuthenticationPrincipal HelpdeskPrincipal actor, @PathVariable Long id,
                                        @Valid @RequestBody NewComment request) {
        return discussion.addComment(actor, id, request.body());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    CommentError invalidComment(MethodArgumentNotValidException exception) {
        var error = exception.getBindingResult().getFieldError("body");
        return new CommentError(error == null ? "Enter a valid comment." : error.getDefaultMessage());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NewComment(@NotBlank(message = "Enter a comment.")
                      @Size(max = 2000, message = "Use 2,000 characters or fewer.") String body) {
    }

    record CommentError(String message) {
    }
}
