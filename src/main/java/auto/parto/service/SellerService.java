package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.repository.ISellerRepository;
import auto.parto.service.iservice.ISellerService;
import auto.parto.utils.JwtTokenGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class SellerService implements ISellerService {
    private ISellerRepository sellerRepository;
    private SellerDbMapper sellerDbMapper;

    @Override
    public Mono<SellerDto> create(SellerDto sellerDto) {
        return sellerRepository.save(sellerDbMapper.toEntity(sellerDto))
                                .map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> create(OtpRequestDto request, String otp) {
        return sellerRepository.save(sellerDbMapper.toEntity(request, otp)).map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> getSellerById(String id) {
        log.info("Getting seller by id: {}", id);
        return sellerRepository.getSellerById(id)
                                .map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> addFunds(String id, BigDecimal amount) {
        log.info("Adding funds to seller with id: {}", id);
        return sellerRepository.getSellerById(id)
                .flatMap(seller -> {
                    seller.getWallet().addFunds(amount);
                    return sellerRepository.save(seller)
                            .map(sellerDbMapper::toDto);
                });
    }

    @Override
    public Mono<SellerDto> deactivate(String id) {
        return sellerRepository.getSellerById(id)
                .flatMap(seller -> {
                    seller.setIsActive(false); // Assuming there is an 'active' field in Seller entity
                    return sellerRepository.save(seller)
                            .map(sellerDbMapper::toDto);
                }); // Assuming there is a constructor to convert entity to DTO
    }

    @Override
    public Mono<Boolean> checkIfSellerExistUsingEmail(String email) {
        return sellerRepository.exists(email, "email");
    }

    @Override
    public Mono<SellerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        return sellerRepository.updateGeneratedOtp(
                            email,
                            sentOtp,
                            JwtTokenGenerator.keyGenerator(email),
                        "email"
                ).map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> findSellerByEmail(String email) {
        return sellerRepository.findBy(email, "email").map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> activateUser(SellerDto sellerDto) {
        log.info("Activate seller with id {}" , sellerDto.getId());
        sellerDto.setIsActive(true);
        return sellerRepository.save(sellerDbMapper.toEntity(sellerDto)).map(sellerDbMapper::toDto);
    }

    public Mono<List<String>> findMatchingSellers() {
        return sellerRepository.findMatchingSellers();
    }
}
