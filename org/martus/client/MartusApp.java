package org.martus.client;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.martus.client.ClientSideNetworkHandlerUsingXmlRpc.SSLSocketSetupException;
import org.martus.common.Base64;
import org.martus.common.FieldDataPacket;
import org.martus.common.FileDatabase;
import org.martus.common.MartusCrypto;
import org.martus.common.MartusSecurity;
import org.martus.common.MartusUtilities;
import org.martus.common.NetworkInterface;
import org.martus.common.NetworkInterfaceConstants;
import org.martus.common.NetworkInterfaceForNonSSL;
import org.martus.common.NetworkInterfaceXmlRpcConstants;
import org.martus.common.NetworkResponse;
import org.martus.common.UnicodeReader;
import org.martus.common.UnicodeWriter;
import org.martus.common.UniversalId;
import org.martus.common.MartusCrypto.MartusSignatureException;
import sun.security.krb5.internal.crypto.e;


public class MartusApp
{
	public class SaveConfigInfoException extends Exception {}
	public class LoadConfigInfoException extends Exception {}

	public static class MartusAppInitializationException extends Exception 
	{
		MartusAppInitializationException(String message)
		{
			super(message);
		}
	}

	public MartusApp() throws MartusAppInitializationException
	{
		this(null, determineDataDirectory());
	}
	
	protected MartusApp(MartusCrypto cryptoToUse, File dataDirectoryToUse) throws MartusAppInitializationException
	{
		try
		{
			if(cryptoToUse == null)
				cryptoToUse = new MartusSecurity();
				
			dataDirectory = dataDirectoryToUse.getPath() + "/";
			security = cryptoToUse;
			localization = new MartusLocalization();
			store = new BulletinStore(dataDirectoryToUse);
			store.setSignatureGenerator(cryptoToUse);
			store.setEncryptPublicData(true);
			configInfo = new ConfigInfo();

			currentUserName = "";
			maxNewFolders = MAXFOLDERS;
		}
		catch(FileDatabase.MissingAccountMapException e)
		{
			throw new MartusAppInitializationException("ErrorMissingAccountMap");
		}
		catch(MartusCrypto.CryptoInitializationException e)
		{
			throw new MartusAppInitializationException("ErrorCryptoInitialization");
		}

		File languageFlag = new File(getDataDirectory(),"lang.es");
		if(languageFlag.exists())
		{
			languageFlag.delete();
			setCurrentLanguage("es");
			setCurrentDateFormatCode(MartusLocalization.DMY_SLASH.getCode());
		}
		else
		{
			CurrentUiState previouslySavedState = new CurrentUiState();
			previouslySavedState.load(getUiStateFile());
			String previouslySavedStateLanguage = previouslySavedState.getCurrentLanguage();
			if(previouslySavedStateLanguage == "")
				setCurrentLanguage(MartusLocalization.getDefaultUiLanguage());
			else
				setCurrentLanguage(previouslySavedStateLanguage);

			String previouslySavedStateDateFormat = previouslySavedState.getCurrentDateFormat();
			if(previouslySavedStateDateFormat == "")
				setCurrentDateFormatCode(MartusLocalization.getDefaultDateFormatCode());
			else
				setCurrentDateFormatCode(previouslySavedStateDateFormat);
		}
	}

	public void enableUploadLogging()
	{
		logUploads = true;
	}
	
	public void setServerInfo(String serverName, String serverKey)
	{
		configInfo.setServerName(serverName);
		configInfo.setServerPublicKey(serverKey);
		try
		{
			saveConfigInfo();
		}
		catch (SaveConfigInfoException e)
		{
			System.out.println("MartusApp.setServerInfo: Unable to Save ConfigInfo" + e);
		}
		
		createSSLServerHandler();
	}
	
	public void setHQKey(String hqKey) throws 
		SaveConfigInfoException
	{
		configInfo.setHQKey(hqKey);
		saveConfigInfo();
	}
	
	public String getHQKey()
	{
		return configInfo.getHQKey();	
	}
	
	public void clearHQKey() throws 
		SaveConfigInfoException
	{
		configInfo.clearHQKey();
		saveConfigInfo();
	}

	public ConfigInfo getConfigInfo()
	{
		return configInfo;
	}

	public void saveConfigInfo() throws SaveConfigInfoException
	{
		String fileName = getConfigInfoFilename();

		try
		{
			ByteArrayOutputStream encryptedConfigOutputStream = new ByteArrayOutputStream();
			configInfo.save(encryptedConfigOutputStream);
			byte[] encryptedConfigInfo = encryptedConfigOutputStream.toByteArray();

			ByteArrayInputStream encryptedConfigInputStream = new ByteArrayInputStream(encryptedConfigInfo);
			FileOutputStream configFileOutputStream = new FileOutputStream(fileName);
			security.encrypt(encryptedConfigInputStream, configFileOutputStream);

			configFileOutputStream.close();
			encryptedConfigInputStream.close();
			encryptedConfigOutputStream.close();


			FileInputStream in = new FileInputStream(fileName);
			byte[] signature = security.createSignature(in);
			in.close();

			FileOutputStream out = new FileOutputStream(getConfigInfoSignatureFilename());
			out.write(signature);
			out.close();
		}
		catch (Exception e)
		{
			System.out.println("saveConfigInfo :" + e);
			throw new SaveConfigInfoException();
		}

	}

