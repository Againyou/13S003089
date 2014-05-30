
import java.net.*;

public class FtpServer extends Thread {

	public static final int FTP_PORT = 21; // default port

	ServerSocket ftpsocket = null;

	public static void main(String[] args) {

		if (args.length != 1) {

			System.out.println("Usage:");

			System.out.println("java FtpServer [root dir]");

			System.out.println("nExample:");

			System.out.println("java FtpServer C:ftp");

			return;

		}

		FtpConnection.root = args[0];

		System.out.println("[info] ftp server root: " + FtpConnection.root);

		new FtpServer().start();

	}

	public void run() {

		Socket client = null;

		try {

			ftpsocket = new ServerSocket(FTP_PORT);

			System.out.println("[info] listening port: " + FTP_PORT);

			for (;;) {

				client = ftpsocket.accept();

				new FtpConnection(client).start();

			}

		}

		catch (Exception e) {
			e.printStackTrace();
		}

	}

}
