package ru.practicum.shareit.user;

import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.dto.UserUpdateDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private UserClient client;

    @Test
    void createsUser() throws Exception {
        when(client.create(any())).thenReturn(ResponseEntity.ok(Map.of("id", 1)));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"user@test.ru\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(client).create(new UserDto("User", "user@test.ru"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"User\"}", "{\"email\":\"user@test.ru\"}",
            "{\"name\":\" \",\"email\":\"user@test.ru\"}", "{\"name\":\"User\",\"email\":\"invalid\"}",
            "{\"name\":\"User\",\"email\":\"\"}"})
    void rejectsInvalidCreate(String body) throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void patchAllowsMissingEmail() throws Exception {
        when(client.update(eq(1L), any())).thenReturn(ResponseEntity.ok(Map.of("id", 1, "email", "user@test.ru")));
        mvc.perform(patch("/users/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@test.ru"));
        verify(client).update(1L, new UserUpdateDto("Updated", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":\" \"}", "{\"email\":\"\"}", "{\"email\":\"invalid\"}"})
    void rejectsInvalidPatch(String body) throws Exception {
        mvc.perform(patch("/users/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        verifyNoInteractions(client);
    }

    @Test
    void getsUser() throws Exception {
        when(client.getById(1L)).thenReturn(ResponseEntity.ok(Map.of("id", 1)));
        mvc.perform(get("/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listsUsers() throws Exception {
        when(client.getAll()).thenReturn(ResponseEntity.ok(List.of(Map.of("id", 1))));
        mvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deletesUser() throws Exception {
        when(client.deleteById(1L)).thenReturn(ResponseEntity.ok().build());
        mvc.perform(delete("/users/1")).andExpect(status().isOk());
        verify(client).deleteById(1L);
    }

    @Test
    void preservesConflict() throws Exception {
        when(client.create(any())).thenReturn(ResponseEntity.status(409).body(Map.of("error", "Email already used")));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"user@test.ru\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Email already used"));
    }

}
