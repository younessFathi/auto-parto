package auto.parto.service;

import auto.parto.dto.SellerDto;
import auto.parto.mapper.SellerMapper;
import auto.parto.repository.ISellerRepository;
import auto.parto.service.iservice.ISellerService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
@Slf4j
public class SellerService implements ISellerService {
    private ISellerRepository sellerRepository;

    @Override
    public Mono<SellerDto> create(SellerDto sellerDto) {
        return sellerRepository.save(SellerMapper.toEntity(sellerDto))
                                .map(SellerMapper::toDto);
    }

    @Override
    public Mono<SellerDto> getSellerById(String id) {
        log.info("Getting seller by id: {}", id);
        return sellerRepository.getSellerById(id)
                                .map(SellerMapper::toDto);
    }
}
