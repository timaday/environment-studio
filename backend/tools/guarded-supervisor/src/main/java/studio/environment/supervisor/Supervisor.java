package studio.environment.supervisor;

final class Supervisor {
    interface ConsolePort { Credentials read(boolean postgres); SessionEngine.Cleanup restore(); }
    interface RuntimePort { SessionEngine.Wire open(Admission.Checked admitted,Credentials credentials); }
    static final ConsolePort UNAVAILABLE_CONSOLE=new ConsolePort(){public Credentials read(boolean pg){throw new Refusal("CONSOLE_UNQUALIFIED");}public SessionEngine.Cleanup restore(){return SessionEngine.Cleanup.COMPLETE;}};
    static final RuntimePort UNAVAILABLE_RUNTIME=(admitted,credentials)->{throw new Refusal("RUNTIME_UNQUALIFIED");};
    record Result(String outcome,String code,SessionEngine.Cleanup cleanup) {
        int exit(){return switch(outcome){case "APPLIED"->cleanup==SessionEngine.Cleanup.COMPLETE?0:5;case "UNKNOWN"->4;case "NOT_APPLIED"->3;default->2;};}
        String json(){return "{\"outcome\":\""+outcome+"\",\"code\":\""+code+"\",\"cleanup\":\""+cleanup+"\"}";}
    }
    Result apply(String[] args,Admission.Registry registry,ConsolePort console,RuntimePort runtime){
        Admission.Checked admitted;
        try{admitted=Admission.admit(Invocation.parse(args),registry);}catch(Refusal e){return new Result("REFUSED",e.code,SessionEngine.Cleanup.COMPLETE);}catch(Throwable e){return new Result("REFUSED","ADMISSION_UNAVAILABLE",SessionEngine.Cleanup.INCONCLUSIVE);}
        Credentials credentials=null;SessionEngine.Wire wire=null;SessionEngine.Outcome known=null;boolean opening=false,engineReturned=false;
        String outcome="REFUSED",code="OPERATION_UNAVAILABLE";SessionEngine.Cleanup cleanup=SessionEngine.Cleanup.INCONCLUSIVE;
        try{
            credentials=console.read(admitted.destination().engine()==studio.environment.server.export.PackageData.Engine.POSTGRESQL);
            var engine=new SessionEngine();opening=true;wire=runtime.open(admitted,credentials);
            known=engine.execute(wire);engineReturned=true;outcome=known.status().name();cleanup=known.cleanup();
            code=switch(known.status()){case APPLIED->"COMMIT_ACKNOWLEDGED";case UNKNOWN->"COMMIT_UNKNOWN";case NOT_APPLIED->"TRANSACTION_REFUSED";};
        }catch(Throwable failure){
            if(known!=null)outcome=known.status().name();else if(opening){outcome="UNKNOWN";code="COMMIT_UNKNOWN";}
            cleanup=SessionEngine.Cleanup.INCONCLUSIVE;
        }finally{
            if(wire!=null&&!engineReturned){try{wire.terminate();}catch(Throwable ignored){cleanup=SessionEngine.Cleanup.INCONCLUSIVE;}try{wire.close();}catch(Throwable ignored){cleanup=SessionEngine.Cleanup.INCONCLUSIVE;}}
            try{if(credentials!=null)credentials.close();}catch(Throwable ignored){cleanup=SessionEngine.Cleanup.INCONCLUSIVE;}
            try{if(console.restore()==SessionEngine.Cleanup.INCONCLUSIVE)cleanup=SessionEngine.Cleanup.INCONCLUSIVE;}catch(Throwable ignored){cleanup=SessionEngine.Cleanup.INCONCLUSIVE;}
        }
        return new Result(outcome,code,cleanup);
    }
}
