package auto.parto.model;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
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

    @Test
    void buyerDefinesIndexesOnCommonQueryFields() {
        assertThat(getField(Buyer.class, "email").getAnnotation(Indexed.class)).isNotNull();
        assertThat(getField(Buyer.class, "phoneNumber").getAnnotation(Indexed.class)).isNotNull();
        assertThat(getField(Buyer.class, "clerkId").getAnnotation(Indexed.class)).isNotNull();

        CompoundIndexes indexes = Buyer.class.getAnnotation(CompoundIndexes.class);
        assertThat(indexes).isNotNull();
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::name))
                .contains("buyer_email_active_idx");
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::def))
                .contains("{'email': 1, 'isActive': 1}");
    }

    @Test
    void sellerDefinesIndexesOnCommonQueryFields() {
        assertThat(getField(Seller.class, "email").getAnnotation(Indexed.class)).isNotNull();

        CompoundIndexes indexes = Seller.class.getAnnotation(CompoundIndexes.class);
        assertThat(indexes).isNotNull();
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::name))
                .contains("seller_email_active_idx");
        assertThat(Arrays.stream(indexes.value()).map(CompoundIndex::def))
                .contains("{'email': 1, 'isActive': 1}");
    }

    @Test
    void fileDefinesIndexOnPieceId() {
        assertThat(getField(File.class, "pieceId").getAnnotation(Indexed.class)).isNotNull();
    }

    @Test
    void notificationPreferencesDefinesIndexOnUserId() {
        assertThat(getField(NotificationPreferences.class, "userId").getAnnotation(Indexed.class)).isNotNull();
    }

    private static Field getField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Field " + fieldName + " not found in " + clazz.getSimpleName(), e);
        }
    }
}
