package studio.environment.supervisor;
final class SessionEngine {
    enum Status { APPLIED, NOT_APPLIED, UNKNOWN }
    enum Cleanup { COMPLETE, INCONCLUSIVE }
    record Outcome(Status status,Cleanup cleanup) { }
    interface Wire extends AutoCloseable {
        void authenticate(); void bootstrap(); void program(); void readiness(); void commit(); void acknowledgement(); void cleanExit();
        Cleanup rollbackAndCleanup(); void terminate(); @Override void close();
    }
    Outcome execute(Wire wire){
        boolean commitAttempted=false;Status status=Status.NOT_APPLIED;Cleanup cleanup=Cleanup.INCONCLUSIVE;
        try {
            wire.authenticate();wire.bootstrap();wire.program();wire.readiness();
            commitAttempted=true;wire.commit();wire.acknowledgement();wire.cleanExit();
            status=Status.APPLIED;cleanup=Cleanup.COMPLETE;
        }catch(Throwable failure){
            if(commitAttempted){status=Status.UNKNOWN;terminate(wire);}
            else {try{cleanup=wire.rollbackAndCleanup();}catch(Throwable ignored){terminate(wire);}}
        }finally{
            try{wire.close();}catch(Throwable ignored){cleanup=Cleanup.INCONCLUSIVE;}
        }
        return new Outcome(status,cleanup);
    }
    private static void terminate(Wire wire){try{wire.terminate();}catch(Throwable ignored){/* Uncertainty is already retained. */}}
}