	public void loadConfigInfo() throws LoadConfigInfoException
	{
		configInfo.clear();

		String fileName = getConfigInfoFilename();
		File sigFile = new File(getConfigInfoSignatureFilename());
		File dataFile = new File(fileName);

		if(!dataFile.exists())
		{
			//System.out.println("MartusApp.loadConfigInfo: config file doesn't exist");
			return;
		}

		try
		{
			byte[] signature =	new byte[(int)sigFile.length()];
			FileInputStream inSignature = new FileInputStream(sigFile);
			inSignature.read(signature);
			inSignature.close();

			FileInputStream inData = new FileInputStream(dataFile);
			boolean verified = security.isSignatureValid(security.getPublicKeyString(), inData, signature);
			inData.close();
			if(!verified)
				throw new LoadConfigInfoException();

			InputStream encryptedConfigFileInputStream = new BufferedInputStream(new FileInputStream(fileName));
			ByteArrayOutputStream plainTextConfigOutputStream = new ByteArrayOutputStream();
			security.decrypt(encryptedConfigFileInputStream, plainTextConfigOutputStream);

			byte[] plainTextConfigInfo = plainTextConfigOutputStream.toByteArray();
			ByteArrayInputStream plainTextConfigInputStream = new ByteArrayInputStream(plainTextConfigInfo);
			configInfo = ConfigInfo.load(plainTextConfigInputStream);

			plainTextConfigInputStream.close();
			plainTextConfigOutputStream.close();
			encryptedConfigFileInputStream.close();
		}
		catch (Exception e)
		{
			//System.out.println("Loadconfiginfo: " + e);
			throw new LoadConfigInfoException();
		}
	}
	
	public String getDataDirectory()
	{
		return dataDirectory;
	}

	public String getConfigInfoFilename()
	{
		return getDataDirectory() + "MartusConfig.dat";
	}

	public String getConfigInfoSignatureFilename()
	{
		return getDataDirectory() + "MartusConfig.sig";
	}

	public File getUploadInfoFile()
	{
		return new File(getDataDirectory() + "MartusUploadInfo.dat");
	}

	public File getUiStateFile()
	{
		return new File(getDataDirectory() + "UiState.dat");
	}

	public String getUploadLogFilename()
	{
		return  getDataDirectory() + "MartusUploadLog.txt";
	}

	public String getHelpFilename()
	{
		String helpFile = "MartusHelp-" + getCurrentLanguage() + ".txt";
		return helpFile;
	}
	
	public String getEnglishHelpFilename()
	{
		return("MartusHelp-en.txt");
	}

	public static String getTranslationsDirectory()
	{
		return determineDataDirectory().getPath();
	}

	public File getKeyPairFile()
	{
		return new File(getDataDirectory() + "MartusKeyPair.dat");
	}
	
	public static File getBackupFile(File original)
	{
		return new File(original.getPath() + ".bak");
	}

	public String getUserName()
	{
		return currentUserName;
	}

	public void loadFolders()
	{
		store.loadFolders();
	}

	public BulletinStore getStore()
	{
		return store;
	}

	public Bulletin createBulletin()
	{
		Bulletin b = store.createEmptyBulletin();
		b.set(Bulletin.TAGAUTHOR, configInfo.getSource());
		b.set(Bulletin.TAGPUBLICINFO, configInfo.getTemplateDetails());
		b.setDraft();
		b.setAllPrivate(true);
		return b;
	}

	public void setHQKeyInBulletin(Bulletin b)
	{
		//System.out.println("App.setHQKeyInBulletin Setting HQ:" + getHQKey());
		b.setHQPublicKey(getHQKey());
	}

	public BulletinFolder getFolderSent()
	{
		return store.getFolderSent();
	}

	public BulletinFolder getFolderDiscarded()
	{
		return store.getFolderDiscarded();
	}

	public BulletinFolder getFolderOutbox()
	{
		return store.getFolderOutbox();
	}
	
	public BulletinFolder getFolderDraftOutbox()
	{
		return store.getFolderDraftOutbox();
	}
	
	public BulletinFolder getFolderRetrieved()
	{
		return store.findFolder(RETRIEVED_FOLDER_NAME);
	}

	public BulletinFolder createFolderRetrieved()
	{
		return store.createOrFindFolder(RETRIEVED_FOLDER_NAME);
	}
	
	public void setMaxNewFolders(int numFolders)
	{
		maxNewFolders = numFolders;
	}
	
	public BulletinFolder createUniqueFolder() 
	{
		BulletinFolder newFolder = null;
		String uniqueFolderName = null;
		int folderIndex = 0;
		String originalFolderName = getFieldLabel("defaultFolderName");
		while (newFolder == null && folderIndex < maxNewFolders)
		{
			uniqueFolderName = originalFolderName;
			if (folderIndex > 0)
				uniqueFolderName += folderIndex;
			newFolder = store.createFolder(uniqueFolderName);
			++folderIndex;
		}
		if(newFolder != null)
			store.saveFolders();
		return newFolder;
	}
	
