package studio.environment.core.plan;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Streaming native framing into a process-local MAC; input bodies are never retained. */
final class CommandIdentity {
    private final byte[] key;
    CommandIdentity() { key = new byte[32]; new SecureRandom().nextBytes(key); }
    CommandIdentity(byte[] key) { this.key = key.clone(); }
    String digest(String lease, String plan, Object command) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update("ES-PLAN-COMMAND-1\0".getBytes(StandardCharsets.US_ASCII));
            frame(mac, lease); frame(mac, plan); frame(mac, command);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException unavailable) { throw new IllegalStateException("REQUIRED_MAC_UNAVAILABLE"); }
    }
    private static void frame(Mac mac, Object value) {
        if (value instanceof String string) { var bytes = string.getBytes(StandardCharsets.UTF_8); ascii(mac,"S"+bytes.length+":"); mac.update(bytes); }
        else if (value instanceof BigInteger integer) ascii(mac,"I"+integer+";");
        else if (value instanceof Boolean flag) ascii(mac,flag?"T":"F");
        else if (value instanceof List<?> list) { ascii(mac,"A"+list.size()+":"); list.forEach(item->frame(mac,item)); }
        else if (value instanceof Map<?,?> map) {
            ascii(mac,"O"+map.size()+":");
            map.entrySet().stream().sorted((a,b)->Arrays.compareUnsigned(((String)a.getKey()).getBytes(StandardCharsets.UTF_8),((String)b.getKey()).getBytes(StandardCharsets.UTF_8)))
                .forEach(entry->{frame(mac,entry.getKey());frame(mac,entry.getValue());});
        } else throw new PlanRefusal(PlanRefusal.Code.INVALID_REQUEST);
    }
    private static void ascii(Mac mac, String value) { mac.update(value.getBytes(StandardCharsets.US_ASCII)); }
}
