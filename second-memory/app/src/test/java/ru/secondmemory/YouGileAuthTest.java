package ru.secondmemory;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class YouGileAuthTest {
 @Test public void authContractUsesYouGileCredentialsAndCompany()throws Exception{
  YouGileAuth auth=new YouGileAuth(new YouGileApi(null,(method,path,body,key)->{
   assertEquals("POST",method);assertNull(key);assertEquals("owner@example.test",body.getString("login"));assertEquals("only-test-password",body.getString("password"));
   if(path.equals("/api-v2/auth/companies?limit=1000"))return new JSONObject().put("content",new JSONArray().put(new JSONObject().put("id","company-1")));
   assertEquals("/api-v2/auth/keys",path);assertEquals("company-1",body.getString("companyId"));return new JSONObject().put("key","test-key");
  }));
  assertEquals("company-1",auth.companies("owner@example.test","only-test-password").getJSONObject(0).getString("id"));
  assertEquals("test-key",auth.createKey("owner@example.test","only-test-password","company-1"));
 }
}
