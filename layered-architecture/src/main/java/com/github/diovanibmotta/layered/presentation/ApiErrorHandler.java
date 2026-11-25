package com.github.diovanibmotta.layered.presentation;

import com.github.diovanibmotta.layered.business.LoanRejectedException;
import com.github.diovanibmotta.layered.business.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Translates business outcomes into HTTP: the business layer never knows status codes exist. */
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(NotFoundException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> rejected(LoanRejectedException e) {
        return Map.of("error", e.reason().name());
    }
}
