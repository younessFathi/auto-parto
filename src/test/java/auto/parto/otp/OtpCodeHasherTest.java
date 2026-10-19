package auto.parto.otp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpCodeHasherTest {

    @Test
    void hashDoesNotContainOtpAndMatchesOnlyTheOriginalCode() {
        String hash = OtpCodeHasher.hashCode("123456");

        assertThat(hash).doesNotContain("123456");
        assertThat(OtpCodeHasher.matchesCode("123456", hash)).isTrue();
        assertThat(OtpCodeHasher.matchesCode("654321", hash)).isFalse();
    }

    @Test
    void eachChallengeUsesAUniqueSalt() {
        assertThat(OtpCodeHasher.hashCode("123456"))
                .isNotEqualTo(OtpCodeHasher.hashCode("123456"));
    }
}
