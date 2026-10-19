package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.mapper.BuyerDbMapper;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.mapper.BuyerIdentityMapper;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.persistence.BuyerIdentityOtpRepository;
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
    private final BuyerIdentityOtpRepository postgresqlOtpRepository;
    private final BuyerIdentityMapper buyerIdentityMapper;
    @org.springframework.beans.factory.annotation.Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes = 5;
    public BuyerService(BuyerRepository buyerRepository, ISellerRepository sellerRepository,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper) {
        this(buyerRepository, sellerRepository, buyerDbMapper, sellerDbMapper,
                (AppUserRepository) null, (BuyerProfileRepository) null, null, null);
    }

    @Autowired
    public BuyerService(ObjectProvider<BuyerRepository> buyerProvider, ObjectProvider<ISellerRepository> sellerProvider,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper,
                        ObjectProvider<AppUserRepository> userProvider,
                        ObjectProvider<BuyerProfileRepository> profileProvider,
                        ObjectProvider<BuyerIdentityOtpRepository> otpProvider,
                        BuyerIdentityMapper buyerIdentityMapper) {
        this(getIfAvailable(buyerProvider), getIfAvailable(sellerProvider), buyerDbMapper, sellerDbMapper,
                getIfAvailable(userProvider), getIfAvailable(profileProvider), getIfAvailable(otpProvider),
                buyerIdentityMapper);
    }

    public BuyerService(BuyerRepository buyerRepository, ISellerRepository sellerRepository,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper,
                        AppUserRepository userRepository, BuyerProfileRepository profileRepository,
                        BuyerIdentityOtpRepository otpRepository, BuyerIdentityMapper buyerIdentityMapper) {
        this.buyerRepository = buyerRepository;
        this.sellerRepository = sellerRepository;
        this.buyerDbMapper = buyerDbMapper;
        this.sellerDbMapper = sellerDbMapper;
        this.postgresqlUserRepository = userRepository;
        this.postgresqlProfileRepository = profileRepository;
        this.postgresqlOtpRepository = otpRepository;
        this.buyerIdentityMapper = buyerIdentityMapper;
    }

    public BuyerService(BuyerRepository buyerRepository, ISellerRepository sellerRepository,
                        BuyerDbMapper buyerDbMapper, SellerDbMapper sellerDbMapper,
                        AppUserRepository userRepository, BuyerProfileRepository profileRepository) {
        this(buyerRepository, sellerRepository, buyerDbMapper, sellerDbMapper,
                userRepository, profileRepository, null, null);
    }

    @Override
    public Mono<Boolean> checkIfBuyerExistUsingPhoneNumber(String phoneNumber) {
        if (postgresqlOtpRepository != null) return postgresqlOtpRepository.findByPhoneNumber(phoneNumber).hasElement();
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
        if (postgresqlIdentityReady()) {
            return postgresqlUserRepository.findByProviderSubject("clerk", userId)
                    .filter(user -> "BUYER".equals(user.role()) && "ACTIVE".equals(user.status()))
                    .hasElement();
        }
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
        var buyer = buyerDbMapper.toEntity(otpRequestDto, otp);
        buyer.getOtpDetails().setExpiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        return buyerRepository.save(buyer).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> findBuyerByPhoneNumber(String phoneNumber) {
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.findByPhoneNumber(phoneNumber).map(buyerIdentityMapper::toDto);
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.findBy(phoneNumber, "phoneNumber").map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp) {
        log.info("Updating generated OTP for phone number: {}", phoneNumber);
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.findByPhoneNumber(phoneNumber)
                    .flatMap(identity -> postgresqlOtpRepository.replaceOtp(identity.email(), sentOtp,
                            java.time.Instant.now().plusSeconds(otpExpirationMinutes * 60L)))
                    .map(buyerIdentityMapper::toDto);
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.updateGeneratedOtp(
                            phoneNumber, sentOtp, "phoneNumber",
                        LocalDateTime.now().plusMinutes(otpExpirationMinutes)
                        ).map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.replaceOtp(email, sentOtp,
                            java.time.Instant.now().plusSeconds(otpExpirationMinutes * 60L))
                    .map(buyerIdentityMapper::toDto);
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.updateGeneratedOtp(
                email, sentOtp, "email",
                LocalDateTime.now().plusMinutes(otpExpirationMinutes)
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
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.recordFailedAttempt(email, java.time.Instant.now())
                    .then(postgresqlOtpRepository.findByEmail(email))
                    .map(buyerIdentityMapper::toDto);
        }
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.recordFailedOtpAttempt(email, LocalDateTime.now())
                .map(buyerDbMapper::toDto);
    }

    @Override
    public Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now) {
        if (buyerRepository == null) return legacyPersistenceUnavailable();
        return buyerRepository.consumeValidOtp(email, otp, now);
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
