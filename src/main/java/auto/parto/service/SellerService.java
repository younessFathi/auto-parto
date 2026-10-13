package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.enums.CarPartType;
import auto.parto.enums.Currency;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.mapper.SellerDbMapper;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.SellerProfileRecord;
import auto.parto.persistence.SellerProfileRepository;
import auto.parto.persistence.WalletMutationResult;
import auto.parto.persistence.WalletRepository;
import auto.parto.persistence.SellerIdentityOtpRepository;
import auto.parto.repository.ISellerRepository;
import auto.parto.service.iservice.ISellerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class SellerService implements ISellerService {
    private final ISellerRepository sellerRepository;
    private final SellerDbMapper sellerDbMapper;
    private final AppUserRepository postgresqlUserRepository;
    private final SellerProfileRepository postgresqlProfileRepository;
    private final WalletRepository postgresqlWalletRepository;
    private final SellerIdentityOtpRepository postgresqlOtpRepository;
    private final SellerIdentityMapper sellerIdentityMapper;
    @org.springframework.beans.factory.annotation.Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes = 5;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    public SellerService(ISellerRepository sellerRepository, SellerDbMapper sellerDbMapper) {
        this(sellerRepository, sellerDbMapper, (AppUserRepository) null,
                (SellerProfileRepository) null, (WalletRepository) null, null, null);
    }

    @Autowired
    public SellerService(ObjectProvider<ISellerRepository> sellerProvider, SellerDbMapper sellerDbMapper,
                         ObjectProvider<AppUserRepository> userProvider,
                         ObjectProvider<SellerProfileRepository> profileProvider,
                         ObjectProvider<WalletRepository> walletProvider,
                         ObjectProvider<SellerIdentityOtpRepository> otpProvider,
                         SellerIdentityMapper sellerIdentityMapper) {
        this(getIfAvailable(sellerProvider), sellerDbMapper,
                getIfAvailable(userProvider), getIfAvailable(profileProvider), getIfAvailable(walletProvider),
                getIfAvailable(otpProvider), sellerIdentityMapper);
    }

    public SellerService(ISellerRepository sellerRepository, SellerDbMapper sellerDbMapper,
                         AppUserRepository userRepository, SellerProfileRepository profileRepository,
                         WalletRepository walletRepository, SellerIdentityOtpRepository otpRepository,
                         SellerIdentityMapper sellerIdentityMapper) {
        this.sellerRepository = sellerRepository;
        this.sellerDbMapper = sellerDbMapper;
        this.postgresqlUserRepository = userRepository;
        this.postgresqlProfileRepository = profileRepository;
        this.postgresqlWalletRepository = walletRepository;
        this.postgresqlOtpRepository = otpRepository;
        this.sellerIdentityMapper = sellerIdentityMapper;
    }

    public SellerService(ISellerRepository sellerRepository, SellerDbMapper sellerDbMapper,
                         AppUserRepository userRepository, SellerProfileRepository profileRepository,
                         WalletRepository walletRepository) {
        this(sellerRepository, sellerDbMapper, userRepository, profileRepository, walletRepository, null, null);
    }

    @Override
    public Mono<SellerDto> create(SellerDto sellerDto) {
        if (postgresqlIdentityReady()) return canonicalOnboardingRequired();
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.save(sellerDbMapper.toEntity(sellerDto))
                                .map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> create(OtpRequestDto request, String otp) {
        if (postgresqlIdentityReady()) return canonicalOnboardingRequired();
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.save(sellerDbMapper.toEntity(request, otp)).map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> getSellerById(String id) {
        log.info("Getting seller by id: {}", id);
        UUID publicId = parseUuid(id);
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.findByPublicId(publicId)
                    .filter(user -> "SELLER".equals(user.role()))
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(user.publicId()))
                    .map(result -> toDto(result.getT1(), result.getT2(), null));
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.getSellerById(id)
                                .map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> addFunds(String id, BigDecimal amount) {
        return addFunds(id, amount, Currency.MAD, null);
    }

    @Override
    public Mono<SellerDto> addFunds(String id, BigDecimal amount, Currency currency, String idempotencyKey) {
        log.info("Adding funds to seller with id: {}", id);
        UUID publicId = parseUuid(id);
        if (postgresqlReady() && publicId != null) {
            if (currency == null) return Mono.error(new IllegalArgumentException("currency is required"));
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return Mono.error(new IllegalArgumentException("Idempotency-Key is required for PostgreSQL wallet mutations"));
            }
            long amountMinor = toMinorUnits(amount, currency);
            String operation = amountMinor >= 0 ? "CREDIT" : "DEBIT";
            return postgresqlWalletRepository.mutate(publicId, currency.name(), amountMinor,
                            operation, idempotencyKey, "seller-wallet-api")
                    .flatMap(mutation -> postgresqlUserRepository.findByPublicId(publicId)
                            .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(publicId))
                            .map(result -> toDto(result.getT1(), result.getT2(), mutation)))
                    .switchIfEmpty(Mono.error(new InsufficientBalanceException("Seller wallet not found")))
                    .onErrorMap(error -> error instanceof IllegalStateException
                            ? new InsufficientBalanceException("Insufficient balance") : error);
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.addFunds(id, amount)
                .switchIfEmpty(Mono.error(new InsufficientBalanceException("Insufficient balance")))
                .map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> deactivate(String id) {
        UUID publicId = parseUuid(id);
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.updateStatus(publicId, "INACTIVE")
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(publicId))
                    .map(result -> toDto(result.getT1(), result.getT2(), null));
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.getSellerById(id)
                .flatMap(seller -> {
                    seller.setIsActive(false); // Assuming there is an 'active' field in Seller entity
                    return sellerRepository.save(seller)
                            .map(sellerDbMapper::toDto);
                }); // Assuming there is a constructor to convert entity to DTO
    }

    @Override
    public Mono<Boolean> checkIfSellerExistUsingEmail(String email) {
        if (postgresqlIdentityReady()) {
            return postgresqlUserRepository.findByEmail(email)
                    .filter(user -> "SELLER".equals(user.role()) && "ACTIVE".equals(user.status()))
                    .hasElement();
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.exists(email, "email");
    }

    @Override
    public Mono<SellerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        log.info("Updating generated OTP for email: {}", email);
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.replaceOtp(email, sentOtp,
                            java.time.Instant.now().plusSeconds(otpExpirationMinutes * 60L))
                    .map(sellerIdentityMapper::toDto);
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.updateGeneratedOtp(
                            email,
                            sentOtp,
                        "email"
                ).map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> findSellerByEmail(String email) {
        if (postgresqlIdentityReady()) {
            return postgresqlProfileRepository.findByEmail(email)
                    .zipWhen(profile -> postgresqlUserRepository.findByPublicId(profile.userPublicId()))
                    .map(result -> toDto(result.getT2(), result.getT1(), null));
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.findBy(email, "email").map(sellerDbMapper::toDto);
    }

    @Override
    public Mono<SellerDto> activateUser(SellerDto sellerDto) {
        log.info("Activate seller with id {}" , sellerDto.getId());
        UUID publicId = parseUuid(sellerDto.getId());
        if (postgresqlIdentityReady() && publicId != null) {
            return postgresqlUserRepository.updateStatus(publicId, "ACTIVE")
                    .filter(user -> "SELLER".equals(user.role()))
                    .zipWhen(user -> postgresqlProfileRepository.findByUserPublicId(publicId))
                    .map(result -> toDto(result.getT1(), result.getT2(), null));
        }
        if (postgresqlIdentityReady() && publicId == null) return canonicalOnboardingRequired();
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        sellerDto.setIsActive(true);
        return sellerRepository.save(sellerDbMapper.toEntity(sellerDto)).map(sellerDbMapper::toDto);
    }

    public Mono<List<String>> findMatchingSellers() {
        if (postgresqlIdentityReady()) {
            return postgresqlUserRepository.findPublicIdsByRole("SELLER", 1000)
                    .map(UUID::toString).collectList();
        }
        if (sellerRepository == null) return legacyPersistenceUnavailable();
        return sellerRepository.findMatchingSellers();
    }

    @Override
    public Mono<SellerDto> recordFailedOtpAttempt(String email) {
        if (postgresqlOtpRepository != null) {
            return postgresqlOtpRepository.recordFailedAttempt(email, java.time.Instant.now())
                    .then(postgresqlOtpRepository.findByEmail(email))
                    .map(sellerIdentityMapper::toDto);
        }
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

    private boolean postgresqlReady() {
        return postgresqlUserRepository != null && postgresqlProfileRepository != null
                && postgresqlWalletRepository != null;
    }

    private boolean postgresqlIdentityReady() {
        return postgresqlUserRepository != null && postgresqlProfileRepository != null;
    }

    private SellerDto toDto(AppUserRecord user, SellerProfileRecord profile, WalletMutationResult mutation) {
        Currency currency = mutation == null ? null : Currency.valueOf(mutation.wallet().currency());
        WalletDto wallet = mutation == null ? null : WalletDto.builder()
                .id(mutation.wallet().publicId().toString())
                .balance(fromMinorUnits(mutation.wallet().balanceMinor(), currency))
                .currency(currency)
                .build();
        return SellerDto.builder()
                .id(user.publicId().toString())
                .name(joinName(profile.firstName(), profile.lastName()))
                .email(user.email())
                .phoneNumber(profile.phoneNumber())
                .description(profile.description())
                .walletDto(wallet)
                .isActive("ACTIVE".equals(user.status()))
                .createAt(user.createdAt() == null ? null : LocalDateTime.ofInstant(user.createdAt(), java.time.ZoneOffset.UTC))
                .modifiedAt(user.updatedAt() == null ? null : LocalDateTime.ofInstant(user.updatedAt(), java.time.ZoneOffset.UTC))
                .carPartType(parsePartType(profile.partType()))
                .build();
    }

    private static String joinName(String firstName, String lastName) {
        return java.util.stream.Stream.of(firstName, lastName).filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right).orElse("");
    }

    private static CarPartType parsePartType(String value) {
        if (value == null) return null;
        try { return CarPartType.valueOf(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static long toMinorUnits(BigDecimal amount, Currency currency) {
        if (amount == null) throw new IllegalArgumentException("amount is required");
        int scale = minorScale(currency);
        return amount.movePointRight(scale).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private static BigDecimal fromMinorUnits(long amount, Currency currency) {
        return BigDecimal.valueOf(amount, minorScale(currency));
    }

    private static int minorScale(Currency currency) {
        return switch (currency) {
            case JPY, KRW, VND, CLP -> 0;
            default -> 2;
        };
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
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
