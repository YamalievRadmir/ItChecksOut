package ru.sklad.app;
import org.junit.jupiter.api.Test;
import ru.sklad.app.util.PhoneFields;
import static org.junit.jupiter.api.Assertions.*;
class PhoneFormattingTest {
 @Test void acceptsRussianPrefixesAndLocalNumbers() {
  for(String input : new String[]{"+7 912 345 67 89", "89123456789", "9123456789", "8 (912) 345-67-89"}) assertEquals("8 (912) 345-67-89", PhoneFields.format(input));
 }
 @Test void rejectsIncompleteNumbers() { assertThrows(IllegalArgumentException.class, () -> PhoneFields.format("12345")); }
 @Test void permitsEmptyOptionalPhone() { assertEquals("",PhoneFields.format(null)); }
}
