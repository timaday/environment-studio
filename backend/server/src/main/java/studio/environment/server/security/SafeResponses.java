package studio.environment.server.security;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;

public final class SafeResponses {
    private SafeResponses() { }
    public static void refuse(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
