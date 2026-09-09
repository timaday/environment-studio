package studio.environment.core.session;
import java.time.Clock;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IndependentCompletionRaceTest {
 @Test void completionNotificationCannotBeLostToConcurrentRetryAdmission() throws Exception {
  var executor=Executors.newFixedThreadPool(2);
  try {for(int iteration=0;iteration<10000;iteration++) {
   var calls=new AtomicInteger();var externalSecond=new AtomicBoolean();var release=new CountDownLatch(1);var start=new CyclicBarrier(3);
   var ledger=new SessionLedger(Clock.systemUTC(),lease->{int call=calls.incrementAndGet();if(call==2&&Thread.currentThread().getName().equals("owned-external-retry")){externalSecond.set(true);try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("TEST_TIMEOUT");}catch(InterruptedException interrupted){throw new AssertionError("TEST_INTERRUPTED");}}if(call<3)throw new IllegalStateException("MOCK_PENDING");});
   ledger.admit("old",new Owner("https://invented.invalid","mock-owner"));ledger.close("old");
   var external=executor.submit(()->{Thread.currentThread().setName("owned-external-retry");start.await();return ledger.retryCleanup("old");});
   var notification=executor.submit(()->{Thread.currentThread().setName("owned-completion-notification");start.await();return ledger.resumeCleanup("old");});
   start.await();SessionLedger.CleanupReport notice;
   try{notice=notification.get(3,TimeUnit.SECONDS).orElse(null);}finally{release.countDown();}
   external.get(3,TimeUnit.SECONDS);
   if(externalSecond.get()&&notice!=null&&notice.state()==SessionLedger.CleanupState.IN_PROGRESS) {
    assertEquals(3,calls.get(),"Completion notification lost between lookup and retry admission at iteration "+iteration);
    assertTrue(ledger.cleanupReports().isEmpty());
   }
  }}finally{executor.shutdownNow();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));}
 }
}
