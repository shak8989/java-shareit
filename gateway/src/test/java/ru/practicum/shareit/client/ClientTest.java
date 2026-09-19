package ru.practicum.shareit.client;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import ru.practicum.shareit.booking.dto.BookingCreateDto;
import ru.practicum.shareit.exception.ErrorHandler;
import ru.practicum.shareit.item.dto.ItemUpdateDto;
import ru.practicum.shareit.user.dto.UserDto;
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
import java.util.List;
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

    @Test
    void userCreateSendsBodyWithoutUserHeaderAndPreservesCreatedStatus() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/users")).andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("X-Sharer-User-Id"))
                .andExpect(content().json("{\"name\":\"User\",\"email\":\"user@test.ru\"}"))
                .andRespond(withStatus(HttpStatusCode.valueOf(201))
                        .contentType(MediaType.APPLICATION_JSON).body("{\"id\":2}"));

        ResponseEntity<Object> response = client.create(new UserDto("User", "user@test.ru"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 2));
        server.verify();
    }

    @Test
    void userGetByIdUsesUserPathWithoutUserHeader() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/users/2")).andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("X-Sharer-User-Id"))
                .andRespond(withSuccess("{\"id\":2,\"name\":\"User\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.getById(2L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 2, "name", "User"));
        server.verify();
    }

    @Test
    void userGetAllReturnsServerList() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/users")).andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("X-Sharer-User-Id"))
                .andRespond(withSuccess("[{\"id\":2}]", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.getAll();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(List.of(Map.of("id", 2)));
        server.verify();
    }

    @Test
    void itemPatchSendsOwnerHeaderAndPartialBody() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items/3")).andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andRespond(withSuccess("{\"id\":3,\"available\":false}", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.update(7L, 3L, new ItemUpdateDto(null, null, false));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 3, "available", false));
        server.verify();
    }

    @Test
    void itemGetByIdSendsViewerHeader() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items/3")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":3,\"comments\":[]}", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.getById(7L, 3L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 3, "comments", List.of()));
        server.verify();
    }

    @Test
    void itemGetByOwnerSendsOwnerHeaderAndReturnsList() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/items")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("[{\"id\":3}]", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.getByOwner(7L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(List.of(Map.of("id", 3)));
        server.verify();
    }

    @Test
    void bookingCreateSendsBookerHeaderAndBookingBody() {
        BookingClient client = new BookingClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/bookings")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andExpect(jsonPath("$.itemId").value(3))
                .andExpect(jsonPath("$.start").exists())
                .andExpect(jsonPath("$.end").exists())
                .andExpect(jsonPath("$.endAfterStart").doesNotExist())
                .andRespond(withSuccess("{\"id\":5,\"status\":\"WAITING\"}", MediaType.APPLICATION_JSON));
        LocalDateTime start = LocalDateTime.of(2030, 1, 1, 10, 0);

        ResponseEntity<Object> response = client.create(7L, new BookingCreateDto(3L, start, start.plusDays(1)));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 5, "status", "WAITING"));
        server.verify();
    }

    @Test
    void bookingGetByIdSendsUserHeader() {
        BookingClient client = new BookingClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();
        server.expect(requestTo(URL + "/bookings/5")).andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":5,\"status\":\"APPROVED\"}", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.getById(7L, 5L);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 5, "status", "APPROVED"));
        server.verify();
    }

    @Test
    void userClientCoversCreateAndGetMethods() {
        UserClient client = new UserClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();

        server.expect(requestTo(URL + "/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(URL + "/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(URL + "/users"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.create(new UserDto("User", "user@test.ru"));
        client.getById(1L);
        client.getAll();

        server.verify();
    }

    @Test
    void itemClientCoversUpdateAndGetMethods() {
        ItemClient client = new ItemClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();

        server.expect(requestTo(URL + "/items/3"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(URL + "/items/3"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(URL + "/items"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        client.update(7L, 3L, new ItemUpdateDto("Updated", null, null));
        client.getById(7L, 3L);
        client.getByOwner(7L);

        server.verify();
    }

    @Test
    void bookingClientCoversCreateAndGetById() {
        BookingClient client = new BookingClient(URL, new RestTemplateBuilder());
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusDays(1);

        server.expect(requestTo(URL + "/bookings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(URL + "/bookings/3"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));

        client.create(7L, new BookingCreateDto(10L, start, end));
        client.getById(7L, 3L);

        server.verify();
    }

    @Test
    void baseClientCoversPutPatchAndDeleteWithUser() {
        TestBaseClient client = new TestBaseClient(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();

        server.expect(requestTo(URL + "/test"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess());

        server.expect(requestTo(URL + "/test"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess());

        server.expect(requestTo(URL + "/test"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andRespond(withSuccess());

        client.putWithUser("/test", 7L, Map.of("value", "test"));
        client.patchWithUser("/test", 7L);
        client.deleteWithUser("/test", 7L);

        server.verify();
    }

    @Test
    void baseClientHandlesRedirectResponses() {
        TestBaseClient client = new TestBaseClient(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(client.rest).build();

        server.expect(requestTo(URL + "/redirect-body"))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"moved\":true}"));

        server.expect(requestTo(URL + "/not-modified"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        ResponseEntity<Object> withBody = client.getWithoutUser("/redirect-body");
        ResponseEntity<Object> withoutBody = client.getWithoutUser("/not-modified");

        assertThat(withBody.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(withBody.hasBody()).isTrue();

        assertThat(withoutBody.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(withoutBody.getBody()).isNull();

        server.verify();
    }

    @Test
    void errorHandlerReturnsBadGatewayWhenServerUnavailable() {
        ErrorHandler handler = new ErrorHandler();

        var response = handler.handleServerUnavailable(
                new ResourceAccessException("Server unavailable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error())
                .isEqualTo("ShareIt server is unavailable");
    }

    private static class TestBaseClient extends BaseClient {
        TestBaseClient(String url) {
            super(new RestTemplateBuilder().rootUri(url).build());
        }

        ResponseEntity<Object> getWithoutUser(String path) {
            return get(path);
        }

        ResponseEntity<Object> putWithUser(String path, long userId, Object body) {
            return put(path, userId, body);
        }

        ResponseEntity<Object> patchWithUser(String path, long userId) {
            return patch(path, userId);
        }

        ResponseEntity<Object> deleteWithUser(String path, long userId) {
            return delete(path, userId);
        }
    }

}
