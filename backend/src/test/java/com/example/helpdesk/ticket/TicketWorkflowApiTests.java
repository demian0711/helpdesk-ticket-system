package com.example.helpdesk.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
@Transactional
class TicketWorkflowApiTests {

    private static final String PASSWORD = "workflow-test-password";
    private static final String ADMIN = "/api/admin/tickets";
    private static final String AGENT = "/api/agent/tickets";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void adminAssignsAndReassignsThenAgentCompletesTheWork() throws Exception {
        Fixtures f = fixtures();
        long ticket = f.ticket();
        MockHttpSession admin = signIn(f.admin());
        MockHttpSession firstAgent = signIn(f.firstAgent());
        MockHttpSession secondAgent = signIn(f.secondAgent());

        mvc.perform(get(ADMIN + "/{id}", ticket).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/api/admin/agents").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '%s')]".formatted(f.firstAgent().email())).isNotEmpty())
                .andExpect(jsonPath("$[?(@.email == '%s')]".formatted(f.requester().email())).isEmpty());
        mvc.perform(get(ADMIN).session(firstAgent)).andExpect(status().isForbidden());

        assign(admin, ticket, f.firstAgent().id(), 0).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get(AGENT + "/{id}", ticket).session(firstAgent)).andExpect(status().isOk());
        assign(admin, ticket, f.secondAgent().id(), 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(TicketWorkflowService.CONFLICT));
        assign(admin, ticket, f.secondAgent().id(), 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee.email").value(f.secondAgent().email()));

        mvc.perform(get(AGENT).session(firstAgent)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(AGENT).session(secondAgent)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ticket));
        mvc.perform(get(ADMIN).param("status", "OPEN").param("assigneeId", Long.toString(f.secondAgent().id())).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ticket));
        mvc.perform(get(ADMIN).param("status", "RESOLVED").param("assigneeId", Long.toString(f.secondAgent().id())).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        change(secondAgent, ticket, "IN_PROGRESS", 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.version").value(3));
        assign(admin, ticket, f.firstAgent().id(), 3).andExpect(status().isConflict());
        change(secondAgent, ticket, "RESOLVED", 3).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.version").value(4));

        List<Event> events = jdbc.query("""
                        SELECT event_type, actor_id, old_assignee_id, new_assignee_id, old_status, new_status
                        FROM ticket_events WHERE ticket_id = ? ORDER BY id
                        """, (rs, row) -> new Event(rs.getString(1), rs.getLong(2),
                        rs.getObject(3, Long.class), rs.getObject(4, Long.class), rs.getString(5), rs.getString(6)), ticket);
        assertEquals(List.of(
                new Event("ASSIGNMENT_CHANGED", f.admin().id(), null, f.firstAgent().id(), null, null),
                new Event("ASSIGNMENT_CHANGED", f.admin().id(), f.firstAgent().id(), f.secondAgent().id(), null, null),
                new Event("STATUS_CHANGED", f.secondAgent().id(), null, null, "OPEN", "IN_PROGRESS"),
                new Event("STATUS_CHANGED", f.secondAgent().id(), null, null, "IN_PROGRESS", "RESOLVED")
        ), events);
    }

    @Test
    void rejectsNonAgentAssigneeDifferentAgentAndSkippedStatus() throws Exception {
        Fixtures f = fixtures();
        long ticket = f.ticket();
        MockHttpSession admin = signIn(f.admin());
        MockHttpSession firstAgent = signIn(f.firstAgent());
        MockHttpSession secondAgent = signIn(f.secondAgent());

        assign(admin, ticket, f.requester().id(), 0).andExpect(status().isBadRequest());
        assign(admin, ticket, f.firstAgent().id(), 0).andExpect(status().isOk());
        mvc.perform(get(AGENT + "/{id}", ticket).session(secondAgent)).andExpect(status().isNotFound());
        change(secondAgent, ticket, "IN_PROGRESS", 1).andExpect(status().isNotFound());
        change(firstAgent, ticket, "RESOLVED", 1).andExpect(status().isConflict());
        change(admin, ticket, "IN_PROGRESS", 1).andExpect(status().isForbidden());
        mvc.perform(get(AGENT + "/{id}", ticket).session(firstAgent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(1, eventCount(ticket));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentUpdatesWithTheSameVersionSaveExactlyOneAssignment() throws Exception {
        Fixtures f = fixtures();
        long ticket = f.ticket();
        try {
            MockHttpSession firstSession = signIn(f.admin());
            MockHttpSession secondSession = signIn(f.admin());
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> {
                    start.await();
                    return assign(firstSession, ticket, f.firstAgent().id(), 0).andReturn().getResponse().getStatus();
                });
                var second = executor.submit(() -> {
                    start.await();
                    return assign(secondSession, ticket, f.secondAgent().id(), 0).andReturn().getResponse().getStatus();
                });
                start.countDown();
                var statuses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                        .stream().sorted().toList();
                assertEquals(List.of(200, 409), statuses);
            }
            assertEquals(1, eventCount(ticket));
            assertEquals(1L, jdbc.queryForObject("SELECT version FROM tickets WHERE id = ?", Long.class, ticket));
        } finally {
            jdbc.update("DELETE FROM ticket_events WHERE ticket_id = ?", ticket);
            jdbc.update("DELETE FROM tickets WHERE id = ?", ticket);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)",
                    f.admin().id(), f.requester().id(), f.firstAgent().id(), f.secondAgent().id());
        }
    }

    private ResultActions assign(MockHttpSession session, long ticket, long agent, int version) throws Exception {
        return mvc.perform(patch(ADMIN + "/{id}/assignee", ticket).session(session).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeId\":%d,\"version\":%d}".formatted(agent, version)));
    }

    private ResultActions change(MockHttpSession session, long ticket, String status, int version) throws Exception {
        return mvc.perform(patch(AGENT + "/{id}/status", ticket).session(session).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"version\":%d}".formatted(status, version)));
    }

    private MockHttpSession signIn(TestUser user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf().asHeader())
                        .param("email", user.email()).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private Fixtures fixtures() {
        String suffix = "-" + UUID.randomUUID() + "@example.test";
        String hash = passwords.encode(PASSWORD);
        TestUser admin = insertUser("admin" + suffix, hash, "ADMIN");
        TestUser requester = insertUser("requester" + suffix, hash, "REQUESTER");
        TestUser first = insertUser("first-agent" + suffix, hash, "AGENT");
        TestUser second = insertUser("second-agent" + suffix, hash, "AGENT");
        long ticket = jdbc.queryForObject("""
                INSERT INTO tickets (title, description, priority, requester_id)
                VALUES ('Workflow ticket', 'Please check the network.', 'HIGH', ?) RETURNING id
                """, Long.class, requester.id());
        return new Fixtures(ticket, admin, requester, first, second);
    }

    private TestUser insertUser(String email, String hash, String role) {
        long id = jdbc.queryForObject("INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, hash, role);
        return new TestUser(id, email);
    }

    private int eventCount(long ticket) {
        return jdbc.queryForObject("SELECT count(*) FROM ticket_events WHERE ticket_id = ?", Integer.class, ticket);
    }

    private record TestUser(long id, String email) {
    }

    private record Fixtures(long ticket, TestUser admin, TestUser requester, TestUser firstAgent, TestUser secondAgent) {
    }

    private record Event(String type, long actor, Long oldAssignee, Long newAssignee, String oldStatus, String newStatus) {
    }
}
