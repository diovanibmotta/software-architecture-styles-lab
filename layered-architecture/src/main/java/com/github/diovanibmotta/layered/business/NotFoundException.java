package com.github.diovanibmotta.layered.business;

public class NotFoundException extends RuntimeException {

    private final String resource;

    public NotFoundException(String resource) {
        super(resource + "_NOT_FOUND");
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}
