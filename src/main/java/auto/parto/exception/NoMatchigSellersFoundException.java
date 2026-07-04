package auto.parto.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NoMatchigSellersFoundException extends EntityNotFoundException {
    public NoMatchigSellersFoundException(String message) {
        super(message);
    }
}