	public int repairOrphans()
	{
		Set orphans = store.getSetOfOrphanedBulletinUniversalIds();
		int foundOrphanCount = orphans.size();
		if(foundOrphanCount == 0)
			return 0;
		
		String name = store.getOrphanFolderName();
		BulletinFolder orphanFolder = store.createOrFindFolder(name);

		Iterator it = orphans.iterator();
		while(it.hasNext())
		{
			UniversalId uid = (UniversalId)it.next();
			store.addBulletinToFolder(uid, orphanFolder);
		}
		
		store.saveFolders();
		return foundOrphanCount;
	}
	

	public Vector findBulletinInAllVisibleFolders(Bulletin b)
	{
		Vector folders = new Vector();
		for(int i = 0; i < store.getFolderCount(); ++i)
		{
			BulletinFolder folder = store.getFolder(i);
			if(folder.isVisible() && folder.contains(b))
				folders.add(folder.getName());
		}
		return folders;	
	}

	public boolean shouldShowUploadReminder()
	{
		if(getFolderOutbox().getBulletinCount() == 0)
			return false;

		long now = System.currentTimeMillis();
		long thresholdMillis = 5 * 24 * 60 * 60 * 1000;
		Date uploaded = getLastUploadedTime();
		if(uploaded != null && now - uploaded.getTime() < thresholdMillis )
			return false;

		Date reminded = getLastUploadRemindedTime();
		if(reminded != null && now - reminded.getTime() < thresholdMillis )
			return false;

		return true;
	}

	public Date getUploadInfoElement(int index)
	{
		File file = getUploadInfoFile();
		if (!file.canRead())
			return null;
		Date date = null;
		try
		{
			ObjectInputStream stream = new ObjectInputStream(new FileInputStream(file));
			for(int i = 0 ; i < index ; ++i)
			{
				stream.readObject();
			}
			date = (Date)stream.readObject();
			stream.close();
		}
		catch (Exception e)
		{
			System.out.println("Error reading from getUploadInfoElement " + index + ":" + e);
		}
		return date;

	}

	public Date getLastUploadedTime()
	{
		return(getUploadInfoElement(0));
	}

	public Date getLastUploadRemindedTime()
	{
		return(getUploadInfoElement(1));
	}


	public void setUploadInfoElements(Date uploaded, Date reminded)
	{
		File file = getUploadInfoFile();
		file.delete();
		try
		{
			ObjectOutputStream stream = new ObjectOutputStream(new FileOutputStream(file));
			stream.writeObject(uploaded);
			stream.writeObject(reminded);
			stream.close();
		}
		catch (Exception e)
		{
			System.out.println("Error writing to setUploadInfoElements:" + e);
		}

	}

	public void setLastUploadedTime(Date uploaded)
	{
		Date reminded = getLastUploadRemindedTime();
		setUploadInfoElements(uploaded, reminded);
	}

	public void setLastUploadRemindedTime(Date reminded)
	{
		Date uploaded = getLastUploadedTime();
		setUploadInfoElements(uploaded, reminded);
	}

	public void resetLastUploadedTime()
	{
		setLastUploadedTime(new Date());
	}

	public void resetLastUploadRemindedTime()
	{
		setLastUploadRemindedTime(new Date());
	}

	public void search(String searchFor)
	{
		SearchParser parser = new SearchParser(this);
		SearchTreeNode searchNode = parser.parse(searchFor);

		String name = store.getSearchFolderName();
		BulletinFolder results = store.findFolder(name);
		if(results == null)
			results = store.createFolder(name);

		results.removeAll();

		Vector uids = store.getAllBulletinUids();
		for(int i = 0; i < uids.size(); ++i)
		{
			UniversalId uid = (UniversalId)uids.get(i);
			Bulletin b = store.findBulletinByUniversalId(uid);
			if(b.matches(searchNode))
				store.addBulletinToFolder(b.getUniversalId(), results);
		}
		store.saveFolders();
	}

	public boolean isNonSSLServerAvailable(String serverName)
	{
		if(serverName.length() == 0)
			return false;

		int port = NetworkInterfaceXmlRpcConstants.MARTUS_PORT_FOR_NON_SSL;
		NetworkInterfaceForNonSSL server = new ClientSideNetworkHandlerUsingXmlRpcForNonSSL(serverName, port);
		return isNonSSLServerAvailable(server);
	}
	
	public boolean isSSLServerAvailable()
	{
		if(currentSSLServer == null && getServerName().length() == 0)
			return false;
			
		return isSSLServerAvailable(getCurrentSSLServerProxy());
	}

	public boolean isSSLServerAvailable(String serverName)
	{
		if(serverName.length() == 0)
			return false;

		int port = NetworkInterfaceXmlRpcConstants.MARTUS_PORT_FOR_SSL;
		NetworkInterface server;
		try 
		{
			server = new ClientSideNetworkHandlerUsingXmlRpc(serverName, port);
		} 
		catch (SSLSocketSetupException e) 
		{
			//TODO propagate to UI and needs a test.
			e.printStackTrace();
			return false;
		}
		return isSSLServerAvailable(new ClientSideNetworkGateway(server));
	}
	
	public boolean isSignedIn()
	{
		return security.hasKeyPair();	
	}
	
