package org.martus.server.formirroring;

import java.io.File;
import java.io.IOException;
import java.util.TimerTask;
import java.util.Vector;

import org.martus.common.Base64;
import org.martus.common.BulletinHeaderPacket;
import org.martus.common.Database;
import org.martus.common.DatabaseKey;
import org.martus.common.InputStreamWithSeek;
import org.martus.common.MartusCrypto;
import org.martus.common.MartusUtilities;
import org.martus.common.UniversalId;
import org.martus.common.MartusUtilities.InvalidPublicKeyFileException;
import org.martus.common.MartusUtilities.PublicInformationInvalidException;
import org.martus.server.core.LoggerInterface;
import org.martus.server.core.MartusXmlRpcServer;
import org.martus.server.forclients.MartusServer;
import org.martus.server.forclients.MartusServerUtilities;
import org.martus.server.formirroring.CallerSideMirroringGatewayForXmlRpc.SSLSocketSetupException;

public class ServerForMirroring implements ServerSupplierInterface
{
	public ServerForMirroring(MartusServer coreServerToUse, LoggerInterface loggerToUse) throws 
			IOException, InvalidPublicKeyFileException, PublicInformationInvalidException, SSLSocketSetupException
	{
		coreServer = coreServerToUse;
		logger = loggerToUse;
		log("Initializing ServerForMirroring");
		
		authorizedCallers = new Vector();
		loadServersWhoAreAuthorizedToCallUs();
		log("Authorized " + authorizedCallers.size() + " Mirrors to call us");

		retrieversWeWillCall = new Vector();
		createGatewaysForServersWhoWeCall();
		log("Configured to call " + retrieversWeWillCall.size() + " Mirrors");
	}

	public void log(String message)
	{
		logger.log(message);
	}
	
	public void addListeners()
	{
		int port = MirroringInterface.MARTUS_PORT_FOR_MIRRORING;
		log("Opening port " + port + " for mirroring...");
		SupplierSideMirroringHandler supplierHandler = new SupplierSideMirroringHandler(this, getSecurity());
		MartusXmlRpcServer.createSSLXmlRpcServer(supplierHandler, MirroringInterface.DEST_OBJECT_NAME, port);

		MartusUtilities.startTimer(new MirroringTask(retrieversWeWillCall), mirroringIntervalMillis);
		log("Mirroring port opened and mirroring task scheduled");
	}

	// Begin ServerSupplierInterface
	public Vector getPublicInfo()
	{
		try
		{
			Vector result = new Vector();
			result.add(getSecurity().getPublicKeyString());
			result.add(MartusUtilities.getSignatureOfPublicKey(getSecurity()));
			return result;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return new Vector();
		}
	}
	
	public boolean isAuthorizedForMirroring(String callerAccountId)
	{
		return authorizedCallers.contains(callerAccountId);
	}

	public Vector listAccountsForMirroring()
	{
		class Collector implements Database.AccountVisitor
		{
			public void visit(String accountId)
			{
				accounts.add(accountId);
			}
			
			Vector accounts = new Vector();
		}

		Collector collector = new Collector();		
		getDatabase().visitAllAccounts(collector);
		return collector.accounts;
	}

	public Vector listBulletinsForMirroring(String authorAccountId)
	{
		class Collector implements Database.PacketVisitor
		{
			public void visit(DatabaseKey key)
			{
				try
				{
					InputStreamWithSeek in = getDatabase().openInputStream(key, null);
					byte[] sigBytes = BulletinHeaderPacket.verifyPacketSignature(in, getSecurity());
					in.close();
					String sigString = Base64.encode(sigBytes);
					Vector info = new Vector();
					info.add(key.getLocalId());
					info.add(sigString);
					infos.add(info);
				}
				catch (Exception e)
				{
					// TODO: Log this so the MSPA knows there's a problem
					// (but in a way that won't print during unit tests)
					//e.printStackTrace();
				}
			}
			
			Vector infos = new Vector();
		}

		Collector collector = new Collector();		
		getDatabase().visitAllRecordsForAccount(collector, authorAccountId);
		return collector.infos;
	}
	
