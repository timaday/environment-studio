package studio.environment.supervisor;

/** Standalone entry point. No runtime combination or console adapter is currently qualified. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        Supervisor.Result result=execute(()->new Supervisor().apply(args,Admission.COMPILED_REGISTRY,Supervisor.UNAVAILABLE_CONSOLE,Supervisor.UNAVAILABLE_RUNTIME));
        System.out.println(result.json());System.exit(result.exit());
    }
    static Supervisor.Result execute(java.util.function.Supplier<Supervisor.Result> operation){
        try{return operation.get();}catch(Throwable failure){return new Supervisor.Result("UNKNOWN","SUPERVISOR_UNAVAILABLE",SessionEngine.Cleanup.INCONCLUSIVE);}
    }
}