	public String getServerPublicCode(String serverName) throws
		ServerNotAvailableException,
		PublicInformationInvalidException
	{
		try
		{
			return MartusUtilities.computePublicCode(getServerPublicKey(serverName));
		}
		catch(Base64.InvalidBase64Exception e)
		{
			throw new PublicInformationInvalidException();
		}
	}
	
	public String getServerPublicKey(String serverName) throws
		ServerNotAvailableException,
		PublicInformationInvalidException
	{
		int port = NetworkInterfaceXmlRpcConstants.MARTUS_PORT_FOR_NON_SSL;
		NetworkInterfaceForNonSSL server = new ClientSideNetworkHandlerUsingXmlRpcForNonSSL(serverName, port);
		return getServerPublicKey(server);
	}
	
	class ServerNotAvailableException extends Exception {}
	class PublicInformationInvalidException extends Exception {}
	
	public String getServerPublicKey(NetworkInterfaceForNonSSL server) throws
		ServerNotAvailableException,
		PublicInformationInvalidException
	{
		if(server.ping() == null)
			throw new ServerNotAvailableException();
			
		Vector serverInformation = server.getServerInformation();
		if(serverInformation == null)
			throw new ServerNotAvailableException();
			
		if(serverInformation.size() != 3)
			throw new PublicInformationInvalidException();
		
		String accountId = (String)serverInformation.get(1);
		String sig = (String)serverInformation.get(2);
		validatePublicInfo(accountId, sig);
		return accountId;
	}

	public void validatePublicInfo(String accountId, String sig) throws 
		PublicInformationInvalidException 
	{
		try
		{
			ByteArrayInputStream in = new ByteArrayInputStream(Base64.decode(accountId));
			if(!security.isSignatureValid(accountId, in, Base64.decode(sig)))
				throw new PublicInformationInvalidException();
		
		}
		catch(Exception e)
		{
			//System.out.println("MartusApp.getServerPublicCode: " + e);
			throw new PublicInformationInvalidException();
		}
	}

	public boolean requestServerUploadRights(String magicWord)
	{
		try
		{
			NetworkResponse response = getCurrentSSLServerProxy().getUploadRights(security, magicWord);
			if(response.getResultCode().equals(NetworkInterfaceConstants.OK))
				return true;
		}
		catch(MartusCrypto.MartusSignatureException e)
		{
			System.out.println("MartusApp.requestServerUploadRights: " + e);
		}
			
		return false;
	}
	
	public String uploadBulletin(Bulletin b)
	{
		String result = null;
		File tempFile = null;
		try
		{
			tempFile = File.createTempFile("$$$MartusUploadBulletin", null);
			b.saveToFile(tempFile);
			FileInputStream inputStream = new FileInputStream(tempFile);
			int totalSize = MartusUtilities.getCappedFileLength(tempFile);
			int offset = 0;
			byte[] rawBytes = new byte[serverChunkSize];

			while(true)
			{
				int chunkSize = inputStream.read(rawBytes);
				if(chunkSize <= 0)
					break;
				byte[] chunkBytes = new byte[chunkSize];
				System.arraycopy(rawBytes, 0, chunkBytes, 0, chunkSize);

				String authorId = getAccountId();
				String bulletinLocalId = b.getLocalId();
				String encoded = Base64.encode(chunkBytes);
				
				NetworkResponse response = getCurrentSSLServerProxy().putBulletinChunk(security, 
									authorId, bulletinLocalId, offset, chunkSize, totalSize, encoded);
				result = response.getResultCode();
				if(!result.equals(NetworkInterfaceConstants.CHUNK_OK) && !result.equals(NetworkInterfaceConstants.OK))
					break;
				offset += chunkSize;
			}
			inputStream.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.out.println("MartusApp.uploadBulletin: " + e);
		}
		
		if(tempFile != null)
			tempFile.delete();
			
		return result;
	}
	
	public String backgroundUpload()
	{
		if(getFolderOutbox().getBulletinCount() > 0)
			return backgroundUploadOneSealedBulletin();
			
		if(getFolderDraftOutbox().getBulletinCount() > 0)
			return backgroundUploadOneDraftBulletin();

		return null;
	}

	String backgroundUploadOneSealedBulletin() 
	{
		if(!isSSLServerAvailable())
			return null;
		
		BulletinFolder outbox = getFolderOutbox();
		Bulletin b = outbox.getBulletin(0);
		String result = uploadBulletin(b);
		
		if(result.equals(NetworkInterfaceConstants.OK) || result.equals(NetworkInterfaceConstants.DUPLICATE))
		{
			outbox.remove(b.getUniversalId());
			store.moveBulletin(b, outbox, getFolderSent());
			store.saveFolders();
			resetLastUploadedTime();
			if(logUploads)
			{
				try
				{
					File file = new File(getUploadLogFilename());
					UnicodeWriter log = new UnicodeWriter(file, UnicodeWriter.APPEND);
					log.writeln(b.getLocalId());
					log.writeln(configInfo.getServerName());
					log.writeln(b.get(b.TAGTITLE));
					log.close();
					log = null;
				}
				catch(Exception e)
				{
					System.out.println("MartusApp.backgroundUpload: " + e);
				}
			}
		}
		return result;
	}

