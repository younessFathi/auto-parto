package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.mapper.BuyerDbMapper;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.repository.ISellerRepository;
import auto.parto.repository.mongo_repository.BuyerRepository;
import auto.parto.service.iservice.IBuyerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@Slf4j
public class BuyerService implements IBuyerService {
    private final BuyerRepository buyerRepository;
    private final ISellerRepository sellerRepository;
    private final BuyerDbMapper buyerDbMapper;
    private final SellerDbMapper sellerDbMapper;
    private final AppUserRepository postgresqlUserRepository;
    private final BuyerProfileRepository postgresqlProfileRepository;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    public BuyerService(BuyerRepository buyerRepository, ISellerRepository sellerRepository,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper) {
        this(buyerRepository, sellerRepository, buyerDbMapper, sellerDbMapper,
                (AppUserRepository) null, (BuyerProfileRepository) null);
    }

    @Autowired
    public BuyerService(ObjectProvider<BuyerRepository> buyerProvider, ObjectProvider<ISellerRepository> sellerProvider,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper,
                        ObjectProvider<AppUserRepository> userProvider,
                        ObjectProvider<BuyerProfileRepository> profileProvider) {
        this(getIfAvailable(buyerProvider), getIfAvailable(sellerProvider), buyerDbMapper, sellerDbMapper,
                getIfAvailable(userProvider), getIfAvailable(profileProvider));
    }

    public BuyerService(BuyerRepository buyerRepository, ISellerRepository sellerRepository,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper,
                        AppUserRepository userRepository, BuyerProfileRepository profileRepository) {
        this.buyerRepository = buyerRepository;
        this.sellerRepository = sellerRepository;
        this.buyerDbMapper = buyerDbMapper;
        this.sellerDbMapper = sellerDbMapper;
        this.postgresqlUserRepository = userRepository;
        this.postgresqlProfileRepository = profileRepository;
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingPhoneNumber(String phoneNumber) {
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.exists(phoneNumber, "phoneNumber");
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingEmail(String email) {
        if (postgresqlIdentityReady()) {
            return postgresqlUserRepository.findByEmail(email)
                    .filter(user -> "BUYER".equals(user.role()) && "ACTIVE".equals(user.status()))
                    .hasElement();
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.exists(email, "email");
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingId(String id) {
        log.info("Check if customer exist using id {} " , id);
        UUID publicId = parseUuid(id);
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.findByPublicId(publicId)
                    .filter(user -> "BUYER".equals(user.role()) && "ACTIVE".equals(user.status()))
                    .hasElement();
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.existsById(id);
    }

    @Override
    public Mono<BuyerDto> deactivateBuyer(String id) {
        log.info("Deactivate customer by id : {} " , id);
        UUID publicId = parseUuid(id);
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.updateStatus(publicId, "INACTIVE")
                    .filter(user -> "BUYER".equals(user.role()))
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(publicId))
                    .map(result -> toDto(result.getT1(), result.getT2()));
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.deactivateCustomer(id).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> createBuyerFromClerkAuth(BuyerDto customerDto) {
        if (postgresqlIdentityReady()) return canonicalOnboardingRequired();
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.save(
                buyerDbMapper.toEntity(customerDto)
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistClerkId(String userId) {
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.exists(userId, "clerkId");
    }

    @Override
    public Mono<BuyerDto> activateUser(BuyerDto buyerDto) {
        log.info("Activate buyer with id {} " , buyerDto.getId());
        UUID publicId = parseUuid(buyerDto.getId());
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.updateStatus(publicId, "ACTIVE")
                    .filter(user -> "BUYER".equals(user.role()))
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(publicId))
                    .map(result -> toDto(result.getT1(), result.getT2()));
        }
        buyerDto.setIsActive(true);
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.save(buyerDbMapper.toEntity(buyerDto)).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> create(OtpRequestDto otpRequestDto, String otp) {
        log.info("Creating new customer with email: {}", otpRequestDto.getEmail());
        if (postgresqlIdentityReady()) return canonicalOnboardingRequired();
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.save(
                buyerDbMapper.toEntity(otpRequestDto , otp)
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByPhoneNumber(String phoneNumber) {
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.findBy(phoneNumber, "phoneNumber").map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp) {
        log.info("Updating generated OTP for phone number: {}", phoneNumber);
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.updateGeneratedOtp(
                            phoneNumber, sentOtp ,
                        "phoneNumber"
                        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.updateGeneratedOtp(
                email, sentOtp ,
                "email"
        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByEmail(String email) {
        if (postgresqlIdentityReady()) {
            return postgresqlUserRepository.findByEmail(email)
                    .filter(user -> "BUYER".equals(user.role()))
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(user.publicId()))
                    .map(result -> toDto(result.getT1(), result.getT2()));
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
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
                                if (buyerRepository == null) return legacyPersistenceUnavailable();
                                return buyerRepository.save(buyerDbMapper.toEntity(updated))
                                        .map(buyerDbMapper::toDto);
                            });
                });
    }

    private boolean postgresqlIdentityReady() {
        return postgresqlUserRepository != null && postgresqlProfileRepository != null;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BuyerDto toDto(AppUserRecord user, BuyerProfileRecord profile) {
        return BuyerDto.builder()
                .id(user.publicId().toString())
                .firstName(profile.firstName())
                .lastName(profile.lastName())
                .email(user.email())
                .phoneNumber(profile.phoneNumber())
                .picture(profile.pictureUrl())
                .isActive("ACTIVE".equals(user.status()))
                .keyVersion(user.keyVersion())
                .createAt(user.createdAt() == null ? null : LocalDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC))
                .updatedAt(user.updatedAt() == null ? null : LocalDateTime.ofInstant(user.updatedAt(), ZoneOffset.UTC))
                .build();
    }

    private static <T> T getIfAvailable(ObjectProvider<T> provider) {
        return provider == null ? null : provider.getIfAvailable();
    }

    private static <T> Mono<T> legacyPersistenceUnavailable() {
        return Mono.error(new IllegalStateException("MongoDB persistence is disabled; use the PostgreSQL identity boundary"));
    }

    private static <T> Mono<T> canonicalOnboardingRequired() {
        return Mono.error(new IllegalStateException("PostgreSQL identity onboarding must be used when the canonical boundary is enabled"));
    }
}
