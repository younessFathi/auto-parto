package auto.parto.api;

import auto.parto.dto.FileDto;
import auto.parto.dto.PieceDto;
import auto.parto.dto.PieceOfferDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.IFileManagerService;
import auto.parto.service.iservice.ISearchPieceService;
import auto.parto.utils.Constants;
import auto.parto.utils.JsonUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Tag(name = Constants.Swagger.TAG_SEARCH, description = Constants.Swagger.TAG_SEARCH_DESCRIPTION)
public class SearchController {
    private ISearchPieceService searchPieceService;
    private IFileManagerService fileManagerService;
    private JsonUtil<PieceDto> pieceDeserializer;
    private JsonUtil<PieceOfferDto> pieceOfferDeserializer;

    @Operation(summary = Constants.Swagger.SEARCH_PIECE_IMAGES_SUMMARY, description = Constants.Swagger.SEARCH_PIECE_IMAGES_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_SEARCH_SUCCESS),
            @ApiResponse(responseCode = Constants.Swagger._400, description = Constants.Swagger.RESPONSE_400_BAD_REQUEST),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @PostMapping(value = "/piece" , consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<PieceDto> searchPieceAndNotifySellers(@RequestPart("images") Flux<FilePart> images , @RequestPart("piece") Mono<String> pieceStr){
        return pieceStr.flatMap(str->pieceDeserializer.fromJson(str , PieceDto.class))
                       .flatMap(pieceDto -> searchPieceService.notifySellers(pieceDto))
                       .flatMap(pieceDto -> images != null ?
                            images.flatMap(fileManagerService::save)
                                  .map(FileDto::getId)
                                  .collectList()
                                  .map(files -> {
                                      pieceDto.setImagesId(files);
                                      return pieceDto;
                                  }):
                            Mono.just(pieceDto)
                       ).flatMap(searchPieceService::savePieceWithImages);
    }

    @Operation(summary = Constants.Swagger.GET_PIECE_SUMMARY, description = Constants.Swagger.GET_PIECE_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = Constants.Swagger._200, description = Constants.Swagger.RESPONSE_200_PIECE_FOUND,
                    content = @Content(schema = @Schema(implementation = PieceDto.class))),
            @ApiResponse(responseCode = Constants.Swagger._404, description = Constants.Swagger.RESPONSE_404_PIECE_NOT_FOUND),
            @ApiResponse(responseCode = Constants.Swagger._500, description = Constants.Swagger.RESPONSE_500_INTERNAL_ERROR)
    })
    @GetMapping("/piece/{id}")
    public Mono<PieceDto> getPieceById(@PathVariable String id){
        return searchPieceService.getPieceWithImagesByPieceId(id)
                                .switchIfEmpty(Mono.error(new EntityNotFoundException("Piece not found")));
    }

    @Operation(summary = "Create a new piece offer and notify customer", description = "Creates a new offer for a piece and notifies the customer")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Piece offer created successfully",
                    content = @Content(schema = @Schema(implementation = PieceOfferDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/piece-offer")
    public Mono<PieceOfferDto> createOfferAndNotifyCustomer(@RequestPart("images") Flux<FilePart> images , @RequestPart("pieceOffer") Mono<String> pieceOfferStr){
        return pieceOfferStr.flatMap(str-> pieceOfferDeserializer.fromJson(str , PieceOfferDto.class))
                            .flatMap(searchPieceService::notifyCustomer)
                            .flatMap(pieceOfferDto -> images != null ?
                                    images.flatMap(fileManagerService::save)
                                            .map(FileDto::getId)
                                            .collectList()
                                            .map(files -> {
                                                pieceOfferDto.setImagesId(files);
                                                return pieceOfferDto;
                                            }):
                                    Mono.just(pieceOfferDto)
                            ).flatMap(searchPieceService::createOffer);
    }
}
