package ru.secondmemory;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Immutable wire representation. IDs survive retries and transcript recovery. */
final class ArchiveEnvelope {
 static JSONObject from(String session, JSONObject line, int lineNumber) throws Exception {
  String text=line.optString("text", "");
  long time=line.optLong("recorded_at", -1);
  if(text.trim().isEmpty() || text.length()>24000 || time<0 || time>4102444800000L)
   throw new IOException("Неподходящий фрагмент; исходный текст сохранён");
  if(!session.matches("[A-Za-z0-9_.-]{1,120}"))throw new IOException("Неверный номер записи");
  String source=line.optString("segment_id", "");
  if(source.isEmpty())source="legacy-"+lineNumber+"-"+time;
  String id=UUID.nameUUIDFromBytes((session+"\n"+source).getBytes(StandardCharsets.UTF_8)).toString();
  return new JSONObject().put("segment_id",id).put("session_id",session).put("recorded_at",time).put("text",text);
 }
 static String hash(JSONObject e) throws Exception {
  // Match JSON.stringify([id,session,time,text]) independently of Android's slash escaping.
  String canonical="["+quote(e.getString("segment_id"))+","+quote(e.getString("session_id"))+","+e.getLong("recorded_at")+","+quote(e.getString("text"))+"]";
  return sha(canonical);
 }
 static String sha(String value) throws Exception {
  byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
  StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
 }
 private static String quote(String s){
  StringBuilder out=new StringBuilder("\"");
  for(int i=0;i<s.length();i++){
   char c=s.charAt(i);
   if(c=='"'||c=='\\')out.append('\\').append(c);
   else if(c=='\b')out.append("\\b");else if(c=='\f')out.append("\\f");else if(c=='\n')out.append("\\n");else if(c=='\r')out.append("\\r");else if(c=='\t')out.append("\\t");
   else if(c<32 || (Character.isSurrogate(c) && !(Character.isHighSurrogate(c)&&i+1<s.length()&&Character.isLowSurrogate(s.charAt(i+1))) && !(Character.isLowSurrogate(c)&&i>0&&Character.isHighSurrogate(s.charAt(i-1)))))out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
   else out.append(c);
  }
  return out.append('"').toString();
 }
 static void verifyAck(JSONObject envelope, JSONObject response) throws Exception {
  if(!Boolean.TRUE.equals(response.opt("accepted")) || !envelope.getString("segment_id").equals(response.optString("segment_id")) || !hash(envelope).equals(response.optString("content_sha256")))
   throw new IOException("Архив не подтвердил сохранение этого фрагмента");
 }
}
