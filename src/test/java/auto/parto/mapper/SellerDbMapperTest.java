package auto.parto.mapper;

import auto.parto.dto.AddressDto;
import auto.parto.dto.SellerDto;
import auto.parto.dto.WalletDto;
import auto.parto.enums.CarPartType;
import auto.parto.enums.Currency;
import auto.parto.model.Address;
import auto.parto.model.Seller;
import auto.parto.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SellerDbMapperTest {
    private SellerDbMapper sellerDbMapper;
    private Seller seller;
    private SellerDto sellerDto;

    @BeforeEach
    public void setUp() {
        sellerDbMapper = new SellerDbMapperImpl(new AddressDbMapperImpl(), new WalletDbMapperImpl());
        seller = Seller
                .builder()
                .id(UUID.randomUUID().toString())
                .email("youness@gmail.com")
                .name("Youness")
                .carPartType(CarPartType.BOTH)
                .description("Car parts seller")
                .createAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .isActive(Boolean.TRUE)
                .phoneNumber("0612345678")
                .address(Address
                        .builder()
                        .id(UUID.randomUUID().toString())
                        .street("123 Main St")
                        .city("Casablanca")
                        .state("Casablanca-Settat")
                        .country("Morocco")
                        .zipCode("20000")
                        .build())
                .wallet(Wallet.builder()
                        .id(UUID.randomUUID().toString())
                        .balance(new BigDecimal(1000))
                        .currency(Currency.MAD)
                        .build())
                .build();

        sellerDto = SellerDto
                .builder()
                .id(UUID.randomUUID().toString())
                .email("youness@gmail.com")
                .name("Youness")
                .carPartType(CarPartType.BOTH)
                .description("Car parts seller")
                .createAt(LocalDateTime.now())
                .modifiedAt(LocalDateTime.now())
                .isActive(Boolean.TRUE)
                .phoneNumber("0612345678")
                .addressDto(AddressDto
                        .builder()
                        .street("123 Main St")
                        .city("Casablanca")
                        .state("Casablanca-Settat")
                        .country("Morocco")
                        .zipCode("20000")
                        .build())
                .walletDto(WalletDto.builder()
                        .balance(new BigDecimal(1000))
                        .currency(Currency.MAD)
                        .build())
                .build();
    }

    @Test
    public void testToDto() {
        sellerDto = sellerDbMapper.toDto(seller);
        assertEquals(seller.getId(), sellerDto.getId());
        assertEquals(seller.getName(), sellerDto.getName());
        assertEquals(seller.getEmail(), sellerDto.getEmail());
        assertEquals(seller.getPhoneNumber(), sellerDto.getPhoneNumber());
        assertEquals(seller.getCarPartType(), sellerDto.getCarPartType());
        assertEquals(seller.getDescription(), sellerDto.getDescription());
        assertNotNull(seller.getAddress());
        assertNotNull(seller.getWallet());
        assertEquals(seller.getAddress().getStreet(), sellerDto.getAddressDto().getStreet());
        assertEquals(seller.getAddress().getCity(), sellerDto.getAddressDto().getCity());
        assertEquals(seller.getAddress().getState(), sellerDto.getAddressDto().getState());
        assertEquals(seller.getAddress().getCountry(), sellerDto.getAddressDto().getCountry());
        assertEquals(seller.getAddress().getZipCode(), sellerDto.getAddressDto().getZipCode());
        assertEquals(seller.getWallet().getBalance(), sellerDto.getWalletDto().getBalance());
        assertEquals(seller.getWallet().getCurrency(), sellerDto.getWalletDto().getCurrency());
        assertNotNull(sellerDto.getCreateAt());
    }

    @Test
    public void testToEntity(){
        seller = sellerDbMapper.toEntity(sellerDto);
        assertEquals(sellerDto.getId(), seller.getId());
        assertEquals(sellerDto.getName(), seller.getName());
        assertEquals(sellerDto.getEmail(), seller.getEmail());
        assertEquals(sellerDto.getPhoneNumber(), seller.getPhoneNumber());
        assertEquals(sellerDto.getCarPartType(), seller.getCarPartType());
        assertEquals(sellerDto.getDescription(), seller.getDescription());
        assertNotNull(sellerDto.getAddressDto());
        assertNotNull(sellerDto.getWalletDto());
        assertEquals(sellerDto.getAddressDto().getStreet(), seller.getAddress().getStreet());
        assertEquals(sellerDto.getAddressDto().getCity(), seller.getAddress().getCity());
        assertEquals(sellerDto.getAddressDto().getState(), seller.getAddress().getState());
        assertEquals(sellerDto.getAddressDto().getCountry(), seller.getAddress().getCountry());
        assertEquals(sellerDto.getAddressDto().getZipCode(), seller.getAddress().getZipCode());
        assertEquals(sellerDto.getWalletDto().getBalance(), seller.getWallet().getBalance());
        assertEquals(sellerDto.getWalletDto().getCurrency(), seller.getWallet().getCurrency());
        assertNotNull(seller.getCreateAt());
    }

}
