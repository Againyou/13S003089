import java.net.*;
import java.io.*;
import java.util.*;
import java.text.*;

public class FtpConnection extends Thread {
	/** 主目录 */
	static public String root = null;
	private String currentDir = "/"; // 当前目录
	private Socket socket;
	private BufferedReader reader = null;
	private BufferedWriter writer = null;
	private String clientIP = null;
	private Socket tempSocket = null; // tempSocket用于传送文件
	private ServerSocket pasvSocket = null; // 用于被动模式
	private String host = null;
	private int port = (-1);

	public FtpConnection(Socket socket) {
		this.socket = socket;
		this.clientIP = socket.getInetAddress().getHostAddress();
	}

	public void run() {
		String command;
		try {
			System.out.println(clientIP + " connected.");
			socket.setSoTimeout(60000); // ftp超时设定
			reader = new BufferedReader(new InputStreamReader(socket
					.getInputStream()));
			writer = new BufferedWriter(new OutputStreamWriter(socket
					.getOutputStream()));
			response("220-欢迎消息......");
			response("220-欢迎消息......");
			response("220 注意最后一行欢迎消息没有“-”");
			for (;;) {
				command = reader.readLine();
				if (command == null)
					break;
				System.out
						.println("command from " + clientIP + " : " + command);
				parseCommand(command);
				if (command.equals("QUIT")) // 收到QUIT命令
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (Exception e) {
			}
			try {
				if (writer != null)
					writer.close();
			} catch (Exception e) {
			}
			try {
				if (this.pasvSocket != null)
					pasvSocket.close();
			} catch (Exception e) {
			}
			try {
				if (this.tempSocket != null)
					tempSocket.close();
			} catch (Exception e) {
			}
			try {
				if (this.socket != null)
					socket.close();
			} catch (Exception e) {
			}
		}
		System.out.println(clientIP + " disconnected.");
	}

	// 　　FtpConnection在run()方法中仅仅是获得用户命令/处理命令，当收到QUIT时，关闭连接，结束Ftp会话。

	// 　　先准备几个辅助方法：
	private void response(String s) throws Exception {
		// System.out.println(" [RESPONSE] "+s);
		writer.write(s);
		writer.newLine();
		writer.flush(); // 注意要flush否则响应仍在缓冲区
	}

	// 生成一个字符串
	private static String pad(int length) {
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < length; i++)
			buf.append((char) ' ');
		return buf.toString();
	}

	// 获取参数

	private String getParam(String cmd, String start) {
		String s = cmd.substring(start.length(), cmd.length());
		return s.trim();
	}

	// 获取路径
	private String translatePath(String path) {
		if (path == null)
			return root;
		if (path.equals(""))
			return root;
		path = path.replace('/', '\\');
		return root + path;
	}

	// 获取文件长度，注意是一个字符串

	private String getFileLength(long length) {
		String s = Long.toString(length);
		int spaces = 12 - s.length();
		for (int i = 0; i < spaces; i++)
			s = " " + s;
		return s;
	}

