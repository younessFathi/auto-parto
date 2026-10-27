package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.config.DomainMetrics;
import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.enums.CarPartType;
import auto.parto.enums.Currency;
import auto.parto.exception.InsufficientBalanceException;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.money.Money;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.SellerIdentityOtpRepository;
import auto.parto.persistence.SellerProfileRecord;
import auto.parto.persistence.SellerProfileRepository;
import auto.parto.persistence.WalletMutationResult;
import auto.parto.persistence.WalletRepository;
import auto.parto.service.iservice.ISellerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SellerService implements ISellerService {
    private final AppUserRepository userRepository;
    private final SellerProfileRepository profileRepository;
    private final WalletRepository walletRepository;
    private final SellerIdentityOtpRepository otpRepository;
    private final SellerIdentityMapper identityMapper;
    private final long otpExpirationMinutes;

    public SellerService(AppUserRepository userRepository, SellerProfileRepository profileRepository,
                         WalletRepository walletRepository, SellerIdentityOtpRepository otpRepository,
                         SellerIdentityMapper identityMapper,
                         @Value("${otp.expiration.minutes:5}") long otpExpirationMinutes) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.walletRepository = walletRepository;
        this.otpRepository = otpRepository;
        this.identityMapper = identityMapper;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }
    @Override public Mono<SellerDto> create(SellerDto sellerDto) { return onboardingRequired(); }
    @Override public Mono<SellerDto> create(OtpRequestDto request, String otp) { return onboardingRequired(); }
    @Override public Mono<SellerDto> getSellerById(String id) {
        UUID publicId = requireUuid(id);
        return userRepository.findByPublicId(publicId).filter(user -> "SELLER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(publicId))
                .map(result -> toDto(result.getT1(), result.getT2(), null));
    }
    @Override public Mono<SellerDto> addFunds(String id, BigDecimal amount) { return addFunds(id, amount, Currency.MAD, null); }
    @Override public Mono<SellerDto> addFunds(String id, BigDecimal amount, Currency currency, String idempotencyKey) {
        UUID publicId = requireUuid(id);
        if (currency == null) return Mono.error(new IllegalArgumentException("currency is required"));
        if (idempotencyKey == null || idempotencyKey.isBlank()) return Mono.error(new IllegalArgumentException("Idempotency-Key is required for PostgreSQL wallet mutations"));
        long amountMinor = Money.fromMajor(amount, currency).minorUnits();
        String operation = amountMinor >= 0 ? "CREDIT" : "DEBIT";
        Mono<SellerDto> mutation = walletRepository.mutate(publicId, currency.name(), amountMinor, operation,
                        idempotencyKey, "seller-wallet-api")
                .flatMap(walletMutation -> userRepository.findByPublicId(publicId).zipWhen(user -> profileRepository.findByUserPublicId(publicId))
                        .map(result -> toDto(result.getT1(), result.getT2(), walletMutation)))
                .switchIfEmpty(Mono.error(new InsufficientBalanceException("Seller wallet not found")))
                .onErrorMap(error -> error instanceof IllegalStateException ? new InsufficientBalanceException("Insufficient balance") : error);
        return DomainMetrics.observe("wallet", "mutation", mutation)
                .doOnSuccess(ignored -> DomainMetrics.walletMutation(operation.equals("CREDIT") ? "credit" : "debit", "success"))
                .doOnError(ignored -> DomainMetrics.walletMutation(operation.equals("CREDIT") ? "credit" : "debit", "failure"));
    }
    @Override public Mono<SellerDto> deactivate(String id) {
        UUID publicId = requireUuid(id);
        return userRepository.updateStatus(publicId, "INACTIVE").filter(user -> "SELLER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(publicId)).map(result -> toDto(result.getT1(), result.getT2(), null));
    }
    @Override public Mono<Boolean> checkIfSellerExistUsingEmail(String email) {
        return userRepository.findByEmail(email).filter(user -> "SELLER".equals(user.role()) && "ACTIVE".equals(user.status())).hasElement();
    }
    @Override public Mono<SellerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        return otpRepository.replaceOtp(email, sentOtp, expiresAt()).map(identityMapper::toDto);
    }
    @Override public Mono<SellerDto> findSellerByEmail(String email) {
        return profileRepository.findByEmail(email).zipWhen(profile -> userRepository.findByPublicId(profile.userPublicId()))
                .map(result -> toDto(result.getT2(), result.getT1(), null));
    }
    @Override public Mono<SellerDto> activateUser(SellerDto sellerDto) {
        UUID publicId = requireUuid(sellerDto.getId());
        return userRepository.updateStatus(publicId, "ACTIVE").filter(user -> "SELLER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(publicId)).map(result -> toDto(result.getT1(), result.getT2(), null));
    }
    public Mono<java.util.List<String>> findMatchingSellers() { return userRepository.findPublicIdsByRole("SELLER", 1000).map(UUID::toString).collectList(); }
    @Override public Mono<SellerDto> recordFailedOtpAttempt(String email) { return otpRepository.recordFailedAttempt(email, Instant.now()).then(otpRepository.findByEmail(email)).map(identityMapper::toDto); }
    @Override public Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now) { return otpRepository.consumeValidOtp(email, otp, now.toInstant(ZoneOffset.UTC)); }
    private Instant expiresAt() { return Instant.now().plusSeconds(otpExpirationMinutes * 60L); }
    private static <T> Mono<T> onboardingRequired() { return Mono.error(new IllegalStateException("PostgreSQL identity onboarding must be used")); }
    private static UUID requireUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException e) { throw new IllegalArgumentException("canonical seller ID must be a UUID", e); } }
    private static SellerDto toDto(AppUserRecord user, SellerProfileRecord profile, WalletMutationResult mutation) {
        Currency currency = mutation == null ? null : Currency.valueOf(mutation.wallet().currency());
        WalletDto wallet = mutation == null ? null : WalletDto.builder().id(mutation.wallet().publicId().toString()).balanceMinor(mutation.wallet().balanceMinor()).currency(currency).build();
        return SellerDto.builder().id(user.publicId().toString()).name(java.util.stream.Stream.of(profile.firstName(), profile.lastName()).filter(v -> v != null && !v.isBlank()).reduce((a, b) -> a + " " + b).orElse(""))
                .email(user.email()).phoneNumber(profile.phoneNumber()).description(profile.description()).walletDto(wallet).isActive("ACTIVE".equals(user.status()))
                .createAt(user.createdAt() == null ? null : LocalDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC)).modifiedAt(user.updatedAt() == null ? null : LocalDateTime.ofInstant(user.updatedAt(), ZoneOffset.UTC))
                .carPartType(parsePartType(profile.partType())).build();
    }
    private static CarPartType parsePartType(String value) { try { return value == null ? null : CarPartType.valueOf(value); } catch (RuntimeException e) { return null; } }
}