	String backgroundUploadOneDraftBulletin() 
	{
		if(!isSSLServerAvailable())
			return null;
		
		BulletinFolder draftOutbox = getFolderDraftOutbox();
		Bulletin b = draftOutbox.getBulletin(0);
		String result = uploadBulletin(b);
		
		if(result.equals(NetworkInterfaceConstants.OK))
		{
			draftOutbox.remove(b.getUniversalId());
			store.saveFolders();
		}
		
		return result;
	}

	public String retrieveMyBulletins(Vector uidList)
	{
		BulletinFolder retrievedFolder = createFolderRetrieved();
		store.saveFolders();

		return retrieveBulletins(uidList, retrievedFolder);
	}

	public String retrieveFieldOfficeBulletins(Vector uidList)
	{
		BulletinFolder retrievedFolder = createFolderRetrieved();
		store.saveFolders();

		return retrieveBulletins(uidList, retrievedFolder);
	}

	private String retrieveBulletins(Vector uidList, BulletinFolder retrievedFolder) 
	{
		if(!isSSLServerAvailable())
			return NetworkInterfaceConstants.NO_SERVER;
		
		for(int i = 0; i < uidList.size(); ++i)
		{
			try
			{
				UniversalId uid = (UniversalId)uidList.get(i);
				if(store.findBulletinByUniversalId(uid) != null)
					continue;
		
				retrieveOneBulletin(uid, retrievedFolder);
			}
			catch(Exception e)
			{
				return NetworkInterfaceConstants.INCOMPLETE;
			}
		
		}
		
		return NetworkInterfaceConstants.OK;
	}
	
	public static class ServerErrorException extends Exception 
	{
		ServerErrorException(String message)
		{
			super(message);
		}
		
		ServerErrorException()
		{
			this("");
		}
	}
	
	private Vector getMyServerBulletinSummaries() throws ServerErrorException
	{
		String resultCode = "?";
		try 
		{
			NetworkResponse response = getCurrentSSLServerProxy().getSealedBulletinIds(security, getAccountId());
			resultCode = response.getResultCode();
			if(resultCode.equals(NetworkInterfaceConstants.OK))
				return response.getResultVector();
		} 
		catch (MartusSignatureException e) 
		{
			System.out.println("MartusApp.getMyServerBulletinSummaries: " + e);
			resultCode = NetworkInterfaceConstants.SIG_ERROR;
		}
		throw new ServerErrorException(resultCode);
	}
	
	private Vector getMyDraftServerBulletinSummaries() throws ServerErrorException
	{
		String resultCode = "?";
		try 
		{
			NetworkResponse response = getCurrentSSLServerProxy().getDraftBulletinIds(security, getAccountId());
			resultCode = response.getResultCode();
			if(resultCode.equals(NetworkInterfaceConstants.OK))
				return response.getResultVector();
		}
		catch (MartusSignatureException e) 
		{
			System.out.println("MartusApp.getMyDraftServerBulletinSummaries: " + e);
			resultCode = NetworkInterfaceConstants.SIG_ERROR;
		}
		throw new ServerErrorException(resultCode);
	}

	public Vector getMySummaries() throws ServerErrorException
	{
		Vector summaryStrings = getMyServerBulletinSummaries();
		return createSummariesFromStrings(getAccountId(), summaryStrings);
	}

	public Vector getMyDraftSummaries() throws ServerErrorException
	{
		Vector summaryStrings = getMyDraftServerBulletinSummaries();
		return createSummariesFromStrings(getAccountId(), summaryStrings);
	}

	public Vector createSummariesFromStrings(String accountId, Vector summaryStrings)
		throws ServerErrorException 
	{
		Vector allSummaries = new Vector();
		Iterator iterator = summaryStrings.iterator();
		while(iterator.hasNext())
		{
			String pair = (String)iterator.next();
			BulletinSummary bulletinSummary = createSummaryFromString(accountId, pair);
			allSummaries.add(bulletinSummary);
		}
		return allSummaries;
	}

	public BulletinSummary createSummaryFromString(String accountId, String pair)
		throws ServerErrorException 
	{
		int at = pair.indexOf("=");
		if(at < 0)
			throw new ServerErrorException("MartusApp.createSummaryFromString: " + pair);
			
		String bulletinLocalId = pair.substring(0, at);
		String summary = pair.substring(at + 1);
		String author = "";
		if(FieldDataPacket.isValidLocalId(summary))
		{
			String packetlocalId = summary;
			try 
			{
				FieldDataPacket fdp = retrieveFieldDataPacketFromServer(accountId, bulletinLocalId, packetlocalId);
				summary = fdp.get(Bulletin.TAGTITLE);
				author = fdp.get(Bulletin.TAGAUTHOR);
			} 
			catch(Exception e) 
			{
				System.out.println("MartusApp.getSummaries: " + e);
				e.printStackTrace();
				throw new ServerErrorException();
			}
		}
		else
		{
			if(summary.length() > 0)
				summary = summary.substring(1);
		}
		BulletinSummary bulletinSummary = new BulletinSummary(accountId, bulletinLocalId, summary, author);
		return bulletinSummary;
	}
	
