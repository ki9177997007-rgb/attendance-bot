package ru.secondmemory;
import org.junit.Test;
import static org.junit.Assert.*;
public class SpeakerProfileTest {
 @Test public void shortAndInvalidSpeechNeverGetsPriority() {
  assertFalse(SpeakerProfile.probableOwner(new double[]{1,0},new double[]{1,0},399));
  assertFalse(SpeakerProfile.probableOwner(new double[]{1,0},null,500));
  assertFalse(SpeakerProfile.probableOwner(new double[]{1,0},new double[]{Double.NaN,0},500));
  assertFalse(new SpeakerProfile().add(new double[]{0,0},500));
 }
 @Test public void differentSpeakerCannotContaminateEnrollment() {
  SpeakerProfile p = new SpeakerProfile();
  assertTrue(p.add(new double[]{1,0},500));
  assertFalse(p.add(new double[]{0,1},500));
  assertEquals(1,p.count());
  assertTrue(p.add(new double[]{2,0.1},500));
  assertTrue(p.add(new double[]{3,0.1},500));
  assertEquals(3,p.count());
  assertTrue(SpeakerProfile.probableOwner(p.vector(),new double[]{1,0},500));
  assertFalse(SpeakerProfile.probableOwner(p.vector(),new double[]{0,1},500));
 }
}
