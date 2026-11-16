package ru.sklad.app.util;
import com.vaadin.flow.component.textfield.TextField;
public final class PhoneFields {
 private PhoneFields() {}
 public static String format(String value) {
  String d = value == null ? "" : value.replaceAll("\\D", "");
  if (d.isEmpty()) return "";
  if (d.length()==11 && (d.startsWith("7") || d.startsWith("8"))) d=d.substring(1);
  if(d.length()!=10) throw new IllegalArgumentException("Введите 10 цифр телефона");
  return "8 ("+d.substring(0,3)+") "+d.substring(3,6)+"-"+d.substring(6,8)+"-"+d.substring(8);
 }
 public static void configure(TextField field) {
  field.setPlaceholder("8 (999) 123-45-67");
  field.addValueChangeListener(e -> { try { String v=format(e.getValue()); if(!v.equals(e.getValue())) field.setValue(v); field.setInvalid(false); } catch(IllegalArgumentException ex) { field.setInvalid(true); field.setErrorMessage(ex.getMessage()); } });
 }
}
