package ru.secondmemory;
import org.json.*;
/** Official public API v2 authentication. No password is persisted. */
final class YouGileAuth {
 private final YouGileApi api;
 YouGileAuth(YouGileApi api){this.api=api;}
 JSONArray companies(String login,String password)throws Exception{return api.call("POST","/api-v2/auth/companies?limit=1000",new JSONObject().put("login",login).put("password",password)).getJSONArray("content");}
 String createKey(String login,String password,String company)throws Exception{return api.call("POST","/api-v2/auth/keys",new JSONObject().put("login",login).put("password",password).put("companyId",company)).getString("key");}
}
