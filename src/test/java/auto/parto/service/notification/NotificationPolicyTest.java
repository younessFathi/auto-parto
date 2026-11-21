package auto.parto.service.notification;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NotificationPolicyTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "12345678-1234-4abc-8def-1", "1-1-1-1-1",
            "1-1234-4abc-8def-000000000001", "12345678-1-4abc-8def-000000000001",
            "12345678-1234-1-8def-000000000001", "12345678-1234-4abc-1-000000000001",
            " 12345678-1234-4abc-8def-000000000001", "12345678-1234-4abc-8def-000000000001 ",
            "not-a-uuid"
    })
    void rejectsMalformedRecipientRatherThanRepairingItsSpelling(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> NotificationPolicy.recipientId(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefab-abcd-4abc-8def-000000000001",
            "ABCDEFAB-ABCD-4ABC-8DEF-000000000001",
            "aBcDeFaB-AbCd-4aBc-8dEf-000000000001",
            "00000000-0000-0000-0000-000000000000"
    })
    void fullWidthRecipientsRemainValidRegardlessOfHexCase(String value) {
        assertThat(NotificationPolicy.recipientId(value)).isEqualTo(value.toLowerCase(Locale.ROOT));
    }
}
