package ru.secondmemory;
import org.junit.Test;
import static org.junit.Assert.*;
public class TaskRulesTest {
 @Test public void staffCanChange(){assertTrue(TaskRules.candidate("Сергей, подготовь документы по Иванову"));assertFalse(TaskRules.candidate("Сергей сегодня ушёл домой"));}
 @Test public void explicitRussianInstructionsBecomeCandidates() {
  assertTrue(TaskRules.candidate("Олег, подготовь документы по Иванову завтра"));
  assertTrue(TaskRules.candidate("напомни позвонить в больницу"));
  assertTrue(TaskRules.candidate("нужно отправить реестр сегодня"));
 }
 @Test public void conversationNegationAndHypothesesAreNotCommands() {
  assertFalse(TaskRules.candidate("Олег сегодня ушёл домой"));
  assertFalse(TaskRules.candidate("не нужно отправлять документы"));
  assertFalse(TaskRules.candidate("задача если получится позвонить"));
  assertFalse(TaskRules.candidate("например надо позвонить"));
  assertFalse(TaskRules.candidate("задача"));
 }
 @Test public void punctuationAndYoDoNotMakeDifferentCommands() {
  assertEquals(TaskRules.normalize("Артём, передай реестр!"),TaskRules.normalize("артем передай реестр"));
 }
}
