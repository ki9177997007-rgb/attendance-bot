package ru.secondmemory;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class YouGileApiTest {
 @Test public void setupRetriesUseSameIdsAndOnlySeparateProject() throws Exception {
  Map<String,String> created=new HashMap<>();
  YouGileApi.Transport fake=(method,path,body,key)->{
   if(path.equals("/api-v2/users/me")) return new JSONObject().put("id","user-1");
   assertEquals("POST",method);
   String stable=body.getString("idempotencyKey");
   if(!created.containsKey(stable))created.put(stable,"id-"+created.size());
   return new JSONObject().put("id",created.get(stable));
  };
  YouGileApi api=new YouGileApi("secret-for-test",fake);
  JSONObject first=api.setup(),second=api.setup();
  assertEquals(first.toString(),second.toString());
  assertEquals(4,created.size());
 }
 @Test public void lostReplyRetryReusesTaskKeyAndPreservesUncertainVoice() throws Exception {
  TaskDb.Row row=new TaskDb.Row();row.id="task-one";row.text="Олег отправь реестр завтра";row.session="20261030_210000";
  JSONObject config=new JSONObject().put("inbox","owner-column").put("review","uncertain-column");
  List<JSONObject> requests=new ArrayList<>();
  YouGileApi api=new YouGileApi("key",(method,path,body,key)->{
   requests.add(body);
   if(requests.size()==1)throw new java.io.IOException("Reply lost after server commit");
   return new JSONObject().put("id","remote-one");
  });
  try{api.send(row,config);fail();}catch(java.io.IOException expected){}
  assertEquals("remote-one",api.send(row,config));
  assertEquals(requests.get(0).getString("idempotencyKey"),requests.get(1).getString("idempotencyKey"));
  assertEquals("uncertain-column",requests.get(0).getString("columnId"));
  assertFalse(requests.get(0).has("assigned"));assertFalse(requests.get(0).has("deadline"));
  assertFalse(requests.get(0).has("audio"));assertFalse(requests.get(0).has("transcript"));
 }
}
