package auto.parto.service.iservice;

import auto.parto.dto.OtpRequestDto;
import auto.parto.dto.SellerDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ISellerService {
    Mono<SellerDto> create(@Valid SellerDto sellerDto);

    Mono<SellerDto> create(@Valid OtpRequestDto request , String otp);

    Mono<SellerDto> getSellerById(String id);

    Mono<SellerDto> addFunds(String id, BigDecimal amount);

    Mono<SellerDto> deactivate(String id);

    Mono<Boolean> checkIfSellerExistUsingPhoneNumber(@NotNull @NotBlank String phoneNumber);

    Mono<SellerDto> updateGeneratedOtpByEmail(@Email String email, String sentOtp);

    Mono<SellerDto> findSellerByEmail(String email);
}