	public Vector getFieldOfficeSealedSummaries(String fieldOfficeAccountId) throws ServerErrorException
	{
		try 
		{
			NetworkResponse response = getCurrentSSLServerProxy().getSealedBulletinIds(security, fieldOfficeAccountId);
			if(response.getResultCode().equals(NetworkInterfaceConstants.OK))
				return createSummariesFromStrings(fieldOfficeAccountId, response.getResultVector());
		} 
		catch (MartusSignatureException e)
		{
			System.out.println("MartusApp.getFieldOfficeSealedSummaries: " + e);
		}
		throw new ServerErrorException();
	}

	public Vector getFieldOfficeDraftSummaries(String fieldOfficeAccountId) throws ServerErrorException
	{
		try 
		{
			NetworkResponse response = getCurrentSSLServerProxy().getDraftBulletinIds(security, fieldOfficeAccountId);
			if(response.getResultCode().equals(NetworkInterfaceConstants.OK))
				return createSummariesFromStrings(fieldOfficeAccountId, response.getResultVector());
		} 
		catch (MartusSignatureException e)
		{
			System.out.println("MartusApp.getFieldOfficeDraftSummaries: " + e);
		}
		throw new ServerErrorException();
	}


	public Vector getFieldOfficeAccounts() throws ServerErrorException
	{
		try
		{
			NetworkResponse response = getCurrentSSLServerProxy().getFieldOfficeAccountIds(security, getAccountId());
			String resultCode = response.getResultCode();
			if(!resultCode.equals(NetworkInterfaceConstants.OK))
				throw new ServerErrorException(resultCode);
			return response.getResultVector();
		}
		catch(MartusCrypto.MartusSignatureException e)
		{
			System.out.println("MartusApp.getFieldOfficeAccounts: " + e);
			throw new ServerErrorException();
		}
	}

	public FieldDataPacket retrieveFieldDataPacketFromServer(String authorAccountId, String bulletinLocalId, String dataPacketLocalId) throws Exception
	{
		NetworkResponse response = getCurrentSSLServerProxy().getPacket(security, authorAccountId, bulletinLocalId, dataPacketLocalId);
		String resultCode = response.getResultCode();
		if(!resultCode.equals(NetworkInterfaceConstants.OK))
			throw new ServerErrorException(resultCode);

		String xml = (String)response.getResultVector().get(0);
		UniversalId uid = UniversalId.createFromAccountAndLocalId(authorAccountId, dataPacketLocalId);
		FieldDataPacket fdp = new FieldDataPacket(uid , Bulletin.getStandardFieldNames());
		byte[] xmlBytes = xml.getBytes("UTF-8");
		ByteArrayInputStream in =  new ByteArrayInputStream(xmlBytes);
		fdp.loadFromXml(in, security);
		return fdp;
	}

	void retrieveOneBulletin(UniversalId uid, BulletinFolder retrievedFolder) throws Exception
	{
		File tempFile = File.createTempFile("$$$MartusApp", null);
		tempFile.deleteOnExit();
		FileOutputStream outputStream = new FileOutputStream(tempFile);

		int masterTotalSize = 0;
		int totalSize = 0;
		int chunkOffset = 0;
		String lastResponse = "";
		while(!lastResponse.equals(NetworkInterfaceConstants.OK))
		{
			NetworkResponse response = getCurrentSSLServerProxy().getBulletinChunk(security, 
								uid.getAccountId(), uid.getLocalId(), chunkOffset, serverChunkSize);
								
			lastResponse = response.getResultCode();
			if(!lastResponse.equals(NetworkInterfaceConstants.OK) &&
				!lastResponse.equals(NetworkInterfaceConstants.CHUNK_OK))
			{
				//System.out.println((String)result.get(0));
				throw new ServerErrorException();
			}
			
			Vector result = response.getResultVector();
			totalSize = ((Integer)result.get(0)).intValue();
			if(masterTotalSize == 0)
				masterTotalSize = totalSize;
				
			if(totalSize != masterTotalSize)
				throw new ServerErrorException("totalSize not consistent");
			if(totalSize < 0)
				throw new ServerErrorException("totalSize negative");
				
			int chunkSize = ((Integer)result.get(1)).intValue();
			if(chunkSize < 0 || chunkSize > totalSize - chunkOffset)
				throw new ServerErrorException("chunkSize out of range");
			
			// TODO: validate that length of data == chunkSize that was returned
			String data = (String)result.get(2);
			StringReader reader = new StringReader(data);

			Base64.decode(reader, outputStream);
			chunkOffset += chunkSize;
		}

		outputStream.close();

		if(tempFile.length() != masterTotalSize)
			throw new ServerErrorException("totalSize didn't match data length");
			
		store.importZipFileBulletin(tempFile, retrievedFolder, true);

		tempFile.delete();
	}

	public static class AccountAlreadyExistsException extends Exception {}
	public static class CannotCreateAccountFileException extends IOException {}

	public void createAccount(String userName, String userPassPhrase) throws
					AccountAlreadyExistsException,
					CannotCreateAccountFileException,
					IOException
	{
		createAccountInternal(getKeyPairFile(), userName, userPassPhrase);
	}

	public void writeKeyPairFile(String userName, String userPassPhrase) throws 
		IOException, 
		CannotCreateAccountFileException
	{
		writeKeyPairFileWithBackup(getKeyPairFile(), userName, userPassPhrase);
	}

	public boolean doesAccountExist()
	{
		return getKeyPairFile().exists();
	}
	
