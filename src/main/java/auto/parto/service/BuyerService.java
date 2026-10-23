package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.mapper.BuyerIdentityMapper;
import auto.parto.persistence.AppUserRecord;
import auto.parto.persistence.AppUserRepository;
import auto.parto.persistence.BuyerIdentityOtpRepository;
import auto.parto.persistence.BuyerProfileRecord;
import auto.parto.persistence.BuyerProfileRepository;
import auto.parto.service.iservice.IBuyerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class BuyerService implements IBuyerService {
    private final AppUserRepository userRepository;
    private final BuyerProfileRepository profileRepository;
    private final BuyerIdentityOtpRepository otpRepository;
    private final BuyerIdentityMapper identityMapper;
    private final long otpExpirationMinutes;

    public BuyerService(AppUserRepository userRepository, BuyerProfileRepository profileRepository,
                        BuyerIdentityOtpRepository otpRepository, BuyerIdentityMapper identityMapper,
                        @Value("${otp.expiration.minutes:5}") long otpExpirationMinutes) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.otpRepository = otpRepository;
        this.identityMapper = identityMapper;
        this.otpExpirationMinutes = otpExpirationMinutes;
    }

    @Override public Mono<Boolean> checkIfBuyerExistUsingPhoneNumber(String phoneNumber) {
        return otpRepository.findByPhoneNumber(phoneNumber).hasElement();
    }
    @Override public Mono<Boolean> checkIfBuyerExistUsingEmail(String email) {
        return userRepository.findByEmail(email).filter(this::activeBuyer).hasElement();
    }
    @Override public Mono<Boolean> checkIfBuyerExistUsingId(String id) {
        return parseUuid(id).map(userRepository::findByPublicId).orElseGet(Mono::empty)
                .filter(this::activeBuyer).hasElement();
    }
    @Override public Mono<BuyerDto> deactivateBuyer(String id) {
        UUID publicId = requireUuid(id);
        return userRepository.updateStatus(publicId, "INACTIVE").filter(user -> "BUYER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(publicId))
                .map(result -> toDto(result.getT1(), result.getT2()));
    }
    @Override public Mono<BuyerDto> createBuyerFromClerkAuth(BuyerDto customerDto) {
        return onboardingRequired();
    }
    @Override public Mono<Boolean> checkIfBuyerExistClerkId(String userId) {
        return userRepository.findByProviderSubject("clerk", userId)
                .filter(this::activeBuyer).hasElement();
    }
    @Override public Mono<BuyerDto> activateUser(BuyerDto buyerDto) {
        UUID publicId = requireUuid(buyerDto.getId());
        return userRepository.updateStatus(publicId, "ACTIVE").filter(user -> "BUYER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(publicId))
                .map(result -> toDto(result.getT1(), result.getT2()));
    }
    @Override public Mono<BuyerDto> create(OtpRequestDto request, String otp) { return onboardingRequired(); }
    @Override public Mono<BuyerDto> findBuyerByPhoneNumber(String phoneNumber) {
        return otpRepository.findByPhoneNumber(phoneNumber).map(identityMapper::toDto);
    }
    @Override public Mono<BuyerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp) {
        return otpRepository.findByPhoneNumber(phoneNumber)
                .flatMap(identity -> otpRepository.replaceOtp(identity.email(), sentOtp, expiresAt()))
                .map(identityMapper::toDto);
    }
    @Override public Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp) {
        return otpRepository.replaceOtp(email, sentOtp, expiresAt()).map(identityMapper::toDto);
    }
    @Override public Mono<BuyerDto> findBuyerByEmail(String email) {
        return userRepository.findByEmail(email).filter(user -> "BUYER".equals(user.role()))
                .zipWhen(user -> profileRepository.findByUserPublicId(user.publicId()))
                .map(result -> toDto(result.getT1(), result.getT2()));
    }
    @Override public Mono<BuyerDto> recordFailedOtpAttemptByEmail(String email) {
        return otpRepository.recordFailedAttempt(email, Instant.now()).then(otpRepository.findByEmail(email))
                .map(identityMapper::toDto);
    }
    @Override public Mono<Boolean> consumeValidOtp(String email, String otp, LocalDateTime now) {
        return otpRepository.consumeValidOtp(email, otp, now.toInstant(ZoneOffset.UTC));
    }
    private boolean activeBuyer(AppUserRecord user) { return "BUYER".equals(user.role()) && "ACTIVE".equals(user.status()); }
    private Instant expiresAt() { return Instant.now().plusSeconds(otpExpirationMinutes * 60L); }
    private static <T> Mono<T> onboardingRequired() { return Mono.error(new IllegalStateException("PostgreSQL identity onboarding must be used")); }
    private static UUID requireUuid(String value) { return parseUuid(value).orElseThrow(() -> new IllegalArgumentException("canonical buyer ID must be a UUID")); }
    private static Optional<UUID> parseUuid(String value) { try { return value == null ? Optional.empty() : Optional.of(UUID.fromString(value)); } catch (IllegalArgumentException e) { return Optional.empty(); } }
    private static BuyerDto toDto(AppUserRecord user, BuyerProfileRecord profile) {
        return BuyerDto.builder().id(user.publicId().toString()).firstName(profile.firstName()).lastName(profile.lastName())
                .email(user.email()).phoneNumber(profile.phoneNumber()).picture(profile.pictureUrl())
                .isActive("ACTIVE".equals(user.status())).keyVersion(user.keyVersion())
                .createAt(user.createdAt() == null ? null : LocalDateTime.ofInstant(user.createdAt(), ZoneOffset.UTC))
                .updatedAt(user.updatedAt() == null ? null : LocalDateTime.ofInstant(user.updatedAt(), ZoneOffset.UTC)).build();
    }
}
