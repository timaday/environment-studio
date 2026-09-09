package studio.environment.server.plan;
import jakarta.servlet.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnedAsyncCompletionTest {
 static final class Context {
  AsyncListener listener;final AtomicInteger calls=new AtomicInteger();volatile boolean errorReturned;Runnable completion=()->{};
  final AsyncContext context=(AsyncContext)Proxy.newProxyInstance(AsyncContext.class.getClassLoader(),new Class<?>[]{AsyncContext.class},(proxy,method,args)->{
   if(method.getName().equals("addListener")){listener=(AsyncListener)args[0];return null;}
   if(method.getName().equals("complete")){calls.incrementAndGet();if(errorReturned)throw new IllegalStateException("invented-container-detail");completion.run();return null;}
   if(method.getName().equals("toString"))return "MockAsyncContext";
   throw new AssertionError("UNEXPECTED_CONTEXT_ACCESS");
  });
  AsyncEvent event(){return new AsyncEvent(context);}
 }
 @Test void errorCallbackCompletesBeforeItReturnsAndWorkerDoesNotUseContextAgain() throws Exception {
  var context=new Context();var owned=new OwnedAsyncCompletion(context.context);context.listener.onError(context.event());context.errorReturned=true;
  assertEquals(1,context.calls.get(),"Callback must own completion before return");
  assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,assertDoesNotThrow(owned::finish));assertEquals(1,context.calls.get());
 }
 @Test void observedContainerCompletionIsTerminalWithoutCallingCompleteAgain() throws Exception {
  var context=new Context();var owned=new OwnedAsyncCompletion(context.context);context.listener.onComplete(context.event());
  assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,owned.finish());assertEquals(0,context.calls.get());
 }
 @Test void timeoutAndRepeatedWorkerFinishHaveOneAttempt() throws Exception {
  var context=new Context();var owned=new OwnedAsyncCompletion(context.context);context.listener.onTimeout(context.event());
  for(int i=0;i<32;i++)assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,owned.finish());
  context.listener.onError(context.event());assertEquals(1,context.calls.get());
 }
 @Test void completionRefusalStaysInconclusiveWithoutLeakingCauseOrRetry() throws Exception {
  var context=new Context();context.completion=()->{throw new IllegalStateException("invented-private-diagnostic");};var owned=new OwnedAsyncCompletion(context.context);
  var failure=assertThrows(java.io.IOException.class,()->context.listener.onError(context.event()));
  assertEquals("ASYNC_COMPLETION_REFUSED",failure.getMessage());assertNull(failure.getCause());
  context.listener.onComplete(context.event());assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owned.finish());assertEquals(1,context.calls.get());assertFalse(owned.toString().contains("diagnostic"));
 }
 @Test void unsupportedNewCycleNeverRenewsCompletionOwnership() throws Exception {
  var context=new Context();var owned=new OwnedAsyncCompletion(context.context);
  assertEquals("ASYNC_CYCLE_UNSUPPORTED",assertThrows(java.io.IOException.class,()->context.listener.onStartAsync(context.event())).getMessage());
  assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,owned.finish());assertEquals(0,context.calls.get());
 }
 @Test void onCompleteNeverWaitsOnTheInFlightAttemptLock() throws Exception {
  var context=new Context();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  context.completion=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("TEST_RELEASE_TIMEOUT");}catch(InterruptedException interrupted){throw new AssertionError("TEST_INTERRUPTED");}};
  var owned=new OwnedAsyncCompletion(context.context);var executor=Executors.newFixedThreadPool(2);
  try {var worker=executor.submit(owned::finish);assertTrue(entered.await(3,TimeUnit.SECONDS));
   var completed=executor.submit(()->{context.listener.onComplete(context.event());return true;});assertTrue(completed.get(1,TimeUnit.SECONDS));
   release.countDown();assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,worker.get(3,TimeUnit.SECONDS));assertEquals(1,context.calls.get());
  }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));}
 }
 @Test void callbacksAndCompetingWorkersDoNotWaitForTheExistingAttempt() throws Exception {
  var context=new Context();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var callbackStarted=new CountDownLatch(1);
  context.completion=()->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("TEST_RELEASE_TIMEOUT");}catch(InterruptedException interrupted){throw new AssertionError("TEST_INTERRUPTED");}};
  var owned=new OwnedAsyncCompletion(context.context);var executor=Executors.newFixedThreadPool(2);
  try{var worker=executor.submit(owned::finish);assertTrue(entered.await(3,TimeUnit.SECONDS));var error=executor.submit(()->{callbackStarted.countDown();context.listener.onError(context.event());context.errorReturned=true;return true;});assertTrue(callbackStarted.await(3,TimeUnit.SECONDS));
   assertTrue(error.get(1,TimeUnit.SECONDS));assertEquals(OwnedAsyncCompletion.Outcome.IN_PROGRESS,owned.finish());release.countDown();assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,worker.get(3,TimeUnit.SECONDS));assertEquals(1,context.calls.get());assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,owned.finish());
  }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));}
 }
 @Test void simultaneousWorkerAndErrorRacesRemainSingleAttempt() throws Exception {
  var executor=Executors.newFixedThreadPool(2);
  try{for(int i=0;i<64;i++){var context=new Context();var owned=new OwnedAsyncCompletion(context.context);var start=new CountDownLatch(1);
   var worker=executor.submit(()->{start.await();return owned.finish();});var callback=executor.submit(()->{start.await();context.listener.onError(context.event());context.errorReturned=true;return true;});start.countDown();var result=worker.get(3,TimeUnit.SECONDS);assertTrue(callback.get(3,TimeUnit.SECONDS));assertEquals(1,context.calls.get());var settled=owned.finish();assertNotEquals(OwnedAsyncCompletion.Outcome.IN_PROGRESS,settled);if(result==OwnedAsyncCompletion.Outcome.INCONCLUSIVE)assertEquals(result,settled);}}
  finally{executor.shutdownNow();assertTrue(executor.awaitTermination(3,TimeUnit.SECONDS));}
 }
 @Test void actualTomcatTimeoutCompletesBeforeLateWorkerFinish() throws Exception {actualContainer(false);}
 @Test void actualTomcatErrorCompletesBeforeLateWorkerFinish() throws Exception {actualContainer(true);}
 private static void actualContainer(boolean error) throws Exception {
  var base=java.nio.file.Files.createTempDirectory("es-owned-async-");var tomcat=new org.apache.catalina.startup.Tomcat();
  var owned=new AtomicReference<OwnedAsyncCompletion>();var completed=new CountDownLatch(1);var callbackThread=new AtomicReference<Thread>();var completionThread=new AtomicReference<Thread>();var calls=new AtomicInteger();
  tomcat.setBaseDir(base.toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
  var application=tomcat.addContext("",base.toString());
  var servlet=new jakarta.servlet.http.HttpServlet(){@Override protected void doGet(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) throws jakarta.servlet.ServletException {
   var original=request.startAsync();original.setTimeout(50);
   var wrapped=(AsyncContext)Proxy.newProxyInstance(AsyncContext.class.getClassLoader(),new Class<?>[]{AsyncContext.class},(proxy,method,args)->{
    if(method.getName().equals("complete")){calls.incrementAndGet();completionThread.set(Thread.currentThread());}
    try{return method.invoke(original,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   owned.set(new OwnedAsyncCompletion(wrapped));
   original.addListener(new AsyncListener(){public void onComplete(AsyncEvent event){completed.countDown();}public void onError(AsyncEvent event){callbackThread.set(Thread.currentThread());}public void onTimeout(AsyncEvent event){callbackThread.set(Thread.currentThread());}public void onStartAsync(AsyncEvent event){throw new AssertionError("UNEXPECTED_CYCLE");}});
   if(error)throw new jakarta.servlet.ServletException("MOCK_ASYNC_ERROR");
  }};
  var wrapper=org.apache.catalina.startup.Tomcat.addServlet(application,"owned",servlet);wrapper.setAsyncSupported(true);application.addServletMappingDecoded("/","owned");
  try{tomcat.start();try(var client=java.net.http.HttpClient.newHttpClient()) {
   var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/")).timeout(java.time.Duration.ofSeconds(5)).GET().build();
   var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.discarding());assertTrue(response.statusCode()>=200&&response.statusCode()<=599);
   assertTrue(completed.await(3,TimeUnit.SECONDS));assertNotNull(callbackThread.get());assertSame(callbackThread.get(),completionThread.get());assertEquals(1,calls.get());assertEquals(OwnedAsyncCompletion.Outcome.COMPLETE,assertDoesNotThrow(()->owned.get().finish()));assertEquals(1,calls.get());
  }}finally{tomcat.stop();tomcat.destroy();try(var paths=java.nio.file.Files.walk(base)){for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);}}
 }
 @Test void callbackMustNotInvertWithTheSocketLockNeededByWorkerCompletion() throws Exception {
  var context=new Context();var socket=new java.util.concurrent.locks.ReentrantLock();var socketHeld=new CountDownLatch(1);var workerEntered=new CountDownLatch(1);var callbackReturned=new CountDownLatch(1);
  context.completion=()->{workerEntered.countDown();try{if(!socket.tryLock(2,TimeUnit.SECONDS))throw new IllegalStateException("MOCK_SOCKET_LOCK_TIMEOUT");try{if(context.errorReturned)throw new IllegalStateException("MOCK_LATE_COMPLETION_REFUSAL");}finally{socket.unlock();}}catch(InterruptedException interrupted){throw new AssertionError("TEST_INTERRUPTED");}};
  var owned=new OwnedAsyncCompletion(context.context);var executor=Executors.newFixedThreadPool(2);
  try{var callback=executor.submit(()->{socket.lock();try{socketHeld.countDown();assertTrue(workerEntered.await(3,TimeUnit.SECONDS));context.listener.onError(context.event());context.errorReturned=true;return true;}finally{callbackReturned.countDown();socket.unlock();}});
   assertTrue(socketHeld.await(3,TimeUnit.SECONDS));var worker=executor.submit(owned::finish);
   assertTrue(callbackReturned.await(150,TimeUnit.MILLISECONDS),"Callback cannot wait while owning the socket lock needed by complete");
   assertTrue(callback.get(3,TimeUnit.SECONDS));assertEquals(OwnedAsyncCompletion.Outcome.INCONCLUSIVE,worker.get(3,TimeUnit.SECONDS));assertEquals(1,context.calls.get());
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(4,TimeUnit.SECONDS));}
 }
}