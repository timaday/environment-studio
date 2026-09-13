package studio.environment.server.plan;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
/** A response whose owned admission covers encoding, transfer and cleanup. */
interface PlanResponse {void write(HttpServletResponse response,int status)throws IOException;}
