package no.rutebanken.marduk.exceptions;

public class AutomaticImportException extends RuntimeException {

    public AutomaticImportException(String message) {
        super(message);
    }

    public AutomaticImportException(String message, Throwable cause) {
        super(message, cause);
    }

}
