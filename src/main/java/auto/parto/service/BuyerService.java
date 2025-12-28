package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import auto.parto.mapper.BuyerDbMapper;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.repository.ISellerRepository;
import auto.parto.repository.mongo_repository.BuyerRepository;
import auto.parto.service.iservice.IBuyerService;
import auto.parto.utils.JwtTokenGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
@Slf4j
public class BuyerService implements IBuyerService {
    private final BuyerRepository buyerRepository;
    private final ISellerRepository sellerRepository;
    private final BuyerDbMapper buyerDbMapper;
    private final SellerDbMapper sellerDbMapper;

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingPhoneNumber(String phoneNumber) {
        return buyerRepository.exists(phoneNumber, "phoneNumber");
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingEmail(String email) {
        return buyerRepository.exists(email, "email");
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingId(String id) {
        log.info("Check if customer exist using id {} " , id);
        return buyerRepository.existsById(id);
    }

    @Override
    public Mono<BuyerDto> deactivateBuyer(String id) {
        log.info("Deactivate customer by id : {} " , id);
        return buyerRepository.deactivateCustomer(id).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> createBuyerFromClerkAuth(BuyerDto customerDto) {
        return buyerRepository.save(
                buyerDbMapper.toEntity(customerDto)
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistClerkId(String userId) {
        return buyerRepository.exists(userId, "clerkId");
    }

    @Override
    public Mono<BuyerDto> create(OtpRequestDto otpRequestDto, String otp) {
        log.info("Creating new customer with email: {}", otpRequestDto.getEmail());
        return buyerRepository.save(
                buyerDbMapper.toEntity(otpRequestDto , otp)
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByPhoneNumber(String phoneNumber) {
        return buyerRepository.findBy(phoneNumber, "phoneNumber").map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp) {
        log.info("Updating generated OTP for phone number: {}", phoneNumber);
        return buyerRepository.updateGeneratedOtp(
                            phoneNumber, sentOtp ,
                            JwtTokenGenerator.keyGenerator(phoneNumber),
                        "phoneNumber"
                        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        return buyerRepository.updateGeneratedOtp(
                email, sentOtp ,
                JwtTokenGenerator.keyGenerator(email),
                "email"
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByEmail(String email) {
        return buyerRepository.findBy(email , "email").map(buyerDbMapper::toDto);
    }
}
