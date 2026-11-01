package ru.secondmemory;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.json.JSONObject;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

public class ArchiveOutboxTest {
 @Test public void unacknowledgedEventSurvivesReopenAndReceiverChange()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String id=UUID.randomUUID().toString();
  String receiver="https://test.invalid/"+id+"/sync";
  JSONObject e=new JSONObject().put("segment_id",id).put("session_id","instrumented").put("recorded_at",1793516400000L).put("text","Искусственная проверка сохранности очереди");
  ArchiveOutbox first=new ArchiveOutbox(c);first.enqueue(e);first.enqueue(e);
  File own=null;for(File f:new ArchiveOutbox(c).pending(receiver,500))if(f.getName().equals(id+".json"))own=f;
  assertNotNull("Неотправленный фрагмент должен пережить повторное открытие",own);
  first.accepted(own,receiver,e);
  for(File f:new ArchiveOutbox(c).pending(receiver,500))assertNotEquals(own,f);
  assertTrue(new ArchiveOutbox(c).pending(receiver+"-other",500).contains(own));
  assertEquals(e.getString("text"),ArchiveOutbox.read(own).getString("text"));
 }
 @Test public void conflictingRetryCannotReplaceOriginal()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String id=UUID.randomUUID().toString();
  JSONObject e=new JSONObject().put("segment_id",id).put("session_id","instrumented").put("recorded_at",1793516400000L).put("text","Первоначальная искусственная реплика");
  ArchiveOutbox b=new ArchiveOutbox(c);b.enqueue(e);
  try{b.enqueue(new JSONObject(e.toString()).put("text","Подменённая реплика"));fail("Конфликт должен быть отклонён");}catch(java.io.IOException expected){}
  File own=new File(c.getFilesDir(),"archive-outbox/events/"+id+".json");assertEquals(e.getString("text"),ArchiveOutbox.read(own).getString("text"));
 }
}
