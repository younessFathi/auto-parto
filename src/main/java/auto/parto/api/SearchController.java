package auto.parto.api;

import auto.parto.dto.PieceDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.utils.Constants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = Constants.Swagger.TAG_SEARCH, description = Constants.Swagger.TAG_SEARCH_DESCRIPTION)
public class SearchController {
    private ISearchPieceService searchPieceService;
    private ObjectMapper objectMapper;
    private IFileManagerService fileManagerService;

    @Operation(summary = Constants.Swagger.SEARCH_PIECE_SUMMARY, description = Constants.Swagger.SEARCH_PIECE_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_SEARCH_SUCCESS,
                    content = @Content(schema = @Schema(implementation = PieceDto.class))),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.RESPONSE_400_BAD_REQUEST),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @PostMapping("/piece")
    public Mono<PieceDto> searchPieceAndNotifySellers(@Valid @RequestBody PieceDto pieceDto){
        return searchPieceService.notifySellers(pieceDto)
                .flatMap(piece -> searchPieceService.savePieceWithImages(piece));
    }

    @Operation(summary = Constants.Swagger.SEARCH_PIECE_IMAGES_SUMMARY, description = Constants.Swagger.SEARCH_PIECE_IMAGES_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_SEARCH_SUCCESS),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.RESPONSE_400_BAD_REQUEST),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @PostMapping(value = "/piece-with-images" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<PieceDto> searchPieceAndNotifySellers(@RequestPart("images") Flux<FilePart> images , @RequestPart("piece") Mono<String> pieceStr){
        return pieceStr.flatMap(this::deserializePiece)
                .flatMap(pieceDto -> searchPieceService.notifySellers(pieceDto))
                .flatMap(pieceDto ->{
                    return images.flatMap(image -> fileManagerService.save(image))
                                .collectList()
                                .flatMap(fileDtos -> {
                                    pieceDto.setImages(fileDtos);
                                    return searchPieceService.savePieceWithImages(pieceDto);
                                })
                                .flatMap(Mono::just);
                });
    }

    @GetMapping("/piece/{id}")
    public Mono<PieceDto> getPieceById(@PathVariable String id){
        return searchPieceService.getPieceWithImagesByPieceId(id)
                                .switchIfEmpty(Mono.error(new EntityNotFoundException("Piece not found")));
    }

    private Mono<PieceDto> deserializePiece(String pieceStr) {
        try {
            return Mono.just(objectMapper.readValue(pieceStr, PieceDto.class));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