	public void exportPublicInfo(File exportFile) throws 
		IOException,
		Base64.InvalidBase64Exception,
		MartusCrypto.MartusSignatureException
	{
			String publicKeyString = security.getPublicKeyString();
			byte[] publicKeyBytes = Base64.decode(publicKeyString);
			ByteArrayInputStream in = new ByteArrayInputStream(publicKeyBytes);
			byte[] sigBytes = security.createSignature(in);
			
			UnicodeWriter writer = new UnicodeWriter(exportFile);
			writer.writeln(publicKeyString);
			writer.writeln(Base64.encode(sigBytes));
			writer.close();
	}
	
	public String extractPublicInfo(File file) throws
		IOException,
		Base64.InvalidBase64Exception,
		PublicInformationInvalidException
	{
		UnicodeReader reader = new UnicodeReader(file);
		String publicKey = reader.readLine();
		String signature = reader.readLine();
		reader.close();
		validatePublicInfo(publicKey, signature);
		return publicKey;
	}		

	public File getPublicInfoFile(String fileName)
	{
		fileName = toFileName(fileName);
		String completeFileName = fileName + PUBLIC_INFO_EXTENSION;
		return(new File(getDataDirectory(), completeFileName));
	}

	public boolean attemptSignIn(String userName, String userPassPhrase)
	{
		return attemptSignInInternal(getKeyPairFile(), userName, userPassPhrase);
	}
	
	public String getFieldLabel(String fieldName)
	{
		return localization.getLabel(getCurrentLanguage(), "field", fieldName, "");
	}

