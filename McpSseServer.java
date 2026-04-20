import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class McpSseServer {

    public void sendSseResponse(HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream"); 
        response.setCharacterEncoding("UTF-8");
        ServletOutputStream out = response.getOutputStream();

        // Example of sending data
        out.write("data: Hello, World!\n\n".getBytes());
        out.flush();

        // Keep the connection alive
        while (true) {
            try {
                Thread.sleep(1000); // Send a message every second
                out.write("data: Another message\n\n".getBytes());
                out.flush();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}