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
    private final IBuyerService buyerService;

    @PutMapping("/{id}/deactivate")
    public Mono<BuyerDto> deactivateSeller(@PathVariable String id) {
        return buyerService.deactivateBuyer(id);
    }
}