	public String getLanguageName(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "language", code, "Unknown");
	}

	public String getWindowTitle(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "wintitle", code, "???");
	}

	public String getButtonLabel(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "button", code, "???");
	}

	public String getMenuLabel(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "menu", code, "???");
	}

	public String getMonthLabel(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "month", code, "???");
	}

	public String getMessageLabel(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "message", code, "???");
	}

	public String getStatusLabel(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "status", code, "???");
	}

	public String getKeyword(String code)
	{
		return localization.getLabel(getCurrentLanguage(), "keyword", code, "???");
	}

	public String[] getMonthLabels()
	{
		final String[] tags = {"jan","feb","mar","apr","may","jun",
							"jul","aug","sep","oct","nov","dec"};

		String[] labels = new String[tags.length];
		for(int i = 0; i < labels.length; ++i)
		{
			labels[i] = getMonthLabel(tags[i]);
		}

		return labels;
	}

	public ChoiceItem[] getUiLanguages()
	{
		return localization.getUiLanguages(getTranslationsDirectory());
	}
	
	public String getCurrentLanguage()
	{
		return currentLanguage;
	}

	public void setCurrentLanguage(String languageCode)
	{
		localization.loadTranslationFile(languageCode);
		currentLanguage = languageCode;
	}

	public String getCurrentDateFormatCode()
	{
		return currentDateFormat;
	}

	public void setCurrentDateFormatCode(String code)
	{
		currentDateFormat = code;
	}


	
	
	public String convertStoredToDisplay(String storedDate)
	{
		DateFormat dfStored = Bulletin.getStoredDateFormat();
		DateFormat dfDisplay = new SimpleDateFormat(getCurrentDateFormatCode());
		String result = "";
		try
		{
			Date d = dfStored.parse(storedDate);
			result = dfDisplay.format(d);
		}
		catch(ParseException e)
		{
			// unparsable dates simply become blank strings,
			// so we don't want to do anything for this exception
			//System.out.println(e);
		}

		return result;
	}

	public static Point center(Dimension inner, Rectangle outer)
	{
		int x = (outer.width - inner.width) / 2;
		int y = (outer.height - inner.height) / 2;
		return new Point(x, y);
	}

	public String getAccountId()
	{
		return store.getAccountId();
	}

	protected void createAccountInternal(File keyPairFile, String userName, String userPassPhrase) throws
					AccountAlreadyExistsException,
					CannotCreateAccountFileException,
					IOException
	{
		if(keyPairFile.exists())
			throw(new AccountAlreadyExistsException());
		security.clearKeyPair();
		security.createKeyPair();
		try 
		{
			writeKeyPairFileWithBackup(keyPairFile, userName, userPassPhrase);
			currentUserName = userName;
		} 
		catch(IOException e) 
		{
			security.clearKeyPair();
			throw(e);
		} 
	}

	protected void writeKeyPairFileWithBackup(File keyPairFile, String userName, String userPassPhrase) throws 
		IOException, 
		CannotCreateAccountFileException 
	{
		writeKeyPairFileInternal(keyPairFile, userName, userPassPhrase);
		try
		{
			writeKeyPairFileInternal(getBackupFile(keyPairFile), userName, userPassPhrase);			
		}
		catch (Exception e)
		{
			System.out.println("MartusApp.writeKeyPairFileWithBackup: " + e);
		}
		
	}

	protected void writeKeyPairFileInternal(File keyPairFile, String userName, String userPassPhrase) throws 
		IOException, 
		CannotCreateAccountFileException 
	{
		try
		{
			FileOutputStream outputStream = new FileOutputStream(keyPairFile);
			security.writeKeyPair(outputStream, getCombinedPassPhrase(userName, userPassPhrase));
			outputStream.close();
		}
		catch(FileNotFoundException e)
		{
			throw(new CannotCreateAccountFileException());
		}
		
	}

	protected boolean attemptSignInInternal(File keyPairFile, String userName, String userPassPhrase)
	{
		FileInputStream inputStream = null;
		security.clearKeyPair();
		currentUserName = "";

		try
		{
			inputStream = new FileInputStream(keyPairFile);
		}
		catch(IOException e)
		{
			return false;
		}

		boolean worked = true;
		try
		{
			security.readKeyPair(inputStream, getCombinedPassPhrase(userName, userPassPhrase));
		}
		catch(Exception e)
		{
			worked = false;
		}

		try
		{
			inputStream.close();
		}
		catch(IOException e)
		{
			worked = false;
		}

		if(worked)
			currentUserName = userName;
					
		return worked;
	}

	protected String getCombinedPassPhrase(String userName, String userPassPhrase)
	{
		return(userPassPhrase + ":" + userName);
	}

	public MartusCrypto getSecurity()
	{
		return security;
	}
	
	public void setSSLServerForTesting(NetworkInterface server)
	{
		currentSSLServer = server;
	}

	private boolean isNonSSLServerAvailable(NetworkInterfaceForNonSSL server)
	{
		String result = server.ping();
		if(result == null)
			return false;

		if(result.indexOf("MartusServer") != 0)
			return false;

		return true;
	}
	
	private boolean isSSLServerAvailable(ClientSideNetworkGateway server)
	{
		NetworkResponse response = server.getServerInfo();
		if(!response.getResultCode().equals(NetworkInterfaceConstants.OK))
			return false;

		try
		{
			String version = (String)response.getResultVector().get(0);
			if(version.indexOf("MartusServer") == 0)
				return true;
		}
		catch(Exception e)
		{
			//System.out.println("MartusApp.isSSLServerAvailable: " + e);
		}

		return false;
	}
	
	private ClientSideNetworkGateway getCurrentSSLServerProxy()
	{
		if(currentSSLServerProxy == null)
		{
			currentSSLServerProxy = new ClientSideNetworkGateway(getCurrentSSLServer());
		}
		
		return currentSSLServerProxy;
	}
	
	private NetworkInterface getCurrentSSLServer()
	{
		if(currentSSLServer == null)
		{
			createSSLServerHandler();
		}

		return currentSSLServer;
	}

	private void createSSLServerHandler() 
	{
		String ourServer = getServerName();
		int ourPort = NetworkInterfaceXmlRpcConstants.MARTUS_PORT_FOR_SSL;
		try 
		{
			ClientSideNetworkHandlerUsingXmlRpc handler = new ClientSideNetworkHandlerUsingXmlRpc(ourServer, ourPort);
			handler.getSimpleX509TrustManager().setExpectedPublicKey(getConfigInfo().getServerPublicKey());
			currentSSLServer = handler;
			
			
		} 
		catch (SSLSocketSetupException e) 
		{
			//TODO propagate to UI and needs a test.
			e.printStackTrace();
		}
	}
	
	private String getServerName()
	{
		return configInfo.getServerName();
	}

	private static File determineDataDirectory()
	{
		String dir;
		if(System.getProperty("os.name").indexOf("Windows") >= 0)
		{
			dir = "C:/Martus/";
		}
		else
		{
			String userHomeDir = System.getProperty("user.home");
			dir = userHomeDir + "/.Martus/";
		}
		File file = new File(dir);
		if(!file.exists())
		{
			file.mkdirs();
		}
		
		return file;
	}

	static public String toFileName(String text)
	{
		final int maxLength = 20;
		final int minLength = 3;

		if(text.length() > maxLength)
			text = text.substring(0, maxLength);

		char[] chars = text.toCharArray();
		for(int i = 0; i < chars.length; ++i)
		{
			if(!isCharOkInFileName(chars[i]))
				chars[i] = ' ';
		}

		text = new String(chars).trim();
		if(text.length() < minLength)
			text = "Martus-" + text;

		return text;
	}

	static private boolean isCharOkInFileName(char c)
	{
		if(Character.isLetterOrDigit(c))
			return true;
		return false;
	}

	private String createSignature(String stringToSign)
		throws UnsupportedEncodingException, MartusSignatureException 
	{
		MartusCrypto security = getSecurity();
		return MartusUtilities.createSignature(stringToSign, security);
	}

	String dataDirectory;
	private MartusLocalization localization;
	BulletinStore store;
	private ConfigInfo configInfo;
	NetworkInterface currentSSLServer;
	ClientSideNetworkGateway currentSSLServerProxy;
	private boolean logUploads;
	MartusCrypto security;
	private String currentUserName;
	private int maxNewFolders;

	private static final String RETRIEVED_FOLDER_NAME = "Retrieved Bulletins";
	public static final String PUBLIC_INFO_EXTENSION = ".mpi";
	public static final String AUTHENTICATE_SERVER_FAILED = "Failed to Authenticate Server";
	private final int MAXFOLDERS = 50;
	int serverChunkSize = NetworkInterfaceConstants.MAX_CHUNK_SIZE;
	private String currentLanguage;
	private String currentDateFormat;
}

