package ru.practicum.shareit.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException exception) {
        return response(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException exception) {
        return response(exception, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception) {
        return response(exception, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({ValidationException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class, MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return response(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(Exception exception) {
        return response(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> response(Exception exception, HttpStatus status) {
        return ResponseEntity.status(status).body(new ErrorResponse(exception.getMessage()));
    }
}
