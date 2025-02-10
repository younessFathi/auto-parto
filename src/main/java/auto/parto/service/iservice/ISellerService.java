package auto.parto.service.iservice;

import auto.parto.dto.SellerDto;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

public interface ISellerService {
    Mono<SellerDto> create(@Valid SellerDto sellerDto);
}
