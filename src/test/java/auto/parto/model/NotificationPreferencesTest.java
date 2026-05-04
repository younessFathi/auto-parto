package auto.parto.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferencesTest {

    @Test
    void createDefault_setsAllDefaults() {
        NotificationPreferences prefs = NotificationPreferences.createDefault("user-123");

        assertThat(prefs.getUserId()).isEqualTo("user-123");
        assertThat(prefs.isEmailEnabled()).isTrue();
        assertThat(prefs.isPushEnabled()).isTrue();
        assertThat(prefs.isSmsEnabled()).isFalse();
        assertThat(prefs.isNewOfferEnabled()).isTrue();
        assertThat(prefs.isPriceAlertEnabled()).isTrue();
        assertThat(prefs.isSearchMatchEnabled()).isTrue();
        assertThat(prefs.isOrderUpdateEnabled()).isTrue();
        assertThat(prefs.isMarketingEnabled()).isFalse();
        assertThat(prefs.getCreatedAt()).isNotNull();
        assertThat(prefs.getUpdatedAt()).isNotNull();
    }

    @Test
    void builder_createsPreferencesCorrectly() {
        LocalDateTime now = LocalDateTime.now();

        NotificationPreferences prefs = NotificationPreferences.builder()
                .id("pref-id")
                .userId("user-123")
                .emailEnabled(true)
                .pushEnabled(false)
                .smsEnabled(true)
                .newOfferEnabled(false)
                .priceAlertEnabled(false)
                .searchMatchEnabled(true)
                .orderUpdateEnabled(true)
                .marketingEnabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(prefs.getId()).isEqualTo("pref-id");
        assertThat(prefs.getUserId()).isEqualTo("user-123");
        assertThat(prefs.isEmailEnabled()).isTrue();
        assertThat(prefs.isPushEnabled()).isFalse();
        assertThat(prefs.isSmsEnabled()).isTrue();
        assertThat(prefs.isNewOfferEnabled()).isFalse();
        assertThat(prefs.isPriceAlertEnabled()).isFalse();
        assertThat(prefs.isSearchMatchEnabled()).isTrue();
        assertThat(prefs.isOrderUpdateEnabled()).isTrue();
        assertThat(prefs.isMarketingEnabled()).isTrue();
    }

    @Test
    void builder_noArgs_createsEmptyPreferences() {
        NotificationPreferences prefs = new NotificationPreferences();

        assertThat(prefs.getUserId()).isNull();
        assertThat(prefs.isEmailEnabled()).isFalse();
        assertThat(prefs.isPushEnabled()).isFalse();
    }
}