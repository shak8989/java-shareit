package ru.practicum.shareit.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BaseClientTest {
    private static final String URL = "http://localhost:9090/items/3";

    private final RestTemplate rest = new RestTemplate();
    private final BaseClient client = new BaseClient(rest);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void putSendsBodyAndUserHeaderWithOptionalQueryParameters(boolean withParameters) {
        server.expect(requestTo(withParameters ? URL + "?notify=true" : URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"name\":\"Drill\"}"))
                .andRespond(withSuccess("{\"id\":3}", MediaType.APPLICATION_JSON));
        Map<String, String> body = Map.of("name", "Drill");

        ResponseEntity<Object> response = withParameters
                ? client.put(URL + "?notify={notify}", 7L, Map.of("notify", true), body)
                : client.put(URL, 7L, body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("id", 3));
        server.verify();
    }

    @Test
    void patchWithoutBodyStillSendsUserHeader() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andExpect(content().string(""))
                .andRespond(withNoContent());

        ResponseEntity<Object> response = client.patch(URL, 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        server.verify();
    }

    @Test
    void deleteSendsUserHeaderAndPreservesEmptyResponse() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Sharer-User-Id", "7"))
                .andExpect(content().string(""))
                .andRespond(withNoContent());

        ResponseEntity<Object> response = client.delete(URL, 7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        server.verify();
    }

    @Test
    void nonSuccessfulResponseWithoutExceptionPreservesStatusAndBody() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Temporarily moved\"}"));

        ResponseEntity<Object> response = client.get(URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(response.getBody()).isEqualTo(Map.of("message", "Temporarily moved"));
        server.verify();
    }

    @Test
    void nonSuccessfulResponseWithoutExceptionSupportsEmptyBody() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT));

        ResponseEntity<Object> response = client.get(URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
        assertThat(response.getBody()).isNull();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serverErrorWithoutContentTypePreservesStatusAndRawBody(boolean hasHeaders) {
        RestTemplate failingRest = mock(RestTemplate.class);
        byte[] body = "Item not found".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = hasHeaders ? new HttpHeaders() : null;
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.NOT_FOUND,
                "Not found", headers, body, StandardCharsets.UTF_8);
        when(failingRest.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(exception);

        ResponseEntity<Object> response = new BaseClient(failingRest).get(URL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders().getContentType()).isNull();
    }
}