	// 　　接下来便是处理用户命令，这个方法有点长，需要重构一下，我只是把LIST命令单独挪了出来：
	private void parseCommand(String s) throws Exception {
		if (s == null || s.equals(""))
			return;
		if (s.startsWith("USER")) {
			response("331 need password");
		} else if (s.startsWith("PASS")) {
			response("230 welcome to my ftp!");
		} else if (s.equals("QUIT")) {
			response("221 欢迎再来！");
		} else if (s.equals("TYPE A")) {
			response("200 TYPE set to A.");
		} else if (s.equals("TYPE I")) {
			response("200 TYPE set to I.");
		} else if (s.equals("NOOP")) {
			response("200 NOOP OK.");
		} else if (s.startsWith("CWD")) { // 设置当前目录，注意没有检查目录是否有效
			this.currentDir = getParam(s, "CWD ");
			response("250 CWD command successful.");
		} else if (s.equals("PWD")) { // 打印当前目录
			response("257 " + this.currentDir + " is current directory.");
		} else if (s.startsWith("PORT")) {
			// 记录端口
			String[] params = getParam(s, "PORT ").split(",");
			if (params.length <= 4 || params.length >= 7)
				response("500 command param error.");
			else {
				this.host = params[0] + "." + params[1] + "." + params[2] + "."
						+ params[3];
				String port1 = null;
				String port2 = null;
				if (params.length == 6) {
					port1 = params[4];
					port2 = params[5];
				} else {
					port1 = "0";
					port2 = params[4];
				}
				this.port = Integer.parseInt(port1) * 256
						+ Integer.parseInt(port2);
				response("200 command successful.");
			}
		} else if (s.equals("PASV")) { // 进入被动模式
			if (pasvSocket != null)
				pasvSocket.close();
			try {
				pasvSocket = new ServerSocket(0);
				int pPort = pasvSocket.getLocalPort();
				String s_port;
				if (pPort <= 255)
					s_port = "255";
				else {
					int p1 = pPort / 256;
					int p2 = pPort - p1 * 256;
					s_port = p1 + "," + p2;
				}
				pasvSocket.setSoTimeout(60000);
				response("227 Entering Passive Mode ("
						+ InetAddress.getLocalHost().getHostAddress().replace(
								'.', ',') + "," + s_port + ")");
			} catch (Exception e) {
				if (pasvSocket != null) {
					pasvSocket.close();
					pasvSocket = null;
				}
			}
		} else if (s.startsWith("RETR")) { // 传文件
			String file = currentDir + (currentDir.endsWith("/") ? "" : "/")
					+ getParam(s, "RETR");
			System.out.println("download file: " + file);
			Socket dataSocket;
			// 根据上一次的PASV或PORT命令决定使用哪个socket
			if (pasvSocket != null)
				dataSocket = pasvSocket.accept();
			else
				dataSocket = new Socket(this.host, this.port);
			OutputStream dos = null;
			InputStream fis = null;
			response("150 Opening ASCII mode data connection.");
			try {
				fis = new BufferedInputStream(new FileInputStream(
						translatePath(file)));
				dos = new DataOutputStream(new BufferedOutputStream(dataSocket
						.getOutputStream()));
				// 开始正式发送数据:
				byte[] buffer = new byte[20480]; // 发送缓冲 20k
				int num = 0; // 发送一次读取的字节数
				do {
					num = fis.read(buffer);
					if (num != (-1)) {
						// 发送：
						dos.write(buffer, 0, num);
						dos.flush();
					}
				} while (num != (-1));
				fis.close();
				fis = null;
				dos.close();
				dos = null;
				dataSocket.close();
				dataSocket = null;
				response("226 transfer complete."); // 响应一个成功标志
			} catch (Exception e) {
				response("550 ERROR: File not found or accessdenied.");
			} finally {
				try {
					if (fis != null)
						fis.close();
					if (dos != null)
						dos.close();
					if (dataSocket != null)
						dataSocket.close();
				} catch (Exception e) {
				}
			}
		}
		else if (s.startsWith("STOR")) {
			response("150 Binary data connection");
			s = s.substring(4);
			s = s.trim();
			RandomAccessFile inFile = new RandomAccessFile(translatePath(currentDir) + "\\" + s, "rw");
			Socket tempSocket = new Socket(host, this.port);
			InputStream inSocket = tempSocket.getInputStream();
			byte byteBuffer[] = new byte[1024];
			int amount;
			try {
				while ((amount = inSocket.read(byteBuffer)) != -1) {
					inFile.write(byteBuffer, 0, amount);
				}
				inSocket.close();
				response("226 transfer complete");
				inFile.close();
				tempSocket.close();
			} catch (IOException e) {
			}
		}
		else if (s.equals("LIST")) { // 列当前目录文件
			Socket dataSocket;
			// 根据上一次的PASV或PORT命令决定使用哪个socket
			if (pasvSocket != null)
				dataSocket = pasvSocket.accept();
			else
				dataSocket = new Socket(this.host, this.port);
			PrintWriter writer = new PrintWriter(new BufferedOutputStream(
					dataSocket.getOutputStream()));
			response("150 Opening ASCII mode data connection.");
			try {
				responseList(writer, this.currentDir);
				writer.close();
				dataSocket.close();
				response("226 transfer complete.");
			} catch (IOException e) {
				writer.close();
				dataSocket.close();
				response(e.getMessage());
			}
			dataSocket = null;
		} else {
			response("500 invalid command"); // 没有匹配的命令，输出错误信息
		}
	}

	// 响应LIST命令
	private void responseList(PrintWriter writer, String path)
			throws IOException {
		File dir = new File(translatePath(path));
		System.out.println(translatePath(path));
		if (!dir.isDirectory())
			throw new IOException("550 No such file or directory");
		File[] files = dir.listFiles();
		String dateStr;
		for (int i = 0; i < files.length; i++) {
			// dateStr = new SimpleDateFormat("MMM dd hh:mm").format(new
			// Date(files[i].lastModified()));
			dateStr = "";
			if (files[i].isDirectory()) {
				writer.println("drwxrwxrwx 1 ftp System 0 " + dateStr + " "
						+ files[i].getName());
			} else {
				writer.println("-rwxrwxrwx 1 ftp System "
						+ getFileLength(files[i].length()) + " " + dateStr
						+ " " + files[i].getName());
			}
		}
		String file_header = "-rwxrwxrwx 1 ftp System 0 Aug 5 19:59 ";
		String dir_header = "drwxrwxrwx 1 ftp System 0 Aug 15 19:59 ";
		writer.println("total " + files.length);
		writer.flush();
	}
}
