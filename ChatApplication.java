import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/*
 * 
 * Implementation of a chat program that allows teo users to exchange messages and files.
 * 
 */
class ChatApplication {
	int port;
	String clientName;
	DataInputStream dataInputStream;

	ChatApplication(String clientName, int port) {
		this.clientName = clientName;
		this.port = port;
	}

	private static class WriterThread extends Thread {
		Socket socket;
		DataOutputStream dataOutputStream;
		String ipAddress;
		int cport, fileBufferSize;

		WriterThread() {
			fileBufferSize = 1024;
			ipAddress = "localhost";
			System.out.println("Writer started.");
		}

		// Sends the requested file to client
		public void sendFile(String filePath) throws Exception {
			FileInputStream fileInputStream = new FileInputStream(new File(filePath));
			int bytes = 0;

			// File found, notify other party
			dataOutputStream.writeUTF("transfer " + filePath);
			dataOutputStream.flush();

			// Send file size
			dataOutputStream.writeLong(fileInputStream.getChannel().size());

			// Sends the file in pieces of 1Kb
			byte[] buffer = new byte[fileBufferSize];
			while ((bytes = fileInputStream.read(buffer)) != -1) {
				dataOutputStream.write(buffer, 0, bytes);
				dataOutputStream.flush();
			}
			fileInputStream.close();
			System.out.println("File sent.");
		}

		public void run() {
			System.out.print("Input target port number: ");
			Scanner scanner = new Scanner(System.in);
			cport = scanner.nextInt();
			try {
				socket = new Socket(ipAddress, cport);
				System.out.println("Connected to " + socket.getPort());
				dataOutputStream = new DataOutputStream(socket.getOutputStream());
				while (true) {
					String msg = scanner.nextLine();
					String[] command = msg.trim().split("\\s+", 3);
					if (command[0].equals("transfer")) {
						try {
							sendFile(command[1]);
						} catch (FileNotFoundException f) {
							System.out.println("File " + command[1] + " not found.");
						} catch (Exception e) {
							System.out.println("Error sending file: " + e.getMessage());
						}
						continue;
					}
					dataOutputStream.writeUTF(msg);
					dataOutputStream.flush();
				}
			} catch (Exception e) {
				System.out.println("WriterThread error " + e.getMessage());
			} finally {
				scanner.close();
			}
		}
	}

	// Receives file data from client and writes into a new file
	public void receiveFile(DataInputStream dataInputStream, String filePath) throws Exception {
		FileOutputStream fileOutputStream = new FileOutputStream(filePath);
		int bytes = 0;
		long size = dataInputStream.readLong();
		byte[] buffer = new byte[1024];

		// Server receives 1kb of the file in each iteration
		while (size > 0 && (bytes = dataInputStream.read(buffer, 0, (int) Math.min(buffer.length, size))) != -1) {
			fileOutputStream.write(buffer, 0, bytes);
			size = size - bytes;
		}
		fileOutputStream.close();
		System.out.println("File received.");
	}

	public void start() {
		System.out.println(clientName + " is running.");
		System.out.println("Port number is " + port);
		ServerSocket sSocket = null;
		WriterThread writer = new WriterThread();
		try {
			sSocket = new ServerSocket(port);
			writer.start();
			Socket socket = sSocket.accept();
			dataInputStream = new DataInputStream(socket.getInputStream());
			String msg = "";
			boolean cont = true;
			while (cont) {
				try {
					msg = dataInputStream.readUTF();
					String[] command = msg.trim().split("\\s+", 3);
					if (command[0].equals("transfer")) {
						System.out.println("Receiving file " + command[1]);
						receiveFile(dataInputStream, "new" + command[1]);
						continue;
					}
					System.out.println(msg);
				} catch (IOException e) {
					System.out.println("Interruption " + e.getMessage());
					cont = false;
				}
			}
		} catch (Exception e) {
			System.out.println("Encountered error " + e.getMessage());
		} finally {
			if (sSocket != null) {
				try {
					sSocket.close();
				} catch (Exception e) {
					System.out.println("Error: " + e.getMessage());
				}
			}
		}
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			System.out.println("Usage: java ChatApplication <client_name> <port>");
			System.exit(1);
		}
		ChatApplication ca = new ChatApplication(args[0], Integer.parseInt(args[1]));
		ca.start();
	}
}