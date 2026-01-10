package auto.parto.service.iservice;

import auto.parto.annotation.phoneNumber.ValidPhoneNumber;
import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import jakarta.validation.constraints.Email;
import reactor.core.publisher.Mono;

public interface IBuyerService {
    Mono<Boolean> checkIfBuyerExistUsingPhoneNumber(String phoneNumber);
    Mono<Boolean> checkIfBuyerExistUsingEmail(String email);
    Mono<BuyerDto> create(OtpRequestDto otpRequestDto , String otp);
    Mono<BuyerDto> findBuyerByPhoneNumber(@ValidPhoneNumber String phoneNumber);
    Mono<BuyerDto> updateGeneratedOtpByPhoneNumber(String phoneNumber, String sentOtp);
    Mono<BuyerDto> updateGeneratedOtpByEmail(String email, String sentOtp);
    Mono<BuyerDto> findBuyerByEmail(@Email String email);
    Mono<Boolean> checkIfBuyerExistUsingId(String id);
    Mono<BuyerDto> deactivateBuyer(String id);
    Mono<BuyerDto> createBuyerFromClerkAuth(BuyerDto customerDto);
    Mono<Boolean> checkIfBuyerExistClerkId(String userId);
    Mono<BuyerDto> activateUser(BuyerDto buyerDto);
}
