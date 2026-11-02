package exception;

/**
 * One exception type for every expected problem in the app
 * (bad input, wrong password, database failure...).
 * It carries an HTTP status code so the API layer knows what to send back.
 */
public class AppException extends Exception {

    private final int status;

    public AppException(String message, int status) {
        super(message);
        this.status = status;
    }

    public AppException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
