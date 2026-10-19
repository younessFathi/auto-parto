package auto.parto.service;

import auto.parto.dto.BuyerDto;
import auto.parto.dto.OtpRequestDto;
import auto.parto.enums.CustomerType;
import auto.parto.service.iservice.IBuyerService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class BuyerAuthStrategyTest {

    @Test
    void existingBuyerChallengeIsKeyedByCanonicalEmail() {
        IBuyerService buyerService = mock(IBuyerService.class);
        BuyerAuthStrategy strategy = new BuyerAuthStrategy(buyerService);
        OtpRequestDto request = OtpRequestDto.builder()
                .email("attacker@example.test")
                .phoneNumber("+212600000001")
                .customerType(CustomerType.BUYER)
                .build();
        BuyerDto buyer = BuyerDto.builder().email(request.getEmail()).build();

        when(buyerService.checkIfBuyerExistUsingEmail(request.getEmail())).thenReturn(Mono.just(true));
        when(buyerService.updateGeneratedOtpByEmail(request.getEmail(), "123456")).thenReturn(Mono.just(buyer));

        assertThat(strategy.create(request, "123456").block()).isSameAs(buyer);
        verify(buyerService).updateGeneratedOtpByEmail(request.getEmail(), "123456");
        verify(buyerService, never()).updateGeneratedOtpByPhoneNumber(anyString(), anyString());
    }
}
