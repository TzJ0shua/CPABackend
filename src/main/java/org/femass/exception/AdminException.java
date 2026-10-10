package org.femass.exception;

public class AdminException extends IllegalArgumentException {
    private final int status;

    public AdminException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
