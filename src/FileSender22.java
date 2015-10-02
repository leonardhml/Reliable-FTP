import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.zip.CRC32;


public class FileSender22 {

	static String host;
	static int unreliNETPort;
	static String fileToSendPathName;
	static String destFilePathName;
	static InetSocketAddress unreliNETAddress;
	static DatagramSocket senderSocket;

	static CRC32 crc = new CRC32();
	
	//header and data size and max datagram size in bytes
	static final int HEADER_SIZE = 20;
	static final int DATA_SIZE = 980;
	static final int MAX_SIZE = 1000;
	
	//ACK packet
	static final int ACK_SIZE = 10;
	
	
	public static void main(String args[]) {
		
		//Read arguments
		if (args.length < 4) {
			System.err.println("Usage: SimpleUDPReceiver <host> <port> <src> <dest>");
			System.exit(-1);
		}
		
		host = args[0];
		unreliNETPort = Integer.parseInt(args[1]);
		fileToSendPathName = args[2];
		destFilePathName = args[3];
		unreliNETAddress = new InetSocketAddress(host, unreliNETPort);
		try {			
			senderSocket = new DatagramSocket();
			senderSocket.setSoTimeout(10);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		File sourceFile = new File(fileToSendPathName);
		
		BufferedInputStream fileReader = null;
		try {
			fileReader = new BufferedInputStream(new FileInputStream(sourceFile));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			send(fileReader);
		} catch (Exception e) {
			System.out.println("Error at send method");
			e.printStackTrace();
		}
	}
	
	private static void send(BufferedInputStream fileReader) throws IOException {
		// Initialise everything needed for packet to be sent
		DatagramPacket packet = null;
		byte seqNum = 0;
		int bytesRead;
		byte[] fileData = new byte[DATA_SIZE];
		byte isFilePath = 1;
		byte[] destPathData = destFilePathName.getBytes();
		bytesRead = destPathData.length;
		
		// Initialise everything needed for received packet
		byte[] ackPacketData = new byte[ACK_SIZE];
		ByteBuffer ackPacketDataBuffer = ByteBuffer.wrap(ackPacketData);
		
		// Send first packet containing destination file path
		packet = createPacket(destPathData, bytesRead, seqNum, isFilePath, (byte) 0);
		sendPacketUntilSuccess(packet, ackPacketData, ackPacketDataBuffer, seqNum);
		seqNum++;
		isFilePath = 0;
		
		// Send packets containing file data
		while ((bytesRead = fileReader.read(fileData)) != -1) {
			System.out.println(seqNum);
			packet = createPacket(fileData, bytesRead, seqNum, isFilePath, (byte) 0);
			sendPacketUntilSuccess(packet, ackPacketData, ackPacketDataBuffer, seqNum);
			
			if(seqNum == 127) {
				seqNum = 0;
			} else {
				seqNum++;
			}
		}
		
		packet = createPacket(new byte[0], 0, seqNum, isFilePath, (byte) 1);
		sendPacketUntilSuccess(packet, ackPacketData, ackPacketDataBuffer, seqNum);
	}
	
	// compute checksum
	// create header (checksum + seqNum)
	// put data
	// return completed packet
	private static DatagramPacket createPacket(byte[] data, int contentSize, byte seqNum, byte isFilePath, byte isLast) {
		byte[] packetData = new byte[MAX_SIZE];
		ByteBuffer packetDataBuffer = ByteBuffer.wrap(packetData);
		// reserve space for checksum
		packetDataBuffer.clear();
		packetDataBuffer.putLong(0);
		//put seqNum and isFilePath flag
		packetDataBuffer.put(seqNum);
		packetDataBuffer.put(isFilePath);
		packetDataBuffer.putInt(contentSize);
		packetDataBuffer.put(isLast);
		// move buffer pointer to initial byte position for data
		packetDataBuffer.position(HEADER_SIZE);
		packetDataBuffer.put(data);
		
		crc.reset();
		// update crc checksum based on the number of bytes in the whole packet
		crc.update(packetData, 8, packetData.length - 8);
		long checksum = crc.getValue();
		packetDataBuffer.rewind();
		// put in checksum
		packetDataBuffer.putLong(checksum);
		
		// At this stage, packetData now contains:
		// In header: checksum (8 bytes), seqNum (1 byte), isFilePath(1 byte), contentSize(4 bytes) , isLast(1 byte), unused (5 bytes) [total 20 bytes]
		// In data: at most 980 bytes data
		
		return new DatagramPacket(packetData, packetData.length, unreliNETAddress);
	}

	private static void sendPacketUntilSuccess(DatagramPacket packet, byte[] ackPacketData, ByteBuffer ackPacketDataBuffer, byte seqNum) throws IOException {
		//Send packet
		//Receive ACK into ackPacket
		//Compare checksum in ACK and own checksum for ACK
		//Resend if checksum mismatch || ACK.seqNum != seqNum
		while(true) {
			senderSocket.send(packet);
			ackPacketDataBuffer.clear();
			DatagramPacket ackPacket = new DatagramPacket(ackPacketData, ACK_SIZE);
			boolean ackReceived = false;
			try {
				senderSocket.receive(ackPacket);
				ackReceived = true;
			} catch (SocketTimeoutException e) {
				ackReceived = false;
			}
			
			if (ackReceived) {
				ackPacketDataBuffer.rewind();
				long ackChecksum = ackPacketDataBuffer.getLong();
				crc.reset();
				crc.update(ackPacketData, 8, 2);
				byte ackSeqNum = ackPacketDataBuffer.get();
				if (crc.getValue() == ackChecksum && ackSeqNum == seqNum) {
					break;
				}
			}
		}
			
	}
}
