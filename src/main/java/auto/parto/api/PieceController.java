package auto.parto.api;

import auto.parto.dto.PagedResponse;
import auto.parto.dto.PieceDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.IPieceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/pieces")
@AllArgsConstructor
@Tag(name = "Piece Controller", description = "APIs for managing pieces")
public class PieceController {
    private final IPieceService pieceService;

    @Operation(
            summary = "Get all pieces by customer ID",
            description = "Retrieves a paginated list of pieces for a given customer ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Pieces found",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No pieces found for the given customer ID",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))
            )
    })
    @GetMapping("/{customerId}")
    public Mono<ResponseEntity<PagedResponse<PieceDto>>> getAllPiecesByCustomerId(
            @Parameter(description = "ID of the customer", required = true, example = "customer1")
            @PathVariable String customerId,
            @Parameter(description = "Page number (starting from 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page number (starting from 0)", example = "0")
            @RequestParam(defaultValue = "10") int size){
        if (page < 0) {
            return Mono.error(new IllegalArgumentException("page must be >= 0"));
        }
        if (size < 1 || size > 100) {
            return Mono.error(new IllegalArgumentException("size must be between 1 and 100"));
        }
        return pieceService.getAllPiecesByCustomerId(customerId , page , size).map(
                pieceDtoPagedResponse -> {
                    if (pieceDtoPagedResponse.getData().isEmpty()){
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(pieceDtoPagedResponse);
                    }
                    return ResponseEntity.status(HttpStatus.OK).body(pieceDtoPagedResponse);
                });
    }

    @Operation(
            summary = "Get piece details by piece ID",
            description = "Retrieves the details of a piece by its ID."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Piece found",
                    content = @Content(schema = @Schema(implementation = PieceDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Piece not found",
                    content = @Content(schema = @Schema(implementation = Void.class))
            )
    })
    @GetMapping("/{pieceId}/detail")
    public Mono<ResponseEntity<PieceDto>> getPieceDetailByPieceId(
            @Parameter(description = "ID of the piece", required = true, example = "piece1")
            @PathVariable String pieceId){
        return pieceService.getPieceDetailByPieceId(pieceId)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Piece not found with id: " + pieceId)))
                .map(pieceDto -> ResponseEntity.status(HttpStatus.OK).body(pieceDto));
    }
}
