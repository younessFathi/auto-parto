package auto.parto.api;

import auto.parto.dto.SellerDto;
import auto.parto.enums.Currency;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.ISellerService;
import auto.parto.security.CanonicalSecurity;
import auto.parto.utils.Constants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@AllArgsConstructor
@Validated
@RequestMapping({"/api/v1/sellers", "/seller"})
@Tag(name = "Seller", description = "Seller API")
public class SellerController {
    private ISellerService sellerService;

    @Operation(summary = "Create a new seller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Seller created successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.class)) }),
            @ApiResponse(responseCode = "400", description = "Invalid input",
                    content = @Content)
    })
    @PostMapping("/create")
    @PreAuthorize("isAuthenticated()")
    public Mono<SellerDto> createSeller(@RequestBody @Valid SellerDto sellerDto) {
        return sellerService.create(sellerDto);
    }

    @Operation(summary = "Get seller by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Seller found",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.class)) }),
            @ApiResponse(responseCode = "404", description = "Seller not found",
                    content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Mono<SellerDto> getSellerById(@PathVariable String id) {
        return sellerService.getSellerById(id)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller not found")));
    }

    @Operation(summary = "Add funds to seller's wallet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds added successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.class)) }),
            @ApiResponse(responseCode = "404", description = "Seller not found",
                    content = @Content)
    })
    public Mono<SellerDto> addFunds(@PathVariable String id, @RequestParam BigDecimal amount,
                                    Authentication authentication) {
        return addFunds(id, amount, Currency.MAD, null, authentication);
    }

    @PostMapping("/{id}/wallet/add")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<SellerDto> addFunds(@PathVariable String id, @RequestParam BigDecimal amount,
                                    @RequestParam(defaultValue = "MAD") Currency currency,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    Authentication authentication) {
        CanonicalSecurity.requireOwner(authentication, id, "ROLE_SELLER");
        Mono<SellerDto> mutation = idempotencyKey == null
                ? sellerService.addFunds(id, amount)
                : sellerService.addFunds(id, amount, currency, idempotencyKey);
        return mutation
                        .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller not found")));
    }

    @Operation(summary = "Deduct funds from seller's wallet")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Funds deducted successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.class)) }),
            @ApiResponse(responseCode = "404", description = "Seller not found",
                    content = @Content)
    })
    public Mono<SellerDto> deductFunds(@PathVariable String id, @RequestParam BigDecimal amount,
                                       Authentication authentication) {
        return deductFunds(id, amount, Currency.MAD, null, authentication);
    }

    @PostMapping("/{id}/wallet/deduct")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<SellerDto> deductFunds(@PathVariable String id, @RequestParam BigDecimal amount,
                                       @RequestParam(defaultValue = "MAD") Currency currency,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       Authentication authentication) {
        CanonicalSecurity.requireOwner(authentication, id, "ROLE_SELLER");
        Mono<SellerDto> mutation = idempotencyKey == null
                ? sellerService.addFunds(id, amount.negate())
                : sellerService.addFunds(id, amount.negate(), currency, idempotencyKey);
        return mutation
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller not found")));
    }

    @Operation(summary = "Deactivate a seller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Seller deactivated successfully",
                    content = { @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SellerDto.class)) }),
            @ApiResponse(responseCode = "404", description = "Seller not found",
                    content = @Content)
    })
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SELLER')")
    public Mono<SellerDto> deactivateSeller(@PathVariable String id, Authentication authentication) {
        CanonicalSecurity.requireOwner(authentication, id, "ROLE_SELLER");
        return sellerService.deactivate(id)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller not found")));
    }
}
