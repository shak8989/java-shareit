package ru.practicum.shareit.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import ru.practicum.shareit.booking.BookingClient;
import ru.practicum.shareit.booking.BookingState;
import ru.practicum.shareit.item.ItemClient;
import ru.practicum.shareit.item.dto.CommentCreateDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.request.ItemRequestClient;
import ru.practicum.shareit.request.dto.ItemRequestCreateDto;
import ru.practicum.shareit.user.UserClient;
import ru.practicum.shareit.user.dto.UserUpdateDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClientTest {
    private static final String URL = "http://localhost:9090";

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409, 500})
    void preservesServerErrorStatusBodyAndContentType(int status) {
        ItemRequestClient client = new ItemRequestClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        String body = "{\"error\":\"Server error\"}";
        server.expect(requestTo(URL + "/requests/99")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));

        ResponseEntity<Object> response = client.getById(7L, 99L);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat((byte[]) response.getBody()).isEqualTo(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void requestPostSendsBodyHeaderAndAccept() {
        ItemRequestClient client = new ItemRequestClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/requests")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sharer-User-Id", "7")).andExpect(header("Accept", "application/json"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"description\":\"Need a drill\"}"))
                .andRespond(withSuccess("{\"id\":10,\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.create(7L, new ItemRequestCreateDto("Need a drill")).getStatusCode().value()).isEqualTo(200);
        server.verify();
    }

    @Test
    void requestListsUseCorrectPaths() {
        ItemRequestClient client = new ItemRequestClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/requests")).andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/requests/all")).andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getOwnRequests(7L);
        client.getAllOtherRequests(7L);
        server.verify();
    }

    @Test
    void userPatchSendsPartialBody() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/users/2")).andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.name").value("Updated")).andExpect(jsonPath("$.email").doesNotExist())
                .andRespond(withSuccess("{\"id\":2}", MediaType.APPLICATION_JSON));

        client.update(2L, new UserUpdateDto("Updated", null));
        server.verify();
    }

    @Test
    void userDeleteSupportsEmptyResponse() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/users/2")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatusCode.valueOf(204)));

        ResponseEntity<Object> response = client.deleteById(2L);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
        server.verify();
    }

    @Test
    void bookingApprovalSendsQueryAndHeader() {
        BookingClient client = new BookingClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/bookings/3?approved=false")).andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"status\":\"REJECTED\"}", MediaType.APPLICATION_JSON));

        client.approve(7L, 3L, false);
        server.verify();
    }

    @Test
    void bookingListsPassState() {
        BookingClient client = new BookingClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/bookings?state=WAITING"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL + "/bookings/owner?state=PAST"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.getByBooker(7L, BookingState.WAITING);
        client.getByOwner(7L, BookingState.PAST);
        server.verify();
    }

    @Test
    void itemPostKeepsOptionalRequestId() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sharer-User-Id", "7")).andExpect(jsonPath("$.requestId").value(10))
                .andExpect(jsonPath("$.available").value(false))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));

        client.create(7L, new ItemDto("Drill", "Cordless", false, 10L));
        server.verify();
    }

    @Test
    void searchEncodesQueryText() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items/search?text=drill%20%26%20saw")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.search(7L, "drill & saw");
        server.verify();
    }

    @Test
    void commentsUseItemPathAndAuthorHeader() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items/3/comment")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sharer-User-Id", "7")).andExpect(content().json("{\"text\":\"Good\"}"))
                .andRespond(withSuccess("{\"id\":8,\"text\":\"Good\"}", MediaType.APPLICATION_JSON));

        client.addComment(7L, 3L, new CommentCreateDto("Good"));
        server.verify();
    }
}
