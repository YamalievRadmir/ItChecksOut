package ru.sklad.app.util;
import java.math.BigDecimal;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
public final class InventoryData {
 private static final ObjectMapper JSON = new ObjectMapper();
 private InventoryData() {}
 public record Item(Long productId, String name, BigDecimal expected, BigDecimal actual, BigDecimal purchase, BigDecimal retail) {
  public BigDecimal difference() { return actual.subtract(expected); }
  public BigDecimal cost() { return difference().abs().multiply(purchase); }
  public BigDecimal lostProfit() { return difference().signum()<0 ? difference().abs().multiply(retail.subtract(purchase).max(BigDecimal.ZERO)) : BigDecimal.ZERO; }
 }
 public static String encode(List<Item> items) { try { return JSON.writeValueAsString(items); } catch(Exception ex) { throw new IllegalArgumentException("Не удалось сохранить состав инвентаризации",ex); } }
 public static List<Item> decode(String data) { if(data==null || data.isBlank()) return List.of(); try { return Arrays.asList(JSON.readValue(data,Item[].class)); } catch(Exception ex) { throw new IllegalArgumentException("Не удалось прочитать состав инвентаризации",ex); } }
 public static byte[] document(java.time.LocalDate date, String employee, List<Item> items) {
  try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument(); var out=new java.io.ByteArrayOutputStream()) {
   doc.createParagraph().createRun().setText("Акт инвентаризации от " + date);
   doc.createParagraph().createRun().setText("Проводящий: " + employee);
   var table=doc.createTable(1,4); String[] headers={"Товар","Учетное кол-во","Фактическое кол-во","Разница"};
   for(int i=0;i<4;i++) table.getRow(0).getCell(i).setText(headers[i]);
   for(Item item:items) { var row=table.createRow(); row.getCell(0).setText(item.name()); row.getCell(1).setText(item.expected().toPlainString()); row.getCell(2).setText(item.actual().toPlainString()); row.getCell(3).setText(item.difference().toPlainString()); }
   doc.createParagraph().createRun().setText("Подпись проводящего: ________"); doc.write(out); return out.toByteArray();
  } catch(java.io.IOException ex) { throw new IllegalStateException("Не удалось создать акт",ex); }
 }
}
