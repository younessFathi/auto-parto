package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpExpiredException;
import auto.parto.exception.OtpInvalidException;
import auto.parto.exception.OtpLockedException;
import auto.parto.mapper.BuyerIdentityMapper;
import auto.parto.persistence.BuyerIdentityOtpRepository;
import auto.parto.persistence.BuyerIdentityRecord;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@ConditionalOnExpression("'${auto.parto.persistence.postgresql.buyer-identity.enabled:false}' == 'true'")
public final class BuyerIdentityStrategy implements AuthStrategy, OtpValidationStrategy<BuyerDto> {

    private final BuyerIdentityOtpRepository repository;
    private final BuyerIdentityMapper mapper;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes = 5;

    public BuyerIdentityStrategy(BuyerIdentityOtpRepository repository,
                                 BuyerIdentityMapper mapper,
                                 JwtTokenGenerator jwtTokenGenerator) {
        this.repository = repository;
        this.mapper = mapper;
        this.jwtTokenGenerator = jwtTokenGenerator;
    }

    @Override
    public Mono<Object> create(OtpRequestDto request, String sentOtp) {
        Instant expiresAt = Instant.now().plusSeconds(otpExpirationMinutes * 60L);
        return repository.findByEmail(request.getEmail())
                .flatMap(existing -> repository.replaceOtp(request.getEmail(), sentOtp, expiresAt))
                .switchIfEmpty(Mono.defer(() -> repository.create(
                        request.getEmail(), request.getFirstName(), request.getLastName(),
                        request.getPhoneNumber(), sentOtp, expiresAt)))
                .map(mapper::toDto)
                .cast(Object.class);
    }

    @Override
    public Mono<TokenDto<BuyerDto>> validate(String otp, String email) {
        Instant now = Instant.now();
        return repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)))
                .flatMap(identity -> validateAndConsume(identity, otp, email, now))
                .map(mapper::toDto)
                .flatMap(buyer -> jwtTokenGenerator.encode(buyer.getEmail(), buyer, CustomerType.BUYER))
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.BUYER;
    }

    private Mono<BuyerIdentityRecord> validateAndConsume(BuyerIdentityRecord identity,
                                                         String otp, String email, Instant now) {
        if (!identity.active()) return Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP));
        if (identity.lockedUntil() != null && identity.lockedUntil().isAfter(now)) {
            return Mono.error(new OtpLockedException("Account is locked due to too many failed attempts"));
        }
        if (identity.otpExpiresAt() == null || !identity.otpExpiresAt().isAfter(now)) {
            return Mono.error(new OtpExpiredException(Constants.Exception.EXPIRED_OTP));
        }
        return repository.consumeValidOtp(email, otp, now)
                .flatMap(valid -> valid ? Mono.just(identity)
                        : repository.recordFailedAttempt(email, now)
                        .then(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP))));
    }
}
