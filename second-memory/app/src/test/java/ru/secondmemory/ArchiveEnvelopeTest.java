package ru.secondmemory;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArchiveEnvelopeTest {
 private JSONObject line()throws Exception{return new JSONObject().put("text","Тест / запись: позвонить завтра.\nНе реальное поручение.").put("segment_id","speech_123.pcm").put("recorded_at",1793516400000L);}
 @Test public void retryAndReindexPreserveIdentity()throws Exception{
  JSONObject a=ArchiveEnvelope.from("20261101_100000",line(),1),b=ArchiveEnvelope.from("20261101_100000",line(),100);
  assertEquals(a.toString(),b.toString());assertEquals(ArchiveEnvelope.hash(a),ArchiveEnvelope.hash(b));
  JSONObject changed=line().put("text","Другая реплика");assertEquals(a.getString("segment_id"),ArchiveEnvelope.from("20261101_100000",changed,1).getString("segment_id"));
  assertNotEquals(ArchiveEnvelope.hash(a),ArchiveEnvelope.hash(ArchiveEnvelope.from("20261101_100000",changed,1)));
 }
 @Test public void acknowledgmentMustNameExactContents()throws Exception{
  JSONObject body=ArchiveEnvelope.from("test",line(),1);JSONObject ack=new JSONObject().put("accepted",true).put("segment_id",body.getString("segment_id")).put("content_sha256",ArchiveEnvelope.hash(body));ArchiveEnvelope.verifyAck(body,ack);
  for(JSONObject wrong:new JSONObject[]{new JSONObject(),new JSONObject(ack.toString()).put("segment_id","wrong"),new JSONObject(ack.toString()).put("content_sha256","wrong"),new JSONObject(ack.toString()).put("accepted","true")}){try{ArchiveEnvelope.verifyAck(body,wrong);fail("Нельзя подтверждать чужой/пустой ответ");}catch(java.io.IOException expected){}}
 }
 @Test public void matchesServerFixture()throws Exception{
  JSONObject e=new JSONObject().put("segment_id","synthetic-webhook-check-20261101-001").put("session_id","synthetic-test").put("recorded_at",1793516400000L).put("text","ИСКУССТВЕННЫЙ ТЕСТ ПРИЁМНИКА. Проверить доставку текста. Не создавать реальную задачу.");
  assertEquals("d3b7a984bc4aff18aed66422d4f9fc14201313fb3f8d661013f779c102992296",ArchiveEnvelope.hash(e));
 }
 @Test public void refusesInvalidInputWithoutTruncatingIt()throws Exception{
  for(JSONObject bad:new JSONObject[]{line().put("text",""),line().put("recorded_at",-1),line().put("text",String.join("",java.util.Collections.nCopies(24001,"я")))}){try{ArchiveEnvelope.from("test",bad,1);fail("Неверный фрагмент принят");}catch(java.io.IOException expected){}}
 }
}
