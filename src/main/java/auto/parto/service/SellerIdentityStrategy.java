package auto.parto.service;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.TokenDto;
import auto.parto.enums.CustomerType;
import auto.parto.exception.OtpInvalidException;
import auto.parto.mapper.SellerIdentityMapper;
import auto.parto.persistence.SellerIdentityOtpRepository;
import auto.parto.persistence.SellerIdentityRecord;
import auto.parto.service.iservice.AuthStrategy;
import auto.parto.service.iservice.OtpValidationStrategy;
import auto.parto.utils.Constants;
import auto.parto.utils.JwtTokenGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@ConditionalOnProperty(prefix = "auto.parto.persistence.postgresql.identity", name = "enabled", havingValue = "true")
public final class SellerIdentityStrategy implements AuthStrategy, OtpValidationStrategy<SellerDto> {

    private final SellerIdentityOtpRepository repository;
    private final SellerIdentityMapper mapper;
    private final JwtTokenGenerator jwtTokenGenerator;

    @Value("${otp.expiration.minutes:5}")
    private long otpExpirationMinutes = 5;

    public SellerIdentityStrategy(SellerIdentityOtpRepository repository,
                                  SellerIdentityMapper mapper,
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
                        request.getEmail(), request.getFirstName(), request.getLastName(), sentOtp, expiresAt)))
                .map(mapper::toDto)
                .cast(Object.class);
    }

    @Override
    public Mono<TokenDto<SellerDto>> validate(String otp, String email) {
        Instant now = Instant.now();
        return repository.findByEmail(email)
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)))
                .flatMap(identity -> validateAndConsume(identity, otp, email, now))
                .map(mapper::toDto)
                .flatMap(seller -> jwtTokenGenerator.encode(seller.getEmail(), seller, CustomerType.SELLER))
                .switchIfEmpty(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP)));
    }

    @Override
    public CustomerType getSupportedType() {
        return CustomerType.SELLER;
    }

    private Mono<SellerIdentityRecord> validateAndConsume(SellerIdentityRecord identity,
                                                          String otp, String email, Instant now) {
        if (!identity.active()) return Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP));
        return repository.consumeValidOtp(email, otp, now)
                .flatMap(valid -> valid ? Mono.just(identity)
                        : repository.recordFailedAttempt(email, now)
                        .then(Mono.error(new OtpInvalidException(Constants.Exception.INVALID_OTP))));
    }
}
