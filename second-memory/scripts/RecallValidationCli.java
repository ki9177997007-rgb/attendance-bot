package ru.secondmemory;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;
/** Run production source/relevance validation against actual native model output in CI. */
public class RecallValidationCli {
 public static void main(String[] args)throws Exception{
  List<ConversationAnalysis.Line> lines=Arrays.asList(new ConversationAnalysis.Line(1,"12:00","Пособие пока не поступило на счёт.",1000),new ConversationAnalysis.Line(2,"12:01","Завтра ожидается доставка стульев.",2000));
  String raw=new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8);
  System.out.println(MemorySearch.parseAnswer(raw,lines,args[0],Collections.emptyList()));
 }
}