	public String getBulletinUploadRecord(String authorAccountId, String bulletinLocalId)
	{
		UniversalId uid = UniversalId.createFromAccountAndLocalId(authorAccountId, bulletinLocalId);
		DatabaseKey headerKey = new DatabaseKey(uid);
		DatabaseKey burKey = MartusServerUtilities.getBurKey(headerKey);
		try
		{
			String bur = getDatabase().readRecord(burKey, getSecurity());
			return bur;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public Vector getBulletinChunkWithoutVerifyingCaller(String authorAccountId, String bulletinLocalId, int chunkOffset, int maxChunkSize)
	{
		return coreServer.getBulletinChunkWithoutVerifyingCaller(authorAccountId, bulletinLocalId, chunkOffset, maxChunkSize);
	}
	//End ServerSupplierInterface

	MartusCrypto getSecurity()
	{
		return coreServer.getSecurity();
	}

	Database getDatabase()
	{
		return coreServer.getDatabase();
	}
	
	boolean isSecureMode()
	{
		return coreServer.isSecureMode();
	}

	void loadServersWhoAreAuthorizedToCallUs() throws IOException, InvalidPublicKeyFileException, PublicInformationInvalidException
	{
		authorizedCallers.clear();

		File authorizedCallersDir = getAuthorizedCallersDirectory();
		File[] callersFiles = authorizedCallersDir.listFiles();
		if(callersFiles == null)
			return;
		for (int i = 0; i < callersFiles.length; i++)
		{
			File callerFile = callersFiles[i];
			Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(callerFile, getSecurity());
			addAuthorizedCaller((String)publicInfo.get(0));
			if(isSecureMode())
				callerFile.delete();
		}
	}

	File getAuthorizedCallersDirectory()
	{
		return new File(coreServer.getStartupConfigDirectory(), "mirrorsWhoCallUs");
	}
	
	void addAuthorizedCaller(String publicKey)
	{
		authorizedCallers.add(publicKey);
	}
	
	File getMirrorsWeWillCallDirectory()
	{
		return new File(coreServer.getStartupConfigDirectory(), "mirrorsWhoWeCall");		
	}
	
	void createGatewaysForServersWhoWeCall() throws 
			IOException, InvalidPublicKeyFileException, PublicInformationInvalidException, SSLSocketSetupException
	{
		retrieversWeWillCall.clear();

		File toCallDir = getMirrorsWeWillCallDirectory();
		File[] toCallFiles = toCallDir.listFiles();
		if(toCallFiles == null)
			return;
		for (int i = 0; i < toCallFiles.length; i++)
		{
			File toCallFile = toCallFiles[i];
			retrieversWeWillCall.add(createRetrieverToCall(toCallFile));
			if(isSecureMode())
				toCallFile.delete();
		}
	}
	
	MirroringRetriever createRetrieverToCall(File publicKeyFile) throws
			IOException, 
			InvalidPublicKeyFileException, 
			PublicInformationInvalidException, 
			SSLSocketSetupException
	{
		CallerSideMirroringGateway gateway = createGatewayToCall(publicKeyFile);
		MirroringRetriever retriever = new MirroringRetriever(getDatabase(), gateway, logger, getSecurity());
		return retriever;
	}
	
	CallerSideMirroringGateway createGatewayToCall(File publicKeyFile) throws 
			IOException, 
			InvalidPublicKeyFileException, 
			PublicInformationInvalidException, 
			SSLSocketSetupException
	{
		String ip = extractIpFromFileName(publicKeyFile.getName());
		int port = MirroringInterface.MARTUS_PORT_FOR_MIRRORING;
		Vector publicInfo = MartusUtilities.importServerPublicKeyFromFile(publicKeyFile, getSecurity());
		String publicKey = (String)publicInfo.get(0);

		CallerSideMirroringGatewayForXmlRpc xmlRpcGateway = new CallerSideMirroringGatewayForXmlRpc(ip, port); 
		xmlRpcGateway.setExpectedPublicKey(publicKey);
		return new CallerSideMirroringGateway(xmlRpcGateway);
	}

	static String extractIpFromFileName(String fileName) throws 
		InvalidPublicKeyFileException 
	{
		final String ipStartString = "ip=";
		int ipStart = fileName.indexOf(ipStartString);
		if(ipStart < 0)
			throw new InvalidPublicKeyFileException();
		ipStart += ipStartString.length();
		int ipEnd = ipStart;
		for(int i=0; i < 3; ++i)
		{
			ipEnd = fileName.indexOf(".", ipEnd+1);
			if(ipEnd < 0)
				throw new InvalidPublicKeyFileException();
		}
		++ipEnd;
		while(ipEnd < fileName.length() && Character.isDigit(fileName.charAt(ipEnd)))
			++ipEnd;
		String ip = fileName.substring(ipStart, ipEnd);
		return ip;
	}
	
	private class MirroringTask extends TimerTask
	{
		MirroringTask(Vector retrieversToUse)
		{
			retrievers = retrieversToUse;
		}
		
		public void run()
		{
			protectedRun();
		}
		
		synchronized void protectedRun()
		{
			if(retrievers.size() == 0)
				return;
			if(nextRetriever >= retrievers.size())
				nextRetriever = 0;
				
			MirroringRetriever thisRetriever = (MirroringRetriever)retrievers.get(nextRetriever);
			thisRetriever.tick();
		}

		int nextRetriever;
		Vector retrievers;
	}
	
	MartusServer coreServer;
	LoggerInterface logger;
	Vector authorizedCallers;
	MirroringRetriever retriever;
	Vector retrieversWeWillCall;
	
	private static final long mirroringIntervalMillis = 1 * 1000;	// TODO: Probably 60 seconds
}
