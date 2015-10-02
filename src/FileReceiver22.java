import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;


public class FileReceiver22 {

	static String destFilePathName;
	static File destFile;
	static FileOutputStream fWriter;
	static SocketAddress senderSocketAddress;
	static int recPort;
	static CRC32 crc = new CRC32();
	static DatagramSocket recSocket;
	static byte currSeqNum = 0;
	//header and data size and max datagram size in bytes
	static final int HEADER_SIZE = 20;
	static final int DATA_SIZE = 980;
	static final int MAX_SIZE = 1000;
	
	//ACK packet
	static final int ACK_SIZE = 10;
	
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Usage: SimpleUDPReceiver <port>");
			System.exit(-1);
		}
		
		recPort = Integer.parseInt(args[0]);
		try {
			recSocket = new DatagramSocket(recPort);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		byte[] data = new byte[MAX_SIZE];
		//Initialise a packet to which data (sent from sender) will be written into
		DatagramPacket dataPacket = new DatagramPacket(data, data.length);
		// Once the data has been written into pkt's data buffer, wrap it with ByteBuffer for easier reading
		ByteBuffer b = ByteBuffer.wrap(data);
		
		while(true)	{
			dataPacket.setLength(data.length);
			recSocket.receive(dataPacket);
			senderSocketAddress = dataPacket.getSocketAddress();
			int packetLength = dataPacket.getLength();
			b.rewind();
			long senderChecksum = b.getLong();
			crc.reset();
			crc.update(data, 8, packetLength - 8);
			//Check checksums to see if data is corrupted
			if (crc.getValue() != senderChecksum) {
				System.out.println("Wrong checksum");
				DatagramPacket ack = createAckPacket((byte)(currSeqNum - 1));
				recSocket.send(ack);
				continue;
			}
			
			// Checksum is correct, data shouldn't be corrupted
				
			byte seqNum = b.get();
			if (currSeqNum != seqNum) {
				System.out.println("Wrong seqNum: currSeqNum: " + currSeqNum + " seqNum: " + seqNum);
				
				DatagramPacket ack = createAckPacket((byte)((currSeqNum == 0) ? 127 : currSeqNum - 1));
				recSocket.send(ack);
				continue;
			}
			
			// Correct seqNum: this is the packet we want
			byte isFilePath = b.get();
			int contentSize = b.getInt();
			byte isLast = b.get();
			b.position(HEADER_SIZE);
			
			byte[] dataField = new byte[contentSize];
			b.get(dataField, 0, contentSize);
			
			if (isLast == 1) {
				fWriter.close();
			}
			if (isFilePath == 1) {
				System.out.println("successful");
				destFilePathName = new String(dataField);
				System.out.println(destFilePathName);
				destFile = new File(destFilePathName);
				if (destFile.getParentFile() != null) {destFile.getParentFile().mkdirs();}
				fWriter = new FileOutputStream(destFile);
				
				DatagramPacket ack = createAckPacket(currSeqNum);
				recSocket.send(ack);
			} else {
				System.out.println("successful");
				fWriter.write(dataField);
				
				DatagramPacket ack = createAckPacket(currSeqNum);
				recSocket.send(ack);
			}
			

			if(currSeqNum == 127) {
				currSeqNum = 0;
			} else {
				currSeqNum++;
			}
		}
	}
		
	
	
	private static DatagramPacket createAckPacket(byte currSeqNum) {
		
		byte[] ackData = new byte[ACK_SIZE];
		ByteBuffer ackDataBuffer = ByteBuffer.wrap(ackData);
		// reserve space for checksum
		ackDataBuffer.clear();
		ackDataBuffer.putLong(0);
		//put seqNum and isFilePath flag
		ackDataBuffer.put(currSeqNum);
		
		crc.reset();
		// update crc checksum based on the number of bytes in the whole packet
		crc.update(ackData, 8, 2);
		long checksum = crc.getValue();
		ackDataBuffer.rewind();
		// put in checksum
		ackDataBuffer.putLong(checksum);
				
		DatagramPacket ack = new DatagramPacket(ackData, ackData.length, senderSocketAddress);
		return ack;
	}
	
}
