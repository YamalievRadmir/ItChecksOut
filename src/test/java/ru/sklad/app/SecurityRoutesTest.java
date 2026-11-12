package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
 "spring.datasource.url=jdbc:h2:mem:security-route-test;DB_CLOSE_DELAY=-1",
 "app.bootstrap.admin-password=AdminRouteTest123!",
 "app.bootstrap.seller-password=SellerRouteTest123!",
 "app.bootstrap.super-admin-password=SuperRouteTest123!",
 "app.demo-data.enabled=false",
 "app.site-order.api-key=security-test-key-with-at-least-32-characters"
})
class SecurityRoutesTest {
 @LocalServerPort int port;
 HttpClient login(String username,String password) throws Exception {
  var cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
  var client=HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
  var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/login"))
   .header("Content-Type","application/x-www-form-urlencoded")
   .POST(HttpRequest.BodyPublishers.ofString("username="+URLEncoder.encode(username,java.nio.charset.StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,java.nio.charset.StandardCharsets.UTF_8))).build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(302,response.statusCode());assertFalse(response.headers().firstValue("location").orElse("").contains("error"));return client;
 }
 int get(HttpClient client,String path) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
 }
 @Test void superAdminCanEnterManagementButCannotEnterBusinessPages() throws Exception {
  var client=login("super_admin","SuperRouteTest123!");assertEquals(200,get(client,"/super-admin"));
  for(String path:java.util.List.of("/products","/sales","/deliveries","/settings","/super-admin/nested")) {
   if(path.startsWith("/super-admin/")) assertNotEquals(403,get(client,path));
   else assertEquals(403,get(client,path));
  }
 }
 @Test void shopRolesCannotEnterManagementButCanEnterTheirBusinessPages() throws Exception {
  var admin=login("admin","AdminRouteTest123!");assertEquals(403,get(admin,"/super-admin"));assertEquals(403,get(admin,"/super-admin/nested"));
  assertEquals(200,get(admin,"/products"));assertEquals(200,get(admin,"/sales"));
  var seller=login("seller","SellerRouteTest123!");assertEquals(403,get(seller,"/super-admin"));assertEquals(200,get(seller,"/products"));assertEquals(200,get(seller,"/sales"));
 }

 @Test void websiteApiRequiresExplicitSecretAndNeverAcceptsSessionAlone() throws Exception {
  var client=login("admin","AdminRouteTest123!");
  var uri=URI.create("http://localhost:"+port+"/api/public/delivery-order");
  var request=HttpRequest.newBuilder(uri).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
  assertEquals(401,client.send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode());
  request.header("X-Site-Order-Key","security-test-key-with-at-least-32-characters");
  assertEquals(400,client.send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode());
 }
 @Test void browserBusinessPostWithoutCsrfTokenIsRejected() throws Exception {
  var client=login("admin","AdminRouteTest123!");
  assertEquals(403,client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/products"))
   .POST(HttpRequest.BodyPublishers.ofString("" )).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
 }
}