package com.example.helpdesk.ticket;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.demo.enabled=false")
@AutoConfigureMockMvc
@Transactional
class RequesterTicketApiTests {

    private static final String TICKETS = "/api/tickets";
    private static final String REQUESTER_A = "ticket-requester-a@example.test";
    private static final String REQUESTER_B = "ticket-requester-b@example.test";
    private static final String AGENT = "ticket-agent@example.test";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long requesterBId;

    @BeforeEach
    void seedUsers() {
        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(REQUESTER_A, hash, "REQUESTER");
        requesterBId = insertUser(REQUESTER_B, hash, "REQUESTER");
        insertUser(AGENT, hash, "AGENT");
    }

    @Test
    void requesterACannotSeeOrOpenRequesterBTicket() throws Exception {
        MockHttpSession requesterA = signIn(REQUESTER_A);
        MockHttpSession requesterB = signIn(REQUESTER_B);

        mvc.perform(get(TICKETS).session(requesterA)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        Long ticketB = create(requesterB, "Laptop failure", requesterBId);

        mvc.perform(get(TICKETS + "/{id}", ticketB).session(requesterB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Laptop failure"))
                .andExpect(jsonPath("$.description").value("Please check this issue."));
        mvc.perform(get(TICKETS + "/{id}", ticketB).session(requesterA)).andExpect(status().isNotFound());

        Long ticketA = create(requesterA, "Network failure", requesterBId);
        assertEquals(requesterBId, jdbc.queryForObject("SELECT requester_id FROM tickets WHERE id = ?", Long.class, ticketB));
        assertEquals(REQUESTER_A, jdbc.queryForObject(
                "SELECT u.email FROM users u JOIN tickets t ON t.requester_id = u.id WHERE t.id = ?",
                String.class, ticketA));

        mvc.perform(get(TICKETS).session(requesterA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Network failure"));
        mvc.perform(get(TICKETS).session(requesterB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Laptop failure"));
    }

    @Test
    void validatesFieldsAndRequiresRequesterRoleAndCsrf() throws Exception {
        MockHttpSession requester = signIn(REQUESTER_A);
        MockHttpSession agent = signIn(AGENT);
        String invalid = """
                {"title":"   ","description":"","priority":"INVALID"}
                """;

        mvc.perform(get(TICKETS)).andExpect(status().isUnauthorized());
        mvc.perform(get(TICKETS).session(agent)).andExpect(status().isForbidden());
        mvc.perform(post(TICKETS).session(agent).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isForbidden());
        mvc.perform(post(TICKETS).session(requester).contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isForbidden());
        mvc.perform(post(TICKETS).session(requester).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.description").exists())
                .andExpect(jsonPath("$.fields.priority").exists());
    }

    private Long create(MockHttpSession session, String title, Long submittedRequesterId) throws Exception {
        String body = """
                {"title":"  %s  ","description":"Please check this issue.","priority":"HIGH",
                 "status":"RESOLVED","requesterId":%d}
                """.formatted(title, submittedRequesterId);
        String response = mvc.perform(post(TICKETS).session(session).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.requester").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.id").longValue();
    }

    private MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf().asHeader())
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andReturn().getRequest().getSession(false);
    }

    private Long insertUser(String email, String hash, String role) {
        return jdbc.queryForObject("INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, email, hash, role);
    }
}
