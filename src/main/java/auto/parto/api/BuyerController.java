package auto.parto.api;

import auto.parto.dto.BuyerDto;
import auto.parto.service.iservice.IBuyerService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("customers")
@AllArgsConstructor
public class BuyerController {
    private final IBuyerService customerService;

    @PutMapping("/{id}/deactivate")
    public Mono<BuyerDto> deactivateSeller(@PathVariable String id) {
        return customerService.deactivateBuyer(id);
    }

    @PostMapping("/create/profile")
    public Mono<BuyerDto> createProfile(@RequestBody BuyerDto buyerDto) {
        return customerService.createBuyerFromClerkAuth(buyerDto);
    }


}
