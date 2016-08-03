package no.rutebanken.marduk.exceptions;

public class FileValidationException extends MardukException {

    public FileValidationException(String message) {
        super(message);
    }

    public FileValidationException(String message, Throwable throwable){
        super(message, throwable);
    }
}