package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.repository.ISellerRepository;
import auto.parto.service.iservice.ISellerService;
import auto.parto.utils.JwtTokenGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
public class SellerService implements ISellerService {
    private ISellerRepository sellerRepository;
    private SellerDbMapper sellerDbMapper;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

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
                    BigDecimal currentBalance = seller.getWallet().getBalance();
                    BigDecimal newBalance = currentBalance.add(amount);
                    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                        log.warn("Insufficient balance for seller {}: current={}, requested={}",
                                id, currentBalance, amount);
                        throw new InsufficientBalanceException("Insufficient balance");
                    }
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

    @Override
    public Mono<SellerDto> recordFailedOtpAttempt(String email) {
        return findSellerByEmail(email)
                .flatMap(seller -> {
                    var otpDetails = seller.getOtpDetailsDto();
                    int newFailedAttempts = (otpDetails != null ? otpDetails.getFailedAttempts() : 0) + 1;
                    final LocalDateTime newLockedUntil = newFailedAttempts >= MAX_FAILED_ATTEMPTS
                            ? LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES) : null;
                    if (newLockedUntil != null) {
                        log.info("Seller {} locked due to {} failed OTP attempts", email, newFailedAttempts);
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
                                return sellerRepository.save(sellerDbMapper.toEntity(updated))
                                        .map(sellerDbMapper::toDto);
                            });
                });
    }
}
