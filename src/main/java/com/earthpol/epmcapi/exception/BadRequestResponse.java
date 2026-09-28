package com.earthpol.epmcapi.exception;

public class BadRequestResponse extends HttpResponseException {
    public BadRequestResponse(String message) {
        super(400, message);
    }
}
