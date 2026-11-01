package ru.secondmemory;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class VoiceIntentTest {
 @Test public void russianWakeAndList(){for(String s:new String[]{"Помощник, что делать?","Память, мои задачи","Помощник, чё там надо сделать?","Помощник, что мне нужно сделать сегодня?"}){assertTrue(PhoneCommand.addressed(s));assertEquals(s,VoiceIntent.Kind.LIST,VoiceIntent.parse(s).kind);}}
 @Test public void referencesAndConservativeMutation(){assertEquals(0,VoiceIntent.parse("первое сделал").ordinal);assertEquals(VoiceIntent.Kind.DONE,VoiceIntent.parse("первое сделал").kind);assertEquals(VoiceIntent.Kind.SNOOZE,VoiceIntent.parse("перенеси второе через час").kind);assertEquals(1,VoiceIntent.parse("перенеси второе через час").ordinal);for(String s:new String[]{"не закрывай первое","если первое сделал","например второе готово","закрой документы","Иванов сделал первое"}){assertEquals(s,VoiceIntent.Kind.UNKNOWN,VoiceIntent.parse(s).kind);}}
 @Test public void semanticIntentAndDateWindow(){assertEquals(VoiceIntent.Kind.RECALL,VoiceIntent.parse("Помощник, о чём мы вчера говорили про Иванова").kind);ZoneId zone=ZoneId.of("Europe/Moscow");long now=ZonedDateTime.of(2026,11,1,10,0,0,0,zone).toInstant().toEpochMilli();long[] w=VoiceIntent.window("вчера Иванов",now,zone);assertEquals(LocalDate.of(2026,10,31).atStartOfDay(zone).toInstant().toEpochMilli(),w[0]);assertEquals(86400000,w[1]-w[0]);assertEquals(Long.MIN_VALUE,VoiceIntent.window("Иванов",now,zone)[0]);}
 @Test public void threeInstructionsAreNotMerged(){assertEquals(3,TaskRules.fragments("Сначала нужно отправить документы. Потом позвони Иванову завтра. Затем проверь ответ поставщика.").size());assertEquals(0,TaskRules.fragments("Если надо отправить документы, сначала спроси разрешение.").size());}
}
