package org.martus.meta;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.util.zip.ZipFile;

import org.martus.client.core.BulletinFolder;
import org.martus.client.core.BulletinStore;
import org.martus.common.Bulletin;
import org.martus.common.Database;
import org.martus.common.DatabaseKey;
import org.martus.common.InputStreamWithSeek;
import org.martus.common.MartusCrypto;
import org.martus.common.MartusUtilities;
import org.martus.common.MockClientDatabase;
import org.martus.common.MockMartusSecurity;
import org.martus.common.Packet;
import org.martus.common.StringInputStream;
import org.martus.common.TestCaseEnhanced;
import org.martus.common.UniversalId;

public class TestThreads extends TestCaseEnhanced
{

	public TestThreads(String name)
	{
		super(name);
	}

	public void testThreadedBulletinActivity() throws Throwable
	{
		final int threadCount = 5;
		final int iterations = 5;
		ThreadFactory factory = new BulletinThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}

	
	public void testThreadedPacketWriting() throws Throwable
	{
		final int threadCount = 10;
		final int iterations = 10;
		ThreadFactory factory = new PacketWriteThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}
	
	public void testThreadedExporting() throws Throwable
	{
		final int threadCount = 10;
		final int iterations = 10;
		ThreadFactory factory = new ExportThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}

	public void testThreadedImporting() throws Throwable
	{
		final int threadCount = 10;
		final int iterations = 10;
		ThreadFactory factory = new ImportThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}
	
	public void testThreadedFolderListActivity() throws Throwable
	{
		final int threadCount = 10;
		final int iterations = 10;
		ThreadFactory factory = new FolderListThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}

	public void testThreadedFolderContentsActivity() throws Throwable
	{
		final int threadCount = 5;
		final int iterations = 5;
		ThreadFactory factory = new FolderContentsThreadFactory();
		launchTestThreads(factory, threadCount, iterations);
	}

	private void launchTestThreads(ThreadFactory factory, int threadCount, int iterations) throws Throwable
	{
		TestingThread[] threads = new TestingThread[threadCount];
		for (int i = 0; i < threads.length; i++) 
		{
			threads[i] = factory.createThread(iterations);
		}
		
		for (int i = 0; i < threads.length; i++) 
		{
			threads[i].start();
		}
		
		for (int i = 0; i < threads.length; i++) 
		{
			threads[i].join();
			if(threads[i].getResult() != null)
				throw threads[i].getResult();
		}
	}
	
	abstract class ThreadFactory
	{
		abstract TestingThread createThread(int copies) throws Exception;
	}
	
	class BulletinThreadFactory extends ThreadFactory
	{
		BulletinThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());
			//store.maxCachedBulletinCount = 10;
			
			MockMartusSecurity security = MockMartusSecurity.createClient();
			store.setSignatureGenerator(security);

