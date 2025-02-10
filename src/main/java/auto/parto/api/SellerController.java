package auto.parto.api;

import auto.parto.dto.SellerDto;
import auto.parto.exception.EntityNotFoundException;
import auto.parto.service.iservice.ISellerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@AllArgsConstructor
@Validated
@RequestMapping("/seller")
public class SellerController {
    private ISellerService sellerService;

    @PostMapping("/create")
    public Mono<SellerDto> createSeller(@RequestBody @Valid SellerDto sellerDto) {
        return sellerService.create(sellerDto);
    }

    @GetMapping("/{id}")
    public Mono<SellerDto> getSellerById(@PathVariable String id) {
        return sellerService.getSellerById(id)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Seller not found")));
    }
}
