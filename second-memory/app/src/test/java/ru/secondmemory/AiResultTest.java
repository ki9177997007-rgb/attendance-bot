package ru.secondmemory;
import org.junit.Test;import static org.junit.Assert.*;import java.time.*;import org.json.*;
public class AiResultTest {
 @Test public void noTask()throws Exception{assertNull(AiResult.parse("{\"task\":false}",0,ZoneId.of("UTC")));}
 @Test public void extractsFields()throws Exception{AiResult r=AiResult.parse("{\"task\":true,\"action\":\"Отправить реестр\",\"assignee\":\"Сергей\",\"due\":\"2026-11-01T19:00\"}",Instant.parse("2026-10-31T12:00:00Z").toEpochMilli(),ZoneId.of("Europe/Moscow"));assertEquals("Сергей",r.assignee);assertEquals(Instant.parse("2026-11-01T16:00:00Z").toEpochMilli(),r.due);}
 @Test(expected=JSONException.class)public void rejectsTruncated()throws Exception{AiResult.parse("{\"task\":true",0,ZoneId.of("UTC"));}
 @Test(expected=JSONException.class)public void rejectsMissingAction()throws Exception{AiResult.parse("{\"task\":true}",0,ZoneId.of("UTC"));}
 @Test public void invalidDateDoesNotSetAlarm()throws Exception{assertEquals(0,AiResult.parse("{\"task\":true,\"action\":\"Позвонить\",\"due\":\"никогда\"}",0,ZoneId.of("UTC")).due);}
}