			for (int i = 0; i < 10; i++)
			{
				Bulletin b = store.createEmptyBulletin();
				store.saveBulletin(b);
			}
		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new BulletinTester(store, copies);
		}

		BulletinStore store;
	}
	
	class ExportThreadFactory extends ThreadFactory
	{
		ExportThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());
			MockMartusSecurity security = MockMartusSecurity.createClient();
			
			store.setSignatureGenerator(security);
			b = store.createEmptyBulletin();
			store.saveBulletin(b);
		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new Exporter(store, b, copies);
		}
		
		BulletinStore store;
		Bulletin b;
	}
	
	class ImportThreadFactory extends ThreadFactory
	{
		ImportThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());
			MockMartusSecurity security = MockMartusSecurity.createClient();
			store.setSignatureGenerator(security);
		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new Importer(store, copies);
		}

		BulletinStore store;		
	}
	
	class PacketWriteThreadFactory extends ThreadFactory
	{
		PacketWriteThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());

			MockMartusSecurity security = MockMartusSecurity.createClient();
			store.setSignatureGenerator(security);

		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new PacketWriter(store, copies);
		}

		BulletinStore store;		
	}
	
	class FolderListThreadFactory extends ThreadFactory
	{
		FolderListThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());

			MockMartusSecurity security = MockMartusSecurity.createClient();
			store.setSignatureGenerator(security);

		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new FolderListTester(store, copies, nextId++);
		}

		BulletinStore store;
		int nextId;	
	}
	
	class FolderContentsThreadFactory extends ThreadFactory
	{
		FolderContentsThreadFactory() throws Exception
		{
			store = new BulletinStore(new MockClientDatabase());

			MockMartusSecurity security = MockMartusSecurity.createClient();
			store.setSignatureGenerator(security);

		}
		
		TestingThread createThread(int copies) throws Exception
		{
			return new FolderContentsTester(store, copies);
		}

		BulletinStore store;
	}
	
	abstract class TestingThread extends Thread
	{
		Throwable getResult()
		{
			return result;
		}

		Throwable result;
	}

	class BulletinTester extends TestingThread
	{
		BulletinTester(BulletinStore storeToUse, int copiesToDo) throws Exception
		{
			store = storeToUse;
			copies = copiesToDo;
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
				{
					Bulletin b = store.createEmptyBulletin();
					UniversalId uid = b.getUniversalId();

					store.saveBulletin(b);
					Bulletin b2 = store.findBulletinByUniversalId(uid);
					assertNotNull("not found after save?", b2);
					store.removeBulletinFromStore(uid);
					Bulletin b3 = store.findBulletinByUniversalId(uid);
					assertNull("found after remove?", b3);
					
				}
			}
			catch (Throwable e)
			{
System.out.println(folderName + ": " + e);
System.out.flush();
				result = e;
			}
		}
		
		BulletinStore store;
		int copies;
		String folderName;
		Bulletin[] bulletins;
	}

	class Exporter extends TestingThread
	{
		Exporter(BulletinStore store, Bulletin bulletinToExport, int copiesToExport) throws Exception
		{
			bulletin = bulletinToExport;
			file = createTempFile();
			copies = copiesToExport;
			db = store.getDatabase();
			security = store.getSignatureVerifier();
			headerKey = DatabaseKey.createKey(bulletin.getUniversalId(), bulletin.getStatus());
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
					MartusUtilities.exportBulletinPacketsFromDatabaseToZipFile(db, headerKey, file, security);
				} 
			catch (Exception e) 
			{
				result = e;
			}
		}
		
		Bulletin bulletin;
		File file;
		int copies;
		Database db;
		MartusCrypto security;
		DatabaseKey headerKey;
	}

	class Importer extends TestingThread
	{
		Importer(BulletinStore storeToUse, int copiesToDo) throws Exception
		{
			copies = copiesToDo;
			store = storeToUse;

			file = createTempFile();
			db = store.getDatabase();
			security = store.getSignatureVerifier();

			Bulletin b = store.createEmptyBulletin();
			store.saveBulletin(b);
			Database db = store.getDatabase();
			headerKey = DatabaseKey.createKey(b.getUniversalId(), b.getStatus());
			MartusUtilities.exportBulletinPacketsFromDatabaseToZipFile(db, headerKey, file, security);
			store.destroyBulletin(b);
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
				{
					ZipFile zip = new ZipFile(file);
					MartusUtilities.importBulletinPacketsFromZipFileToDatabase(db, null, zip, security);
					zip.close();

					Bulletin b = store.findBulletinByUniversalId(headerKey.getUniversalId());
					assertNotNull("import didn't work?", b);
					store.destroyBulletin(b);
				}
			} 
			catch (Exception e) 
			{
				result = e;
			}
		}
		
		BulletinStore store;
		File file;
		int copies;
		Database db;
		MartusCrypto security;
		DatabaseKey headerKey;
	}

	class PacketWriter extends TestingThread
	{
		PacketWriter(BulletinStore storeToUse, int copiesToDo) throws Exception
		{
			
			copies = copiesToDo;
			db = storeToUse.getDatabase();
			bulletin = storeToUse.createEmptyBulletin();
			security = storeToUse.getSignatureGenerator();
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
				{
					Writer writer = new StringWriter();
					bulletin.getBulletinHeaderPacket().writeXml(writer, security);
					InputStreamWithSeek in = new StringInputStream(writer.toString());
					Packet.validateXml(in, bulletin.getAccount(), bulletin.getLocalId(), null, security);
				}
			} 
			catch (Exception e) 
			{
				result = e;
			}
		}
		
		Bulletin bulletin;
		Database db;
		MartusCrypto security;
		int copies;
	}

	class FolderListTester extends TestingThread
	{
		FolderListTester(BulletinStore storeToUse, int copiesToDo, int id) throws Exception
		{
			store = storeToUse;
			copies = copiesToDo;
			folderName = Integer.toString(id);
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
				{
//System.out.println("delete " + folderName);
//System.out.flush();
					store.deleteFolder(folderName);
					assertNull("found after delete1?", store.findFolder(folderName));
//System.out.println("create " + folderName);
//System.out.flush();
					store.createFolder(folderName);
					assertNotNull("not found after create?", store.findFolder(folderName));
//System.out.println("save " + folderName);
//System.out.flush();
					store.saveFolders();
					assertNotNull("not found after save?", store.findFolder(folderName));
//System.out.println("delete " + folderName);
//System.out.flush();
					store.deleteFolder(folderName);
					assertNull("found after delete2?", store.findFolder(folderName));
				}
			}
			catch (Throwable e)
			{
System.out.println(folderName + ": " + e);
System.out.flush();
				result = e;
			}
		}
		
		BulletinStore store;
		int copies;
		String folderName;
	}

	class FolderContentsTester extends TestingThread
	{
		FolderContentsTester(BulletinStore storeToUse, int copiesToDo) throws Exception
		{
			store = storeToUse;
			copies = copiesToDo;
			folderName = "test";
			store.createFolder(folderName);
			bulletins= new Bulletin[copies];
			for (int i = 0; i < bulletins.length; i++)
			{
				bulletins[i] = store.createEmptyBulletin();
				store.saveBulletin(bulletins[i]);
			}
		}
		
		public void run()
		{
			try 
			{
				for(int i=0; i < copies; ++i)
				{
					Bulletin b = bulletins[i];
					UniversalId uid = b.getUniversalId();
					BulletinFolder f = store.findFolder(folderName);
					assertEquals("Already in?", false, f.contains(b));
					store.addBulletinToFolder(uid, f);
					assertEquals("Not added?", true, f.contains(b));
					store.discardBulletin(f, b);
					assertEquals("Not discarded?", false, f.contains(b));
					store.moveBulletin(b, store.getFolderDiscarded(), f);
					assertEquals("Not moved back?", true, f.contains(b));
					store.removeBulletinFromFolder(b, f);
					assertEquals("Not removed?", false, f.contains(b));
					assertEquals("Not orphan?", true, store.isOrphan(b));
					store.addBulletinToFolder(uid, f);
				}
			}
			catch (Throwable e)
			{
System.out.println(folderName + ": " + e);
System.out.flush();
				result = e;
			}
		}
		
		BulletinStore store;
		int copies;
		String folderName;
		Bulletin[] bulletins;
	}

}
