package org.martus.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.martus.common.Database.PacketVisitor;


abstract public class MockDatabase implements Database
{
	public MockDatabase()
	{
		deleteAllData();
	}
	
	public int getOpenStreamCount()
	{
		return streamsThatAreOpen.size();
	}

	// Database interface
	synchronized public void deleteAllData()
	{
		sealedQuarantine = new TreeMap();
		draftQuarantine = new TreeMap();
		incomingInterimMap = new TreeMap();
		outgoingInterimMap = new TreeMap();
	}

	public void writeRecord(DatabaseKey key, String record) throws IOException
	{
		if(key == null || record == null)
			throw new IOException("Null parameter");
			
		addKeyToMap(key, record);
	}
	
	public void importFiles(HashMap fileMapping) throws IOException
	{
		Iterator keys = fileMapping.keySet().iterator();
		while(keys.hasNext())
		{
			DatabaseKey key = (DatabaseKey) keys.next();
			File file = (File) fileMapping.get(key);
			
			InputStream in = new FileInputStream(file.getAbsolutePath());
			writeRecord(key,in);
			in.close();
			file.delete();
		}
	}

	public void writeRecordEncrypted(DatabaseKey key, String record, MartusCrypto encrypter) throws 
			IOException
	{
		writeRecord(key, record);
	}

	//TODO try BufferedInputStream
	public void writeRecord(DatabaseKey key, InputStream record) throws IOException
	{
		if(key == null || record == null)
			throw new IOException("Null parameter");

		String data = null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int theByte = 0;
		while( (theByte = record.read()) >= 0)
			out.write(theByte);

		byte[] bytes = out.toByteArray();
		data = new String(bytes, "UTF-8");

		writeRecord(key, data);
	}

	public InputStreamWithSeek openInputStream(DatabaseKey key, MartusCrypto decrypter)
	{
		String data = readRecord(key, decrypter);
		if(data == null)
			return null;
			
		try 
		{
			byte[] bytes = data.getBytes("UTF-8");
			return new MockRecordInputStream(key, bytes, streamsThatAreOpen);
		} 
		catch(Exception e) 
		{
			System.out.println("MockDatabase.openInputStream: " + e);
			return null;
		}
	}
	
	public String readRecord(DatabaseKey key, MartusCrypto decrypter)
	{
		return readRecord(key);
	}

	public void discardRecord(DatabaseKey key)
	{
		internalDiscardRecord(key);
	}

	public boolean doesRecordExist(DatabaseKey key)
	{
		return (readRecord(key) != null);
	}

	public void visitAllRecords(PacketVisitor visitor)
	{
		Set keys = getAllKeys();
		Iterator iterator = keys.iterator();
		while(iterator.hasNext())
		{
			DatabaseKey key = (DatabaseKey)iterator.next();
			try
			{
				visitor.visit(key);
			}
			catch (RuntimeException nothingWeCanDoAboutIt)
			{
				// nothing we can do, so ignore it
			}
		}
	}

	public String getFolderForAccount(String accountString)
	{
		UniversalId uid = UniversalId.createFromAccountAndLocalId(accountString, "");
		DatabaseKey key = DatabaseKey.createSealedKey(uid);
		File file = getInterimFile(key, incomingInterimMap);
		file.delete();
		return file.getPath();		
	}

	public File getIncomingInterimFile(DatabaseKey key)
	{
		File dir = getInterimFile(key, incomingInterimMap);
		dir.mkdirs();
		return new File(dir, "$$$in");
	}

	public File getOutgoingInterimFile(DatabaseKey key)
	{
		File dir = getInterimFile(key, outgoingInterimMap);
		dir.mkdirs();
		return new File(dir, "$$$out");
	}
	
	public File getContactInfoFile(String accountId)
	{
		File dir = new File(getFolderForAccount(accountId));
		dir.mkdirs();
		return new File(dir, "$$$ContactFile.dat");
	}

	public synchronized boolean isInQuarantine(DatabaseKey key)
	{
		Map quarantine = getQuarantineFor(key);
		return quarantine.containsKey(key);
	}
	
	public synchronized void moveRecordToQuarantine(DatabaseKey key)
	{
		if(!doesRecordExist(key))
			return;
			
		String data = readRecord(key);
		Map quarantine = getQuarantineFor(key);
		quarantine.put(key, data);
		discardRecord(key);
	}
	
	Map getQuarantineFor(DatabaseKey key)
	{
		Map map = sealedQuarantine;
		if(key.isDraft())
			map = draftQuarantine;
		return map;
	}

	public Set getAllKeys()
	{
		return internalGetAllKeys();
	}
	
	public int getRecordCount()
	{
		return getAllKeys().size();
	}
	// end Database interface
	
	private synchronized File getInterimFile(DatabaseKey key, Map map) 
	{
		if(map.containsKey(key))
			return (File)map.get(key);
			
		try 
		{
			File interimFile = File.createTempFile("$$$MockDbInterim", null);
			interimFile.deleteOnExit();
			interimFile.delete();
			map.put(key, interimFile);
			return interimFile;	
		} 
		catch (IOException e) 
		{
			return null;
		}
	}

	abstract void addKeyToMap(DatabaseKey key, String record);
	abstract String readRecord(DatabaseKey key);
	abstract Map getPacketMapFor(DatabaseKey key);
	abstract Set internalGetAllKeys();
	abstract void internalDiscardRecord(DatabaseKey key);
	
	Map sealedQuarantine;
	Map draftQuarantine;
	Map incomingInterimMap;
	Map outgoingInterimMap;
	
	HashMap streamsThatAreOpen = new HashMap();
}

class MockRecordInputStream extends ByteArrayInputStreamWithSeek
{
	MockRecordInputStream(DatabaseKey key, byte[] inputBytes, Map observer)
	{
		super(inputBytes);
		streamsThatAreOpen = observer;
	}
	
	public synchronized void addAsOpen(DatabaseKey key)
	{
		streamsThatAreOpen.put(this, key);
	}
	
	public synchronized void close()
	{
		streamsThatAreOpen.remove(this);
	}
	
	Map streamsThatAreOpen;
}
	
