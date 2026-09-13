package studio.environment.supervisor;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
class IndependentLaunchCleanupTest {
 @Test void earlierCloseWaitMustObserveTheShortenedBudgetWhileNativeCloseIsStillRunning()throws Exception {
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var port=new PrivacyLaunchOwnerTest.Port(){
   @Override public PrivacyLaunchOwner.Cleanup close(long remaining){
    entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("TEST_RELEASE_TIMEOUT");}
    catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError("TEST_INTERRUPTED");}
    return super.close(remaining);
   }
  };
  // No child is needed: the already-open native launch still needs bounded close.
  var owner=new PrivacyLaunchOwner(List.of(),Map.of(),Path.of("/tmp"),port,PrivacyLaunchOwnerTest.deadline());
  try(var executor=Executors.newSingleThreadExecutor()){
   var first=executor.submit(()->owner.close(PrivacyLaunchOwnerTest.deadline()));
   try{
    assertTrue(entered.await(1,TimeUnit.SECONDS));
    assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owner.close(System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(30)));
    assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,assertDoesNotThrow(()->first.get(250,TimeUnit.MILLISECONDS),"Earlier close caller retained the superseded long wait"));
   }finally{release.countDown();first.get(3,TimeUnit.SECONDS);}
  }
  assertEquals(PrivacyLaunchOwner.Cleanup.INCONCLUSIVE,owner.close(PrivacyLaunchOwnerTest.deadline()));
 }
}
