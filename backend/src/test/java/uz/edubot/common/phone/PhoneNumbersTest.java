package uz.edubot.common.phone;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @ParameterizedTest
    @ValueSource(strings = {"+998901234567", "998901234567", "901234567", "+998 (90) 123-45-67", " 90 123 45 67 "})
    void normalizesUzbekNumbers(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).contains("+998901234567");
    }

    @Test
    void keepsForeignNumbers() {
        assertThat(PhoneNumbers.normalize("+7 916 123-45-67")).contains("+79161234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12", "abc901234567", "+9989012345", "+99890123456789", "90123456a"})
    void rejectsInvalidNumbers(String raw) {
        assertThat(PhoneNumbers.normalize(raw)).isEmpty();
    }

    @Test
    void masksAllButLastFourDigits() {
        assertThat(PhoneNumbers.mask("+998901234567")).isEqualTo("+998*****4567");
    }
}
