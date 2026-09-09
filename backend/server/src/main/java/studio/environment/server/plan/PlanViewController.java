package studio.environment.server.plan;

import java.io.*;
import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import studio.environment.core.plan.*;
import studio.environment.core.session.SessionLedger;
import static studio.environment.server.plan.PlanViewRequest.Route;

@RestController
@ConditionalOnProperty(name="studio.mode",havingValue="hosted")
public final class PlanViewController {
    private final PlanRuntime runtime;private final PlanController transport;
    public PlanViewController(PlanRuntime runtime,PlanController transport){this.runtime=runtime;this.transport=transport;}
    @PostMapping("/api/v1/plans/{planId}/materializations")
    public void materialization(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.MATERIALIZATION,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/documents")
    public void documents(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.DOCUMENTS,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/entities")
    public void entities(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.ENTITIES,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/relations")
    public void relations(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.RELATIONS,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/draft")
    public void draft(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.DRAFT,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/containment")
    public void containment(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.CONTAINMENT,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/placements")
    public void placements(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.PLACEMENTS,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/document")
    public void document(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.DOCUMENT,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/bindings")
    public void bindings(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.BINDINGS,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/views/binding-locations")
    public void bindingLocations(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.BINDING_LOCATIONS,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/profile-captures")
    public void capture(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.CAPTURE,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/profile-previews")
    public void preview(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.PREVIEW,planId,request,response);}
    @PostMapping("/api/v1/plans/{planId}/validations")
    public void validation(@PathVariable("planId") String planId,HttpServletRequest request,HttpServletResponse response)throws IOException{start(Route.VALIDATION,planId,request,response);}
    private void start(Route route,String planId,HttpServletRequest request,HttpServletResponse response)throws IOException {
        var lease=PlanController.lease(request);var service=runtime.service();service.requireOwned(lease,planId);
        var work=new Work(service,lease,planId,route);
        transport.start(lease,request,response,work,work::cancelled,route.collection()?67_108_864:16_384,route.collection()?30:10,200,
            body->(PlanResponse)(output,status)->work.transfer(body,output,status));
    }
    @ExceptionHandler(RuntimeException.class)
    public void failure(RuntimeException failure,HttpServletResponse response)throws IOException{transport.failure(failure,response);}
    private static final class Work implements AutoCloseable {
        private final HostedPlanService service;private final SessionLedger.Lease lease;private final String planId;private final Route route;
        private final PlanController.MetadataSlot metadata;private HostedPlanService.ViewAdmission admission;
        Work(HostedPlanService service,SessionLedger.Lease lease,String planId,Route route){
            this.service=service;this.lease=lease;this.planId=planId;this.route=route;
            if(route.collection()){admission=service.reserveView(lease,planId);metadata=null;}else metadata=PlanController.metadata();
        }
        boolean cancelled(){return !service.live(lease) || admission!=null && !admission.live();}
        void transfer(OwnedServletBody body,HttpServletResponse response,int status)throws IOException {
            PlanViewRequest small=null;
            if(admission==null){small=new PlanViewReader().read(route,body);admission=service.reserveView(lease,planId);}
            final PlanViewRequest decoded=small;
            try {
                admission.run(()->{
                    var request=decoded==null?new PlanViewReader().read(route,body):decoded;admission.pin(request.revision());
                    var result=PlanViewResults.render(admission,route,request);admission.verify();
                    try(var encoded=new PlanViewEncoding(134_217_728)){
                        encoded.encode(result);admission.verify();
                        response.setStatus(status);response.setHeader("Cache-Control","no-store");response.setContentType("application/json");response.setContentLength(encoded.size());
                        try(var output=new OwnedServletOutput(response.getOutputStream(),admission::verify,System.nanoTime()+30_000_000_000L,System::nanoTime)) {
                            encoded.write(output,admission::verify);output.flush();admission.verify();
                        }
                        catch(IOException|RuntimeException refused){throw new UncheckedIOException(new IOException("PLAN_TRANSFER_REFUSED"));}
                    }
                    return true;
                });
            }catch(UncheckedIOException disconnected){throw disconnected.getCause();}
        }
        @Override public void close(){try{if(admission!=null)admission.close();}finally{if(metadata!=null)metadata.close();}}
        @Override public String toString(){return "ViewWork[redacted]";}
    }
}
