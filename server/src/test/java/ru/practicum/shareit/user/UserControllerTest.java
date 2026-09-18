package ru.practicum.shareit.user;

import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private UserService service;

    private final UserDto dto = new UserDto(1L, "User", "user@test.ru");

    @Test
    void create() throws Exception {
        when(service.create(any())).thenReturn(dto);
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"user@test.ru\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(service).create(new UserDto(null, "User", "user@test.ru"));
    }

    @Test
    void updateIsPartial() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(dto);
        mvc.perform(patch("/users/1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"User\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("user@test.ru"));
        verify(service).update(1L, new UserDto(null, "User", null));
    }

    @Test
    void getById() throws Exception {
        when(service.getById(1L)).thenReturn(dto);
        mvc.perform(get("/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getAll() throws Exception {
        when(service.getAll()).thenReturn(List.of(dto));
        mvc.perform(get("/users")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void deletesUser() throws Exception {
        mvc.perform(delete("/users/1")).andExpect(status().isOk());
        verify(service).delete(1L);
    }

    @Test
    void emailConflict() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Email already used"));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"User\",\"email\":\"user@test.ru\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Email already used"));
    }

    @Test
    void missingUser() throws Exception {
        when(service.getById(99L)).thenThrow(new NotFoundException("User not found"));
        mvc.perform(get("/users/99")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }

}
