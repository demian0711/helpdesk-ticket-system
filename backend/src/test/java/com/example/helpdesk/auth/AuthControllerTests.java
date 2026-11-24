package com.example.helpdesk.auth;

import com.example.helpdesk.model.User;
import com.example.helpdesk.model.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTests {

    private static final String EMAIL = "requester@example.test";
    private static final String PASSWORD = "test-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository users;

    @BeforeEach
    void setUp() {
        User user = new User(EMAIL, passwordEncoder.encode(PASSWORD), UserRole.REQUESTER);
        given(users.findByEmail(EMAIL)).willReturn(Optional.of(user));
    }

    @Test
    void rejectsAnonymousRequestsAndWrongPasswords() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isForbidden());

        Csrf csrf = csrf(new MockHttpSession());
        mvc.perform(post("/api/auth/login").session(csrf.session())
                        .header(csrf.header(), csrf.token()).param("email", EMAIL).param("password", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(csrf.session())).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesSessionAndRequiresFreshCsrfForLogout() throws Exception {
        Csrf beforeLogin = csrf(new MockHttpSession());
        MockHttpSession session = beforeLogin.session();
        String anonymousSessionId = session.getId();

        mvc.perform(post("/api/auth/login").session(session)
                        .with(request -> {
                            request.setRequestedSessionId(anonymousSessionId);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        })
                        .header(beforeLogin.header(), beforeLogin.token())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isNoContent());
        assertNotEquals(anonymousSessionId, session.getId());

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("REQUESTER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(session).header(beforeLogin.header(), beforeLogin.token()))
                .andExpect(status().isForbidden());

        Csrf afterLogin = csrf(session);
        mvc.perform(post("/api/auth/logout").session(session).header(afterLogin.header(), afterLogin.token()))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        Csrf afterLogout = csrf(new MockHttpSession());
        mvc.perform(post("/api/auth/login").session(afterLogout.session())
                        .header(afterLogout.header(), afterLogout.token())
                        .param("email", EMAIL).param("password", PASSWORD))
                .andExpect(status().isNoContent());
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new Csrf(session, JsonPath.read(body, "$.headerName"), JsonPath.read(body, "$.token"));
    }

    private record Csrf(MockHttpSession session, String header, String token) {
    }
}
