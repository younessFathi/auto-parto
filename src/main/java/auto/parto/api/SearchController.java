package auto.parto.api;

import auto.parto.dto.PieceDto;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.ISearchPieceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/search")
@AllArgsConstructor
@Validated
public class SearchController {
    private ISearchPieceService searchPieceService;
    private ObjectMapper objectMapper;
    private IFileManagerService fileManagerService;

    @PostMapping("/piece")
    public Mono<PieceDto> searchPieceAndNotifySellers(@Valid @RequestBody PieceDto pieceDto){
        return searchPieceService.searchPieceAndNotifySellers(pieceDto);
    }

    @PostMapping(value = "/piece-with-images" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<String> searchPieceAndNotifySellers(@RequestPart("images") Flux<FilePart> images , @RequestPart("piece") Mono<String> pieceStr){
        return pieceStr.flatMap(this::deserializePiece)
                .flatMap(pieceDto -> searchPieceService.searchPieceAndNotifySellers(pieceDto))
                .flatMap(pieceDto ->{
                    return images.flatMap(image -> fileManagerService.save(image , pieceDto.getId().toString()))
                                .collectList()
                                .flatMap(imagePaths -> Mono.just("Piece and images uploaded successfully!"));
                });
    }

    private Mono<PieceDto> deserializePiece(String pieceStr) {
        try {
            return Mono.just(objectMapper.readValue(pieceStr, PieceDto.class));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
