package auto.parto.service.iservice;

import auto.parto.dto.SellerDto;
import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ISellerService {
    Mono<SellerDto> create(@Valid SellerDto sellerDto);

    Mono<SellerDto> getSellerById(String id);

    Mono<SellerDto> addFunds(String id, BigDecimal amount);

    Mono<SellerDto> deactivate(String id);
}
