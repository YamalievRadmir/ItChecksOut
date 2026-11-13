package ru.sklad.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
 "spring.datasource.url=jdbc:h2:mem:security-route-test;DB_CLOSE_DELAY=-1",
 "app.bootstrap.admin-password=admin123",
 "app.bootstrap.seller-password=SellerRouteTest123!",
 "app.bootstrap.super-admin-password=super123",
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
  assertEquals(302,response.statusCode());assertEquals("/", URI.create(response.headers().firstValue("location").orElseThrow()).getPath());assertFalse(response.headers().firstValue("location").orElse("").contains("error"));return client;
 }
 int get(HttpClient client,String path) throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode();
 }
 @Test void authenticatedRoutesAreAvailableBeforeVaadinNavigation() throws Exception {
  var client=login("super_admin","super123");assertEquals(200,get(client,"/super-admin"));
  for(String path:java.util.List.of("/products","/sales","/deliveries","/settings","/super-admin/nested")) {
   if(path.startsWith("/super-admin/")) assertNotEquals(403,get(client,path));
   else assertEquals(200,get(client,path));
  }
 }
 @Test void shopUsersCanLoadRoutesBeforeVaadinNavigation() throws Exception {
  var admin=login("admin","admin123");assertEquals(200,get(admin,"/super-admin"));assertEquals(200,get(admin,"/super-admin/nested"));
  assertEquals(200,get(admin,"/products"));assertEquals(200,get(admin,"/sales"));
  var seller=login("seller","SellerRouteTest123!");assertEquals(200,get(seller,"/super-admin"));assertEquals(200,get(seller,"/products"));assertEquals(200,get(seller,"/sales"));
 }

 @Test void websiteApiAcceptsAuthenticatedSession() throws Exception {
  var client=login("admin","admin123");
  var uri=URI.create("http://localhost:"+port+"/api/public/delivery-order");
  var request=HttpRequest.newBuilder(uri).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
  assertEquals(400,client.send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode());
  request.header("X-Site-Order-Key","security-test-key-with-at-least-32-characters");
  assertEquals(400,client.send(request.build(),HttpResponse.BodyHandlers.discarding()).statusCode());
 }
 @Test void anonymousLoginBootstrapReturnsJsonInsteadOfRedirectOrHtml() throws Exception {
  var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  assertEquals(200,get(client,"/login"));
  var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/?v-r=init&location=login&query=")).GET().build(),HttpResponse.BodyHandlers.ofString());
  assertEquals(200,response.statusCode());
  assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
  assertFalse(response.body().contains("<!doctype html>"));
  var bootstrap = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
  assertTrue(bootstrap.has("appConfig")); assertTrue(bootstrap.path("errors").isNull());
  assertTrue(bootstrap.path("appConfig").has("uidl"));
 } @Test void browserBusinessPostWithoutCsrfTokenIsRejected() throws Exception {
  var client=login("admin","admin123");
  assertEquals(403,client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/products"))
   .POST(HttpRequest.BodyPublishers.ofString("" )).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
 }
}