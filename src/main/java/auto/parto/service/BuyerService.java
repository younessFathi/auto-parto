package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
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

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
@Slf4j
public class BuyerService implements IBuyerService {
    private final BuyerRepository buyerRepository;
    private final ISellerRepository sellerRepository;
    private final BuyerDbMapper buyerDbMapper;
    private final SellerDbMapper sellerDbMapper;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

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
    public Mono<BuyerDto> activateUser(BuyerDto buyerDto) {
        log.info("Activate buyer with id {} " , buyerDto.getId());
        buyerDto.setIsActive(true);
        return buyerRepository.save(buyerDbMapper.toEntity(buyerDto)).map(buyerDbMapper::toDto);
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
                        "phoneNumber"
                        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        return buyerRepository.updateGeneratedOtp(
                email, sentOtp ,
                "email"
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByEmail(String email) {
        return buyerRepository.findBy(email , "email").map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> recordFailedOtpAttemptByEmail(String email) {
        return findBuyerByEmail(email)
                .flatMap(buyer -> {
                    var otpDetails = buyer.getOtpDetailsDto();
                    int newFailedAttempts = (otpDetails != null ? otpDetails.getFailedAttempts() : 0) + 1;
                    final LocalDateTime newLockedUntil = newFailedAttempts >= MAX_FAILED_ATTEMPTS
                            ? LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES) : null;
                    if (newLockedUntil != null) {
                        log.info("Buyer {} locked due to {} failed OTP attempts", email, newFailedAttempts);
                    }
                    String existingOtp = otpDetails != null ? otpDetails.getOtp() : null;
                    LocalDateTime existingTimestamp = otpDetails != null ? otpDetails.getTimestamp() : LocalDateTime.now();
                    return updateGeneratedOtpByEmail(email, existingOtp)
                            .flatMap(updated -> {
                                updated.setOtpDetailsDto(auto.parto.dto.OtpDetailsDto.builder()
                                        .otp(existingOtp)
                                        .timestamp(existingTimestamp)
                                        .failedAttempts(newFailedAttempts)
                                        .lockedUntil(newLockedUntil)
                                        .build());
                                return buyerRepository.save(buyerDbMapper.toEntity(updated))
                                        .map(buyerDbMapper::toDto);
                            });
                });
    }
}
