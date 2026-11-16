package ru.sklad.app.util;
public final class ReceiptNames {
 private ReceiptNames() {}
 public static String shorten(String name) {
  String value=java.util.Objects.toString(name, "");
  return value.codePointCount(0,value.length())>25 ? value.substring(0,value.offsetByCodePoints(0,25))+"..." : value;
 }
}
