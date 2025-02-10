package auto.parto.api;

import auto.parto.dto.SellerDto;
import auto.parto.service.iservice.ISellerService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
