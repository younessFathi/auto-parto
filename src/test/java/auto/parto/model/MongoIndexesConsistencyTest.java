package auto.parto.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MongoIndexesConsistencyTest {

    @Test
    void pieceDefinesCustomerFocusedIndexesForQueryAndPaginationPaths() {
        CompoundIndexes indexes = Piece.class.getAnnotation(CompoundIndexes.class);
        assertThat(indexes).isNotNull();

        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::name))
                .contains("piece_customer_created_idx", "piece_customer_status_idx");
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::def))
                .contains("{'customerId': 1, 'createAt': -1}", "{'customerId': 1, 'status': 1}");
    }

    @Test
    void pieceOfferDefinesPieceAndCustomerStatusIndexesForOfferQueries() {
        CompoundIndexes indexes = PieceOffer.class.getAnnotation(CompoundIndexes.class);
        assertThat(indexes).isNotNull();

        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::name))
                .contains("offer_piece_created_idx", "offer_customer_status_idx");
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::def))
                .contains("{'pieceId': 1, 'createAt': -1}", "{'customerId': 1, 'status': 1}");
    }
}
