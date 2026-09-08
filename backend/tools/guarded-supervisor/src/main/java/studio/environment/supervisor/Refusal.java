package studio.environment.supervisor;
final class Refusal extends RuntimeException {
    final String code;
    Refusal(String code) { super(null, null, false, false); this.code=code; }
    static void require(boolean condition,String code) { if(!condition)throw new Refusal(code); }
}
