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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
@Transactional
class TicketDiscussionApiTests {

    private static final String PASSWORD = "discussion-test-password";
    private static final String DISCUSSION = "/api/tickets/{id}/discussion";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwords;

    @Test
    void readsChronologicalCommentsAndWorkflowEventsForAllAuthorizedRoles() throws Exception {
        String hash = passwords.encode(PASSWORD);
        Account requester = account("REQUESTER", hash);
        Account admin = account("ADMIN", hash);
        Account firstAgent = account("AGENT", hash);
        Account agent = account("AGENT", hash);
        long ticket = ticket(requester);
        MockHttpSession requesterSession = signIn(requester);
        MockHttpSession adminSession = signIn(admin);
        MockHttpSession agentSession = signIn(agent);

        comment(requesterSession, ticket, """
                {"body":"  The network is down.  ","authorId":%d}
                """.formatted(admin.id()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorEmail").value(requester.email()))
                .andExpect(jsonPath("$.body").value("The network is down."))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        assign(adminSession, ticket, firstAgent, 0);
        assign(adminSession, ticket, agent, 1);
        comment(adminSession, ticket, """
                {"body":"Please investigate."}
                """).andExpect(status().isCreated());

        mvc.perform(patch("/api/agent/tickets/{id}/status", ticket).session(agentSession).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"status":"IN_PROGRESS","version":2}
                                """))
                .andExpect(status().isOk());
        comment(agentSession, ticket, """
                {"body":"Working on it."}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.authorEmail").value(agent.email()));

        for (MockHttpSession session : new MockHttpSession[]{requesterSession, adminSession, agentSession}) {
            mvc.perform(get(DISCUSSION, ticket).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comments.length()").value(3))
                    .andExpect(jsonPath("$.comments[0].authorEmail").value(requester.email()))
                    .andExpect(jsonPath("$.comments[1].authorEmail").value(admin.email()))
                    .andExpect(jsonPath("$.comments[2].authorEmail").value(agent.email()))
                    .andExpect(jsonPath("$.activity.length()").value(3))
                    .andExpect(jsonPath("$.activity[0].type").value("ASSIGNMENT_CHANGED"))
                    .andExpect(jsonPath("$.activity[0].actorEmail").value(admin.email()))
                    .andExpect(jsonPath("$.activity[0].createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.activity[1].oldAssigneeEmail").value(firstAgent.email()))
                    .andExpect(jsonPath("$.activity[1].newAssigneeEmail").value(agent.email()))
                    .andExpect(jsonPath("$.activity[2].actorEmail").value(agent.email()))
                    .andExpect(jsonPath("$.activity[2].oldStatus").value("OPEN"))
                    .andExpect(jsonPath("$.activity[2].newStatus").value("IN_PROGRESS"));
        }
        MockHttpSession formerAssignee = signIn(firstAgent);
        mvc.perform(get(DISCUSSION, ticket).session(formerAssignee)).andExpect(status().isNotFound());
        comment(formerAssignee, ticket, """
                {"body":"I used to be assigned."}
                """).andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthorizedBlankOverlongAndMissingCsrfWrites() throws Exception {
        String hash = passwords.encode(PASSWORD);
        Account owner = account("REQUESTER", hash);
        long ticket = ticket(owner);
        MockHttpSession ownerSession = signIn(owner);
        mvc.perform(get(DISCUSSION, ticket)).andExpect(status().isUnauthorized());

        for (String role : new String[]{"REQUESTER", "AGENT"}) {
            MockHttpSession outsider = signIn(account(role, hash));
            mvc.perform(get(DISCUSSION, ticket).session(outsider)).andExpect(status().isNotFound());
            comment(outsider, ticket, """
                    {"body":"No access"}
                    """).andExpect(status().isNotFound());
        }

        comment(ownerSession, ticket, """
                {"body":"   "}
                """).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Enter a comment."));
        comment(ownerSession, ticket, "{\"body\":\"" + "x".repeat(2001) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Use 2,000 characters or fewer."));
        mvc.perform(post("/api/tickets/{id}/comments", ticket).session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"body":"Missing token"}
                                """))
                .andExpect(status().isForbidden());
        mvc.perform(get(DISCUSSION, ticket).session(ownerSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.comments").isEmpty());
    }

    private ResultActions comment(MockHttpSession session, long ticket, String body) throws Exception {
        return mvc.perform(post("/api/tickets/{id}/comments", ticket).session(session).with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assign(MockHttpSession admin, long ticket, Account agent, int version) throws Exception {
        mvc.perform(patch("/api/admin/tickets/{id}/assignee", ticket).session(admin).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":%d,\"version\":%d}".formatted(agent.id(), version)))
                .andExpect(status().isOk());
    }

    private MockHttpSession signIn(Account user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf().asHeader())
                        .param("email", user.email()).param("password", PASSWORD))
                .andExpect(status().isNoContent()).andReturn().getRequest().getSession(false);
    }

    private Account account(String role, String hash) {
        String email = UUID.randomUUID() + "@example.test";
        long id = jdbc.queryForObject("INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, hash, role);
        return new Account(id, email);
    }

    private long ticket(Account owner) {
        return jdbc.queryForObject("""
                INSERT INTO tickets (title, description, priority, requester_id)
                VALUES ('Discussion ticket', 'Network issue', 'HIGH', ?) RETURNING id
                """, Long.class, owner.id());
    }

    private record Account(long id, String email) {
    }
}
