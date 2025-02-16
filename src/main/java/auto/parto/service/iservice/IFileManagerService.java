package auto.parto.service.iservice;

import auto.parto.dto.FileDto;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public interface IFileManagerService {
    Mono<String> save(FilePart image , String pieceId);
}
