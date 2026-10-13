package auto.parto.service;

import auto.parto.dto.PieceDto;
import auto.parto.enums.Category;
import auto.parto.enums.Condition;
import auto.parto.mapper.PieceDbMapper;
import auto.parto.persistence.PartRequestRecord;
import auto.parto.persistence.PartRequestRepository;
import auto.parto.repository.IPieceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PieceServicePostgresqlRoutingTest {

    private static final UUID BUYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private final IPieceRepository mongoRepository = Mockito.mock(IPieceRepository.class);
    private final PieceDbMapper mapper = Mockito.mock(PieceDbMapper.class);
    private final PartRequestRepository postgresqlRepository = Mockito.mock(PartRequestRepository.class);
    private final PieceService service = new PieceService(mongoRepository, mapper, postgresqlRepository);

    @Test
    void createUsesPostgresqlForCanonicalBuyerId() {
        PieceDto request = PieceDto.builder()
                .customerId(BUYER_ID.toString())
                .name("Brake pad")
                .description("Front axle")
                .condition(Condition.NEW)
                .category(Category.CAR)
                .build();
        PartRequestRecord record = record("OPEN");
        when(postgresqlRepository.create(BUYER_ID, "Brake pad", "Front axle", "NEW", "CAR"))
                .thenReturn(Mono.just(record));

        PieceDto result = service.create(request).block();

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(REQUEST_ID.toString());
        assertThat(result.getStatus()).hasToString("PENDING");
        verify(mongoRepository, never()).save(Mockito.any());
    }

    @Test
    void listMapsPostgresqlRequestStatusAndCount() {
        when(postgresqlRepository.findByBuyerPublicId(BUYER_ID, 0, 10))
                .thenReturn(Flux.just(record("MATCHED")));
        when(postgresqlRepository.countByBuyerPublicId(BUYER_ID)).thenReturn(Mono.just(1L));

        var response = service.getAllPiecesByCustomerId(BUYER_ID.toString(), 0, 10).block();

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().getFirst().getStatus()).hasToString("IN_PROGRESS");
        assertThat(response.getTotalItems()).isEqualTo(1L);
        verify(mongoRepository, never()).getAllPieces(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    void nonUuidIdsKeepUsingMongoFallback() {
        when(mongoRepository.checkIfPieceById("legacy-id")).thenReturn(Mono.just(true));

        assertThat(service.checkIfPieceById("legacy-id").block()).isTrue();
        verify(postgresqlRepository, never()).findByPublicId(Mockito.any());
    }

    @Test
    void canonicalUpdateDoesNotWriteLegacyMongoImageRelationship() {
        PieceDto request = PieceDto.builder()
                .id(REQUEST_ID.toString())
                .imagesId(List.of(UUID.randomUUID().toString()))
                .build();
        when(postgresqlRepository.findByPublicId(REQUEST_ID)).thenReturn(Mono.just(record("OPEN")));

        PieceDto result = service.update(request).block();

        assertThat(result.getId()).isEqualTo(REQUEST_ID.toString());
        verify(mongoRepository, never()).update(Mockito.anyString(), Mockito.anyList());
    }

    @Test
    void canonicalUpdateRejectsLegacyImageIdentifier() {
        PieceDto request = PieceDto.builder().id(REQUEST_ID.toString()).imagesId(List.of("legacy-image")).build();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.update(request).block()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical image IDs");
        verify(postgresqlRepository, never()).findByPublicId(Mockito.any());
        verify(mongoRepository, never()).update(Mockito.anyString(), Mockito.anyList());
    }

    private static PartRequestRecord record(String status) {
        Instant timestamp = Instant.parse("2026-10-13T10:00:00Z");
        return new PartRequestRecord(REQUEST_ID, BUYER_ID, "Brake pad", "Front axle",
                Condition.NEW, Category.CAR, status, 4L, timestamp, timestamp);
    }
}
