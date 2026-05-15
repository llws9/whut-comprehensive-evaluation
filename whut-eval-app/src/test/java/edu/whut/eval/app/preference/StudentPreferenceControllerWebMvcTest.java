package edu.whut.eval.app.preference;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.whut.eval.application.preference.command.CreateUserPreferenceCommand;
import edu.whut.eval.application.preference.query.UserPreferenceView;
import edu.whut.eval.application.preference.service.UserPreferenceCommandApplicationService;
import edu.whut.eval.common.exception.ConflictException;
import edu.whut.eval.interfaces.exception.GlobalExceptionHandler;
import edu.whut.eval.interfaces.student.StudentPreferenceController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudentPreferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = StudentPreferenceControllerWebMvcTest.TestApplication.class)
@Import({
        StudentPreferenceController.class,
        GlobalExceptionHandler.class
})
class StudentPreferenceControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserPreferenceCommandApplicationService userPreferenceCommandApplicationService;

    @Test
    void shouldCreatePreferenceSuccessfully() throws Exception {
        given(userPreferenceCommandApplicationService.createCurrentUserPreference(any(CreateUserPreferenceCommand.class)))
                .willReturn(new UserPreferenceView(1L, 1001L, "dark", true));

        mockMvc.perform(post("/api/student/preferences")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestPayload("dark", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userId").value(1001))
                .andExpect(jsonPath("$.data.preferredTheme").value("dark"))
                .andExpect(jsonPath("$.data.notificationsEnabled").value(true));
    }

    @Test
    void shouldReturn409WhenPreferenceAlreadyExists() throws Exception {
        given(userPreferenceCommandApplicationService.createCurrentUserPreference(any(CreateUserPreferenceCommand.class)))
                .willThrow(new ConflictException("当前用户已存在偏好设置，请改用更新接口"));

        mockMvc.perform(post("/api/student/preferences")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestPayload("dark", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ-4090"))
                .andExpect(jsonPath("$.message").value("当前用户已存在偏好设置，请改用更新接口"));
    }

    @Test
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/student/preferences")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestPayload("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VAL-4001"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    private record RequestPayload(String preferredTheme, Boolean notificationsEnabled) {
    }
}
