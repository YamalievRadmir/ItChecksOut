package ru.sklad.app;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.boot.test.context.TestConfiguration;
import ru.sklad.app.service.*;
import ru.sklad.app.repository.*;
import ru.sklad.app.entity.*;
import ru.sklad.app.util.SecurityUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql=false)
@org.junit.jupiter.api.extension.ExtendWith(TestShopContext.class)
@Import({SecurityUtils.class, ProductFileImporter.class, ProductFileImporterTest.Config.class})
class ProductFileImporterTest {
 @TestConfiguration @EnableMethodSecurity static class Config {}
 @Autowired ProductFileImporter importer; @Autowired ProductRepository products; @Autowired ShopRepository shops;
 @MockitoBean AuditService audit;
 @BeforeEach void admin() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",AuthorityUtils.createAuthorityList("ROLE_ADMIN"))); }
 @AfterEach void clear() { SecurityContextHolder.clearContext(); }
 InputStream excel(Object[][] values) throws Exception {
  try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
   var sheet=workbook.createSheet();for(int r=0;r<values.length;r++){var row=sheet.createRow(r);for(int c=0;c<values[r].length;c++){var cell=row.createCell(c);Object v=values[r][c];if(v instanceof Number n)cell.setCellValue(n.doubleValue());else if(v!=null)cell.setCellValue(v.toString());}}
   workbook.write(out); return new ByteArrayInputStream(out.toByteArray());
  }
 }
 @Test void reorderedHeadersUpsertOnlyCurrentShopAndPreserveOtherFields() throws Exception {
  Shop other=new Shop();other.setName("Other");shops.saveAndFlush(other);
  SecurityUtils.inShop(other,()->{Product p=new Product();p.setName("Feed");p.setRetailPrice(BigDecimal.ONE);products.saveAndFlush(p);return null;});
  Product p=new Product();p.setName("Feed");p.setCategory("Keep");p.setPurchasePrice(new BigDecimal("4"));products.saveAndFlush(p);
  try(var input=excel(new Object[][]{{" STOCK ","  Наименование  ","PRICE"},{7,"Feed",12.5},{2,"New",20},{3,"New",25}})) { assertEquals(3,importer.importFile(input)); }
  assertEquals(2,products.count());var updated=products.findById(p.getId()).orElseThrow();assertEquals("Keep",updated.getCategory());assertEquals(0,new BigDecimal("12.50").compareTo(updated.getRetailPrice()));
  assertEquals(0,new BigDecimal("4").compareTo(updated.getPurchasePrice()));
  SecurityUtils.inShop(other,()->{assertEquals(1,products.count());assertEquals(0,BigDecimal.ONE.compareTo(products.findAll().get(0).getRetailPrice()));return null;});
 }
 @Test void missingOptionalColumnsAndTextNumbersDefaultToZero() throws Exception {
  try(var input=excel(new Object[][]{{"название"},{"One"},{""}})){assertEquals(1,importer.importFile(input));}
  try(var input=excel(new Object[][]{{"товар","цена","кол-во"},{"Two","123","invalid"}})){assertEquals(1,importer.importFile(input));}
  for(Product p:products.findAll()){assertEquals(0,p.getRetailPrice().signum());assertEquals(0,p.getStockQuantity().signum());assertEquals(SecurityUtils.currentShop().getId(),p.getShop().getId());}
 }
 @Test void missingNameHeaderAndCorruptFileAreRejected() throws Exception {
  try(var input=excel(new Object[][]{{"price"},{10}})){assertEquals("Ошибка: не найдена колонка с наименованием",assertThrows(IllegalArgumentException.class,()->importer.importFile(input)).getMessage());}
  assertThrows(Exception.class,()->importer.importFile(new ByteArrayInputStream(new byte[]{1,2,3})));assertEquals(0,products.count());
 }
 @Test void sellerCannotImport() throws Exception {
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",AuthorityUtils.createAuthorityList("ROLE_SELLER")));
  try(var input=excel(new Object[][]{{"name"},{"Forbidden"}})){assertThrows(org.springframework.security.access.AccessDeniedException.class,()->importer.importFile(input));}
 }
}