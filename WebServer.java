
import java.net.*;
public class WebServer {
	public static void main(String args[]) {
		int i=1, PORT=8080;
		ServerSocket server=null;
		Socket client=null;
		try {
			server=new ServerSocket(PORT); 
			System.out.println("Web Server is listening on port "+server.getLocalPort());
			for (;;) {
				client=server.accept(); // 接受客户机的连接请求
				new ConnectionThread(client,i).start(); 
				i++;
			}
		} catch (Exception e) {System.out.println(e);}
	}
}
/* ConnnectionThread类完成与一个Web浏览器的通信 */
